#!/usr/bin/env bash
#
# Starts a cms-backend test instance for one AA session, on that session's own port.
#
# WHY THIS EXISTS:
#  1. HAZELCAST. A second backend on this host SHARES the first one's caches unless its cluster is
#     namespaced, and shared caches silently invalidate any test that reads cached data
#     (translations, categories, banks, form-config, mre-rules): a deliberately corrupted DB row once
#     read back clean through the API because another node's cached bundle answered — the test
#     passed when it should have failed.
#     The property is `cms.hazelcast.cluster-name` (HazelcastCacheConfig.java:16). `-Dhz.cluster-name`
#     is SILENTLY IGNORED. `-Dhazelcast.network.join.multicast.enabled=false` is also insufficient on
#     its own, because multicast is already off in code (:31) while auto-detection was the real
#     culprit. This script passes the correct property so no session can get it wrong, and then
#     VERIFIES isolation in the startup log rather than trusting the flag.
#  2. The user's own backend (8082) and dev server (4200) must never be touched. This script
#     refuses those ports outright.
#  3. Readiness. `mvn spring-boot:run` returns long before the app serves traffic; a session that
#     starts testing immediately sees connection-refused and misreads it as a broken endpoint.
#     This polls until the API actually answers.
#
# Usage:
#   ./deployment/run-test-backend.sh 8092            # start (foreground log tail, backgrounds itself)
#   ./deployment/run-test-backend.sh 8092 --stop      # stop only the instance on that port
#   ./deployment/run-test-backend.sh 8092 --restart
#
# Ports by session: S2A=8092, S2B=8093, S2C=8094.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/cms-backend"
PORT="${1:-}"
ACTION="${2:-start}"

if [ -z "$PORT" ]; then
  echo "usage: run-test-backend.sh <port> [--stop|--restart]" >&2
  exit 2
fi

# Hard refusal, not a warning: these belong to the user.
if [ "$PORT" = "8082" ] || [ "$PORT" = "4200" ]; then
  echo "REFUSING: port ${PORT} belongs to the user's own backend/frontend. Use 8092, 8093 or 8094." >&2
  exit 2
fi

LOG_FILE="${TMPDIR:-/tmp}/cms-backend-${PORT}.log"

pid_on_port() {
  netstat -ano 2>/dev/null | grep -E "[:.]${PORT}[[:space:]]+.*LISTENING" | awk '{print $NF}' | head -1
}

# Only ever stops a process we can positively identify as a cms-backend started on THIS port.
# Killing by port alone risks taking down something that merely reused the number.
stop_instance() {
  local pid; pid="$(pid_on_port)"
  if [ -z "$pid" ]; then
    echo "==> nothing listening on ${PORT}"
    return 0
  fi
  local cmdline
  cmdline="$(powershell -NoProfile -Command "(Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}').CommandLine" 2>/dev/null || true)"
  if printf '%s' "$cmdline" | grep -q "server.port=${PORT}"; then
    taskkill //PID "$pid" //F >/dev/null 2>&1 && echo "==> stopped cms-backend on ${PORT} (pid ${pid})"
  else
    echo "REFUSING: pid ${pid} holds ${PORT} but is not a cms-backend started with server.port=${PORT}." >&2
    echo "          Inspect it yourself before stopping anything." >&2
    return 1
  fi
}

case "$ACTION" in
  --stop)    stop_instance; exit $? ;;
  --restart) stop_instance || exit 1; sleep 3 ;;
esac

if [ -n "$(pid_on_port)" ]; then
  echo "==> a backend is already listening on ${PORT}; reusing it."
  echo "    (use --restart to pick up code changes — a stale instance will serve OLD code and"
  echo "     make your fix look like it did not work)"
  exit 0
fi

CLUSTER_NAME="cms-claude-${PORT}"
echo "==> starting cms-backend on ${PORT} (cluster ${CLUSTER_NAME}, log: ${LOG_FILE})"
(
  cd "$BACKEND_DIR" || exit 1
  nohup mvn -o spring-boot:run \
    -Dspring-boot.run.profiles=dev-local \
    -Dspring-boot.run.jvmArguments="-Dserver.port=${PORT} -Dcms.hazelcast.cluster-name=${CLUSTER_NAME} -Dhazelcast.discovery.enabled=false -Dhazelcast.network.join.multicast.enabled=false" \
    > "$LOG_FILE" 2>&1 &
) >/dev/null 2>&1

# dev-local honours X-User-* dev identity headers, so an AA role is enough to prove readiness.
for i in $(seq 1 60); do
  sleep 5
  CODE="$(curl -s -m 5 -o /dev/null -w "%{http_code}" \
            -H "X-User-Roles: AA_DO" -H "X-User-Id: aa_do_001" \
            "http://localhost:${PORT}/api/v1/appeals/stats" 2>/dev/null || true)"
  if [ "$CODE" = "200" ]; then
    echo "==> READY on ${PORT} after $((i * 5))s"

    # Verify isolation from the LOG, not from the fact we passed a flag. A wrong property name is
    # accepted silently and the node still joins the shared cluster, so every cache-dependent
    # assertion in this session would be meaningless without this check.
    if grep -q "Cluster name: ${CLUSTER_NAME}" "$LOG_FILE" 2>/dev/null; then
      echo "==> Hazelcast cluster is namespaced: ${CLUSTER_NAME}"
    else
      echo "!!  WARNING: could not confirm 'Cluster name: ${CLUSTER_NAME}' in the log." >&2
      echo "!!  This node may have joined a SHARED cluster. Cache-dependent assertions" >&2
      echo "!!  (translations, categories, banks, form-config, mre-rules) cannot be trusted." >&2
      grep -iE "Cluster name:|Members \{" "$LOG_FILE" | tail -3 >&2
    fi
    if grep -qE "Members \{size:([2-9]|[1-9][0-9])" "$LOG_FILE" 2>/dev/null; then
      echo "!!  WARNING: this node has PEERS — caches are shared. Stop the other instance." >&2
      grep -E "Members \{size:" "$LOG_FILE" | tail -2 >&2
    fi
    exit 0
  fi
  if grep -qE "Port ${PORT} was already in use|BUILD FAILURE|APPLICATION FAILED TO START" "$LOG_FILE" 2>/dev/null; then
    echo "ERROR: backend failed to start. Tail of ${LOG_FILE}:" >&2
    tail -25 "$LOG_FILE" >&2
    exit 1
  fi
done

echo "ERROR: backend on ${PORT} did not become ready in 300s. Tail of ${LOG_FILE}:" >&2
tail -25 "$LOG_FILE" >&2
exit 1
