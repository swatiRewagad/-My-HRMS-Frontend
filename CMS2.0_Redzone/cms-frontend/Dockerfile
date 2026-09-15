# Stage 1: Build Angular application
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --prefer-offline --no-audit

COPY . .
ARG NG_CONFIG=openshift
RUN npx ng build --configuration=${NG_CONFIG}

# Stage 2: Serve with Nginx
FROM nginx:1.27-alpine

RUN rm /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/default.conf.template
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

COPY --from=builder /app/dist/cms-frontend/browser /usr/share/nginx/html

RUN addgroup -S frontend && adduser -S frontend -G frontend && \
    chown -R frontend:frontend /var/cache/nginx /var/log/nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && chown frontend:frontend /var/run/nginx.pid

USER frontend

ENV API_BACKEND_HOST=cms-backend
ENV API_BACKEND_PORT=8080

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://localhost:80/healthz || exit 1

ENTRYPOINT ["/docker-entrypoint.sh"]
