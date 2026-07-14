---
name: CMS 2.0 Deployment & Infrastructure
description: OpenShift deployment flow, ConfigMaps, Secrets, Docker images, runtime config injection, Kafka outbox pattern
type: project
originSessionId: d78f00e0-a7bb-4bae-b629-ed6be54dea22
---
## OpenShift Deployment
- Namespace: `rbi-cms`
- Frontend image: `rebit-quay-quay-image-registry.apps.hocpclz.rebit.local/buildpiper/rhel8_nginx_124:amd64-9.7`
- Backend image: `eclipse-temurin:21-jre-alpine` (multi-stage build)
- Config: `cms-infrastructure/openshift/configmaps.yaml` (17 ConfigMaps) + `secrets.yaml`
- Deployment YAMLs: `deployment/openshift/`

**Why:** SIT/Production runs on OpenShift. Local dev uses H2 + `dev-local` profile to avoid Oracle/Kafka dependency.

**How to apply:** When user asks about deployment, ConfigMaps, Dockerfiles, or env vars, reference `cms-infrastructure/openshift/` and `deployment/openshift/`.

## Frontend Runtime Config
1. `src/assets/config-template.json` has `${API_BASE_URL}`, `${KEYCLOAK_URL}`, etc.
2. Dockerfile CMD: `envsubst < config-template.json > config.json && nginx -g 'daemon off;'`
3. Angular fetches `/config.json` at startup (no rebuild per environment)
4. ConfigMaps provide env vars: `API_BASE_URL`, `KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID`

## Kafka Outbox Pattern
- Services write to `OUTBOX_EVENTS` table (same transaction as business logic)
- `cms-outbox-publisher` polls every 5s, max 5 retries
- Publishes to topics: complaint.ingested, .assigned, .inprogress, .escalated, .resolved, .closed
- Dead letter: `complaint.dlq`

## CMS_ENCRYPTION_SECRET
- Required by `EncryptionKeyService` for PII encryption (HKDF-SHA256)
- Must be ≥16 chars
- `dev-local` profile provides dummy value
- Prod: injected via OpenShift Secret
