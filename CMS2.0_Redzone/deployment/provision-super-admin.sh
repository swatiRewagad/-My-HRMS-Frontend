#!/usr/bin/env bash
#
# Creates the SUPER_ADMIN realm role and grants it to the existing admin accounts.
#
# WHY A SEPARATE SCRIPT, AND WHY IT IS SAFE TO RUN ALONGSIDE OTHER SESSIONS
#
#   provision-aa-roles.sh must NOT be extended for this. That script sets the realm's
#   unmanagedAttributePolicy, which is a read-modify-write of the whole realm representation: two
#   concurrent runs silently discard each other's changes. This script only ever CREATES a role and
#   ADDS a role mapping — both additive, both idempotent, neither touching realm-level settings — so
#   it can run at any time without coordinating with the sessions sharing this Keycloak.
#
# WHY SUPER_ADMIN EXISTS
#
#   UST468 requires that only a Super Admin may change an office's assignment logic, and UST638
#   requires the same for the reminder-sweep interval. Before this, SUPER_ADMIN appeared NOWHERE in
#   the repo — not in Java, SQL, JSON, or the realm — and ADMIN was the top role. Guarding those
#   endpoints with ADMIN would have let every existing ADMIN change national assignment policy and
#   scheduler timing, which is a wider grant than the stories allow.
#
#   SUPER_ADMIN is ADDITIVE, not a replacement: ADMIN keeps everything it had. Guards are written as
#   {"SUPER_ADMIN", "ADMIN"} only where a story explicitly permits both.
#
# Usage:
#   ./deployment/provision-super-admin.sh                 # localhost:9090, realm cms
#   KEYCLOAK_URL=... REALM=... ./deployment/provision-super-admin.sh
set -uo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:9090}"
REALM="${REALM:-cms}"
KC_ADMIN="${KC_ADMIN:-admin}"
KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-admin}"
ROLE_NAME="SUPER_ADMIN"
ROLE_DESCRIPTION="Highest privilege: per-office assignment strategy (UST468) and runtime scheduler/config changes (UST638). Additive to ADMIN."

# Accounts that should hold the role. These are the REAL realm usernames — note they are not
# admin/admin_001/rbio_admin_001, which do not exist here; assuming those names silently grants
# nothing, which is why this list was taken from the ADMIN and RBIO_ADMIN role memberships.
GRANTEES="${SUPER_ADMIN_GRANTEES:-cms.admin orbio_admin_001}"

echo "==> Keycloak ${KEYCLOAK_URL}, realm ${REALM}"

TOKEN="$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli" -d "username=${KC_ADMIN}" -d "password=${KC_ADMIN_PASSWORD}" \
  -d "grant_type=password" | grep -o '"access_token":"[^"]*"' | sed 's/"access_token":"//; s/"$//')"

if [ -z "${TOKEN}" ]; then
  echo "!! Could not obtain an admin token. Is Keycloak running on ${KEYCLOAK_URL}?" >&2
  exit 1
fi

# ── Role ─────────────────────────────────────────────────────────────────────────────────────────
HTTP="$(curl -s -o /dev/null -w '%{http_code}' \
  "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/${ROLE_NAME}" -H "Authorization: Bearer ${TOKEN}")"

if [ "${HTTP}" = "200" ]; then
  echo "==> role ${ROLE_NAME} already exists"
else
  CREATED="$(curl -s -o /dev/null -w '%{http_code}' -X POST \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/roles" \
    -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
    -d "{\"name\":\"${ROLE_NAME}\",\"description\":\"${ROLE_DESCRIPTION}\"}")"
  if [ "${CREATED}" = "201" ] || [ "${CREATED}" = "409" ]; then
    echo "==> role ${ROLE_NAME} created (HTTP ${CREATED})"
  else
    echo "!! Failed to create ${ROLE_NAME} (HTTP ${CREATED})" >&2
    exit 1
  fi
fi

# The mapping endpoint wants the FULL role representation in a JSON array, so it is fetched rather
# than hand-built: a hand-built object missing `id` or `containerId` is rejected with HTTP 400.
ROLE_JSON_FILE="$(mktemp)"
{ printf '['
  curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/${ROLE_NAME}" -H "Authorization: Bearer ${TOKEN}"
  printf ']'
} > "${ROLE_JSON_FILE}"

# ── Grants ───────────────────────────────────────────────────────────────────────────────────────
for USERNAME in ${GRANTEES}; do
  # `?username=X&exact=true` returns a JSON ARRAY, so the id is taken from the first element. A
  # dotted-path JSON reader cannot index a top-level array here.
  USER_ID="$(curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/users?username=${USERNAME}&exact=true" \
    -H "Authorization: Bearer ${TOKEN}" | grep -o '"id":"[^"]*"' | head -1 | sed 's/"id":"//; s/"$//')"

  if [ -z "${USER_ID}" ]; then
    echo "   -- ${USERNAME}: no such user, skipped"
    continue
  fi

  # Adding a role the user already holds is a no-op 204, so this is safely re-runnable.
  GRANTED="$(curl -s -o /dev/null -w '%{http_code}' -X POST \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${USER_ID}/role-mappings/realm" \
    -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
    --data-binary "@${ROLE_JSON_FILE}")"
  echo "   -- ${USERNAME}: HTTP ${GRANTED}"
done

rm -f "${ROLE_JSON_FILE}"

echo "==> ${ROLE_NAME} holders:"
curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/roles/${ROLE_NAME}/users" \
  -H "Authorization: Bearer ${TOKEN}" | grep -o '"username":"[^"]*"' | sed 's/"username":"/   /; s/"$//'
