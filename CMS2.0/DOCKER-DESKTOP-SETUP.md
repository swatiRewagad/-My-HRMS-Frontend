# CMS 2.0 — Docker Desktop Local Setup Guide

## Prerequisites

- **Docker Desktop** installed and running (Windows/Mac)
- **Java 21** (for running Spring Boot services natively)
- **Node.js 20+** and Angular CLI (for frontend)
- **Maven 3.9+** (for building Java services)
- At least **8 GB RAM** allocated to Docker Desktop (Settings > Resources)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  Docker Desktop (Infrastructure)                                 │
│  ┌──────────┐ ┌───────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐│
│  │Oracle XE │ │ Kafka │ │Keycloak  │ │OpenSearch│ │Kafka UI ││
│  │  :1521   │ │ :9092 │ │  :8180   │ │  :9200   │ │  :8090  ││
│  └──────────┘ └───────┘ └──────────┘ └──────────┘ └─────────┘│
│  ┌──────────┐ ┌─────────┐                                      │
│  │Prometheus│ │ Grafana │                                       │
│  │  :9090   │ │  :3000  │                                       │
│  └──────────┘ └─────────┘                                       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  Host Machine (Services — run natively with Maven)               │
│  ┌──────────────┐ ┌────────────┐ ┌───────────────────┐         │
│  │cms-api-gateway│ │cms-backend │ │cms-portal-frontend│         │
│  │    :8080      │ │   :8082    │ │      :4200        │         │
│  └──────────────┘ └────────────┘ └───────────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

---

## Step 1: Start Infrastructure with Docker Compose

```bash
cd CMS2.0/deployment/docker
docker-compose up -d
```

This starts:
| Service | Port | URL |
|---------|------|-----|
| Oracle XE 21 | 1521 | `jdbc:oracle:thin:@localhost:1521/XEPDB1` |
| Kafka (KRaft) | 9092 | `localhost:9092` |
| Keycloak 26 | 8180 | http://localhost:8180 |
| OpenSearch | 9200 | http://localhost:9200 |
| OpenSearch Dashboards | 5601 | http://localhost:5601 |
| Kafka UI | 8090 | http://localhost:8090 |
| Prometheus | 9090 | http://localhost:9090 |
| Grafana | 3000 | http://localhost:3000 (admin/admin) |

Wait for all containers to be healthy:
```bash
docker-compose ps
```

Oracle takes ~2-3 minutes to start. Wait until `cms-oracle` shows `healthy`.

---

## Step 2: Initialize Oracle Database

Once Oracle is healthy, run the schema script:

```bash
# Connect to Oracle container
docker exec -it cms-oracle sqlplus cms_app/cms_app_password@//localhost:1521/XEPDB1

# Inside sqlplus, run V5 (the latest consolidated DDL+DML):
@/path/to/CMS2.0/database/oracle/V5__complete_ddl_dml.sql

# Or from host machine using Oracle client:
sqlplus cms_app/cms_app_password@//localhost:1521/XEPDB1 @database/oracle/V5__complete_ddl_dml.sql
```

**Alternative — Copy file into container and run:**
```bash
docker cp ../../database/oracle/V5__complete_ddl_dml.sql cms-oracle:/tmp/
docker exec -it cms-oracle bash -c "sqlplus cms_app/cms_app_password@//localhost:1521/XEPDB1 @/tmp/V5__complete_ddl_dml.sql"
```

Then run the V6 migration (new columns added since V5):
```bash
docker cp ../../database/oracle/V6__alter_tables_new_columns.sql cms-oracle:/tmp/
docker exec -it cms-oracle bash -c "sqlplus cms_app/cms_app_password@//localhost:1521/XEPDB1 @/tmp/V6__alter_tables_new_columns.sql"
```

---

## Step 3: Configure Keycloak

1. Open http://localhost:8180 → Admin Console → Login: `admin` / `admin`
2. Create Realm: `cms`
3. Create Clients:

**Client: `cms-portal`**
- Client type: OpenID Connect
- Root URL: `http://localhost:4200`
- Valid redirect URIs: `http://localhost:4200/*`
- Web origins: `http://localhost:4200`
- Access type: Public
- PKCE: Enabled (S256)

**Client: `cms-officer-portal`**
- Client type: OpenID Connect
- Root URL: `http://localhost:4201`
- Valid redirect URIs: `http://localhost:4201/*`
- Web origins: `http://localhost:4201`
- Access type: Public
- PKCE: Enabled (S256)

4. Create Realm Roles:
```
CEPC: DO, REVIEWER, INCHARGE, CA, ADMIN, CP
RBIO: RBIO_OFFICER, RBIO_SUPERVISOR, RBIO_CONCILIATOR, RBIO_ADJUDICATOR, RBIO_ADMIN
RE:   RE_NODAL_OFFICER, RE_PNO
AA:   AA_REGISTRAR, AA_BENCH_OFFICER, AA_AUTHORITY, AA_ADMIN
```

5. Create Test Users:

| Username | Password | Roles |
|----------|----------|-------|
| deo1 | password | DO |
| reviewer1 | password | REVIEWER |
| incharge1 | password | INCHARGE |
| rbio_officer1 | password | RBIO_OFFICER |
| rbio_admin1 | password | RBIO_ADMIN |

---

## Step 4: Start Backend Services

### Option A: Dev-Local Profile (H2 — No Oracle needed, simplest)

If you just want to run the frontend with a working backend, use H2 in-memory mode:

```bash
cd CMS2.0/cms-backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev-local
```

This runs on port 8082 with H2, Kafka on localhost:9092, Keycloak on localhost:9090.

> **Note:** For dev-local, change Keycloak port in `application-dev-local.yml` from 9090 to 8180 (Docker compose Keycloak port), or run Keycloak natively on 9090.

### Option B: Full Stack with Oracle (Docker)

Set environment variables and run without dev-local profile:

```bash
# Terminal 1: API Gateway
cd CMS2.0/cms-api-gateway
set KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/cms
set KEYCLOAK_JWK_URI=http://localhost:8180/realms/cms/protocol/openid-connect/certs
mvn spring-boot:run

# Terminal 2: Backend (Oracle mode)
cd CMS2.0/cms-backend
set DB_HOST=localhost
set DB_PORT=1521
set DB_SERVICE_NAME=XEPDB1
set DB_USERNAME=cms_app
set DB_PASSWORD=cms_app_password
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
set KEYCLOAK_URL=http://localhost:8180
set CMS_ENCRYPTION_SECRET=docker-local-secret-key-min16
mvn spring-boot:run
```

---

## Step 5: Start Frontend

```bash
cd CMS2.0/cms-portal-frontend
npm install
ng serve --port 4200
```

For the officer portal:
```bash
cd CMS2.0/cms-frontend
npm install
ng serve --port 4201
```

---

## Step 6: Update Frontend Environment (if needed)

If using Docker Keycloak on port 8180, update `environment.ts`:

```typescript
// src/environments/environment.ts
export const environment = {
  ...
  keycloakUrl: 'http://localhost:8180',  // Docker Keycloak port
  ...
};
```

---

## Access Points

| Service | URL |
|---------|-----|
| Public Portal | http://localhost:4200 |
| Officer Portal | http://localhost:4201 |
| API Gateway | http://localhost:8080 |
| Backend (direct) | http://localhost:8082 |
| H2 Console (dev-local) | http://localhost:8082/h2-console |
| Keycloak Admin | http://localhost:8180 |
| Kafka UI | http://localhost:8090 |
| OpenSearch | http://localhost:9200 |
| Grafana | http://localhost:3000 |

---

## Docker Commands Reference

```bash
# Start all infrastructure
docker-compose up -d

# Stop all
docker-compose down

# Stop and remove volumes (DELETES DATA)
docker-compose down -v

# View logs
docker-compose logs -f oracle-db
docker-compose logs -f keycloak
docker-compose logs -f kafka

# Check health status
docker-compose ps

# Restart specific service
docker-compose restart keycloak

# Shell into Oracle container
docker exec -it cms-oracle bash

# Shell into Kafka container
docker exec -it cms-kafka bash
```

---

## Troubleshooting

### Oracle container not starting
- Ensure Docker has at least 4GB RAM allocated
- Check logs: `docker-compose logs oracle-db`
- If port 1521 is in use: `netstat -an | findstr 1521`

### Kafka connection refused
- Wait 30 seconds after container starts
- Verify: `docker exec cms-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list`

### Keycloak errors
- Ensure `keycloak-db` (Postgres) is healthy before Keycloak starts
- Admin console: http://localhost:8180/admin (admin/admin)

### Frontend CORS errors
- Ensure `cms.cors.allowed-origins` in backend includes `http://localhost:4200`
- For dev-local profile this is already configured

### Backend CMS_ENCRYPTION_SECRET error
- Always use `dev-local` profile for local development, OR
- Set env: `set CMS_ENCRYPTION_SECRET=any-string-min-16-chars`

---

## Minimal Quick Start (Fastest Path)

If you just want to get the app running quickly:

```bash
# 1. Start Docker infrastructure
cd CMS2.0/deployment/docker
docker-compose up -d kafka keycloak keycloak-db

# 2. Start backend with H2 (no Oracle needed)
cd CMS2.0/cms-backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev-local

# 3. Start frontend
cd CMS2.0/cms-portal-frontend
npm install && ng serve --port 4200

# 4. Open browser
# http://localhost:4200
```

> For the "Minimal Quick Start" path, you don't need Oracle, OpenSearch, or Prometheus. The backend uses H2 in-memory database and all features work except full-text search.
