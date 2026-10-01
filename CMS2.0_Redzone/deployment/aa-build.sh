#!/usr/bin/env bash
#
# Serialised Maven wrapper for the parallel AA sessions (S2A / S2B / S2C).
#
# WHY THIS EXISTS: all three sessions build the SAME cms-backend/target/ directory. Two concurrent
# `mvn compile` runs interleave their writes to target/classes, and a session that starts a backend
# while another is mid-compile loads half-written class files. The resulting failures belong to
# nobody, are not reproducible, and get misattributed to whichever session happens to notice them.
#
# This takes an exclusive lock for the duration of the Maven run, so the three sessions queue instead
# of colliding without ever needing to know about each other.
#
# Usage:
#   ./deployment/aa-build.sh compile
#   ./deployment/aa-build.sh test -Dtest=MyTest
#   ./deployment/aa-build.sh test                      # full suite
#   ./deployment/aa-build.sh -q compile                # extra args pass straight through
#
# Everything after the first word is forwarded to Maven verbatim. -o (offline) and -q are added for
# you unless you pass them yourself.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/cms-backend"
LOCK_FILE="${TMPDIR:-/tmp}/cms-aa-maven.lock"
WAIT_TIMEOUT="${AA_BUILD_LOCK_TIMEOUT:-1800}"   # seconds to wait for the lock

if [ "$#" -eq 0 ]; then
  echo "usage: aa-build.sh <maven-goal> [extra maven args]" >&2
  exit 2
fi

# Offline by default: the repo is fully primed and offline builds are markedly faster, which
# shortens how long each session holds the lock.
MVN_ARGS=("$@")
case " ${MVN_ARGS[*]} " in
  *" -o "*|*" --offline "*) ;;
  *) MVN_ARGS=(-o "${MVN_ARGS[@]}") ;;
esac

run_maven() {
  echo "==> [$(date +%H:%M:%S)] mvn ${MVN_ARGS[*]}  (lock held by pid $$)"
  ( cd "$BACKEND_DIR" && mvn "${MVN_ARGS[@]}" )
  return $?
}

# flock is the correct tool and ships with Git-Bash/MSYS on this machine. If it is missing we fall
# back to an atomic mkdir spin-lock rather than silently running unserialised — an unserialised run
# is the exact failure mode this script exists to prevent.
if command -v flock >/dev/null 2>&1; then
  exec 9>"$LOCK_FILE"
  if ! flock -w "$WAIT_TIMEOUT" 9; then
    echo "ERROR: timed out after ${WAIT_TIMEOUT}s waiting for the Maven lock." >&2
    echo "       Another AA session is still building. Re-run, or raise AA_BUILD_LOCK_TIMEOUT." >&2
    exit 75
  fi
  START=$(date +%s)
  run_maven
  STATUS=$?
  echo "==> mvn finished in $(( $(date +%s) - START ))s (exit ${STATUS})"
  exit $STATUS
fi

LOCK_DIR="${LOCK_FILE}.d"
WAITED=0
while ! mkdir "$LOCK_DIR" 2>/dev/null; do
  if [ "$WAITED" -ge "$WAIT_TIMEOUT" ]; then
    echo "ERROR: timed out after ${WAIT_TIMEOUT}s waiting for ${LOCK_DIR}." >&2
    echo "       If no build is running, remove it: rm -rf '${LOCK_DIR}'" >&2
    exit 75
  fi
  [ "$WAITED" -eq 0 ] && echo "==> waiting for another AA session's Maven build to finish..."
  sleep 3
  WAITED=$((WAITED + 3))
done
# shellcheck disable=SC2064
trap "rmdir '$LOCK_DIR' 2>/dev/null || true" EXIT INT TERM

START=$(date +%s)
run_maven
STATUS=$?
echo "==> mvn finished in $(( $(date +%s) - START ))s (exit ${STATUS})"
exit $STATUS
