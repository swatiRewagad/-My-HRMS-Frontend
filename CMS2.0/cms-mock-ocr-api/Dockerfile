FROM node:22-alpine
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --omit=dev

COPY server.js .

RUN addgroup -S ocr && adduser -S ocr -G ocr
USER ocr

EXPOSE 8000

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=5 \
  CMD wget -qO- http://localhost:8000/healthz || exit 1

CMD ["node", "server.js"]
