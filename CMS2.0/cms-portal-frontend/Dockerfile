# Stage 1: Build Angular application
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --prefer-offline --no-audit

COPY . .
RUN npx ng build --configuration=production

# Stage 2: Serve with Nginx
FROM nginx:1.27-alpine

RUN rm /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist/cms-portal-frontend/browser /usr/share/nginx/html

RUN addgroup -S frontend && adduser -S frontend -G frontend && \
    chown -R frontend:frontend /var/cache/nginx /var/log/nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && chown frontend:frontend /var/run/nginx.pid

USER frontend

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
