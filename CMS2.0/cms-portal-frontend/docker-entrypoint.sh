#!/bin/sh
# Substitute environment variables in nginx config
envsubst '${API_BACKEND_HOST} ${API_BACKEND_PORT}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf
exec nginx -g 'daemon off;'
