#!/bin/sh

# 1. Substitute env vars in nginx config
envsubst '${API_BACKEND_HOST} ${API_BACKEND_PORT}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf

# 2. Generate runtime config.json from template using env vars from ConfigMap
if [ -f /usr/share/nginx/html/assets/config-template.json ]; then
  envsubst '${API_BASE_URL} ${KEYCLOAK_URL} ${KEYCLOAK_REALM} ${KEYCLOAK_CLIENT_ID}' \
    < /usr/share/nginx/html/assets/config-template.json \
    > /usr/share/nginx/html/assets/config.json
  echo "[entrypoint] config.json generated:"
  cat /usr/share/nginx/html/assets/config.json
fi

exec nginx -g 'daemon off;'
