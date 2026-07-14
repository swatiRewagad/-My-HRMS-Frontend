---
name: CMS 2.0 Project Overview
description: RBI Complaint Management System — microservices architecture, tech stack, module map, ports, deployment strategy
type: project
originSessionId: d78f00e0-a7bb-4bae-b629-ed6be54dea22
---
CMS 2.0 is a microservices-based complaint management system for RBI (Reserve Bank of India) under the Integrated Ombudsman Scheme.

**Why:** Replaces legacy complaint handling with a full digital lifecycle — filing, eligibility, assignment, workflow, SLA monitoring, appeals, and orders.

**How to apply:** This is the primary active project. When the user asks about CMS, microservices, Kafka, OpenShift deployment, or E2E tests, it refers to `c:\Projects\My-HRMS-Frontend\CMS2.0\`.

## Tech Stack
- Frontend: Angular 21, PrimeNG 21, keycloak-js 26
- Backend: Java 21, Spring Boot 3.4.1, Spring Security OAuth2
- Database: Oracle (prod/SIT), H2 (dev-local profile)
- Messaging: Kafka (Transactional Outbox Pattern)
- Auth: Keycloak 26 (realm: rbi-cms, OIDC/PKCE)
- Search: OpenSearch 2.18
- Workflow: Camunda/Drools
- E2E: Playwright
- Deployment: OpenShift (RHEL8 nginx for frontends, Temurin 21 for backends)

## Key Modules & Ports
- cms-portal-frontend: 4200 (public + staff SSO)
- cms-frontend (officer): 4201
- cms-api-gateway: 8080
- cms-backend (monolith): 8082
- cms-eligibility-service: 8081
- cms-ingestion-service: 8082
- cms-workflow-service: 8083
- cms-rules-service: 8084
- cms-assignment-service: 8085
- cms-sla-monitor-service: 8086
- cms-notification-service: 8087
- cms-audit-service: 8088
- cms-outbox-publisher: 8089
- cms-storage-service: 8090
- cms-search-service: 8091

## Roles
- CEPC: DO, REVIEWER, INCHARGE, CA, ADMIN, CP
- RBIO: RBIO_OFFICER, RBIO_SUPERVISOR, RBIO_CONCILIATOR, RBIO_ADJUDICATOR, RBIO_ADMIN
- RE: RE_NODAL_OFFICER, RE_PNO
- AA: AA_REGISTRAR, AA_BENCH_OFFICER, AA_AUTHORITY, AA_ADMIN
