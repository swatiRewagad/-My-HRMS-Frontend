---
name: CMS 2.0 Run Commands
description: How to start CMS2.0 services locally — backend (dev-local profile), frontends, Keycloak, E2E tests
type: reference
originSessionId: d78f00e0-a7bb-4bae-b629-ed6be54dea22
---
## Quick Start (backend only, no Oracle/Kafka)
```bash
cd CMS2.0/cms-backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev-local
```
H2 console: http://localhost:8082/h2-console

## Frontends
```bash
cd CMS2.0/cms-portal-frontend && ng serve --port 4200
cd CMS2.0/cms-frontend && ng serve --port 4201
```

## Keycloak (local, port 9090 to avoid gateway conflict)
```bash
cd c:/tools/keycloak-26.0.0
bin/kc.bat start-dev --http-port=9090
```

## OpenSearch
```bash
cd c:/tools/opensearch-2.18.0
bin/opensearch.bat
```

## E2E Tests
```bash
cd CMS2.0/cms-e2e-tests
npx playwright test tests/launch.spec.ts --headed --project=chromium
npx playwright test tests/public-portal2.spec.ts --headed --project=chromium
npx playwright show-report
```

## Full stack env vars (when Kafka + Oracle on server)
```bash
export DB_HOST=<ip> DB_PORT=1521 DB_SERVICE_NAME=CMSPDB
export DB_USERNAME=cms_user DB_PASSWORD=<pass>
export KAFKA_BOOTSTRAP_SERVERS=<ip>:9092
export CMS_ENCRYPTION_SECRET=<min-16-chars>
export KEYCLOAK_URL=http://localhost:9090
```
