#!/usr/bin/env bash
# PreToolUse hook: blocks Edit/Write/MultiEdit calls whose content matches a
# hard-stop security pattern. Exit 2 blocks the tool call and feeds stderr
# back to Claude as the reason; exit 0 allows it.
set -euo pipefail

input=$(cat)
content=$(printf '%s' "$input" | jq -r '.tool_input | .. | strings' 2>/dev/null || true)

patterns=(
  'AKIA[0-9A-Z]{16}'                              # AWS access key id
  '-----BEGIN (RSA|EC|OPENSSH|PRIVATE) KEY-----'  # private key material
  'password[[:space:]]*=[[:space:]]*["\x27][^"\x27]+["\x27]'
  'eval\('
  'verify[[:space:]]*=[[:space:]]*False'
  'InsecureSkipVerify:[[:space:]]*true'
  'TrustAllCerts'
  'X509TrustManager'
  'ObjectInputStream'
  'yaml\.unsafe_load'
  'pickle\.loads?\('
)

for p in "${patterns[@]}"; do
  if printf '%s' "$content" | grep -qiE "$p"; then
    echo "BLOCKED by security-hook.sh: content matches forbidden pattern: $p" >&2
    exit 2
  fi
done

exit 0
