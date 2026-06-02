# RBI Complaint Management System (CMS 2.0) - Architecture & Developer Guide

**Version:** 1.0.0-SNAPSHOT  
**Date:** May 2026  
**Platform:** Java 21 / Spring Boot 3.4.1 / Angular 21 / Oracle / Kafka / jBPM / Drools / Keycloak

---

## Table of Contents

1. [Tech Stack Details](#1-tech-stack-details)
2. [Architecture Overview](#2-architecture-overview)
3. [Module Structure](#3-module-structure)
4. [Functional Flow](#4-functional-flow)
5. [Tech Stack Integration](#5-tech-stack-integration)
6. [Developer Guidelines](#6-developer-guidelines)
7. [Build & Run Instructions](#7-build--run-instructions)
8. [Build Fixes Applied](#8-build-fixes-applied)
9. [Sample Use Case with Flow](#9-sample-use-case-with-flow)
10. [API Reference](#10-api-reference)
11. [Deployment](#11-deployment)

---

## 1. Tech Stack Details

### Backend

| Technology | Version | Purpose |
|---|---|---|
| Java (Microsoft OpenJDK) | 21.0.11 | Runtime platform |
| Spring Boot | 3.4.1 | Application framework |
| Spring Cloud | 2024.0.0 | Cloud-native patterns (Gateway) |
| Spring Cloud Gateway MVC | - | API Gateway & routing |
| Spring Data JPA | (via Boot) | ORM / Repository pattern |
| Spring Kafka | (via Boot) | Kafka producer/consumer |
| Spring Security OAuth2 | (via Boot) | JWT-based authentication |
| Apache Kafka (KRaft) | 3.7.0 | Event streaming / async messaging |
| Oracle Database XE | 21 | Primary RDBMS |
| Drools | 9.44.0.Final | Business rules engine |
| jBPM | 7.74.1.Final | Business process management (BPMN) |
| Keycloak | 26.0 | Identity & Access Management (IAM) |
| OpenSearch | 2.18.0 | Full-text search indexing |
| MapStruct | 1.6.3 | Object mapping (DTO <-> Entity) |
| Lombok | 1.18.36 | Boilerplate reduction |
| SpringDoc OpenAPI | 2.7.0 | REST API documentation |
| ShedLock | - | Distributed scheduler locking |
| Micrometer | 1.14.2 | Metrics collection |

### Frontend

| Technology | Version | Purpose |
|---|---|---|
| Angular | 21.2.8 | SPA framework |
| TypeScript | 5.x | Language |
| Angular Signals | (built-in) | Reactive state management |
| PrimeIcons | - | Icon library |
| RxJS | - | Reactive programming |

### Infrastructure & DevOps

| Technology | Version | Purpose |
|---|---|---|
| Docker / Docker Compose | 3.9 | Local containerized development |
| Maven | 3.9.9 | Build tool (multi-module) |
| Prometheus | 2.54.0 | Metrics aggregation |
| Grafana | 11.3.0 | Dashboards & alerting |
| OpenShift / Kubernetes | - | Production orchestration |
| Oracle JDBC Driver | 23.4.0.24.05 | Database connectivity |
| Testcontainers | - | Integration testing (Oracle + Kafka) |
| Apache HttpClient5 | (via Boot BOM) | OpenSearch transport layer |

### Build Tools

| Tool | Purpose |
|---|---|
| Maven 3.9.9 | Multi-module Java build |
| npm / ng CLI | Angular build and serve |
| maven-compiler-plugin 3.13.0 | Java 21 compilation with annotation processors |
| spring-boot-maven-plugin | Executable JAR packaging |

---

## 2. Architecture Overview

### High-Level Architecture Diagram (Textual)

```
+--------------------+          +------------------+          +------------------+
|   Angular Portal   |  REST    |   API Gateway    |  Routes  |  Microservices   |
|  (cms-portal-frontend)| -----> | (Spring Cloud    | -------> | (Eligibility,    |
|                    |          |  Gateway MVC)    |          |  Ingestion, etc) |
+--------------------+          +------------------+          +------------------+
                                        |                             |
                                        | OAuth2/JWT                  | JPA
                                        v                             v
                                +------------------+          +------------------+
                                |    Keycloak      |          |  Oracle DB (XE)  |
                                | (IAM/RBAC/SSO)   |          |                  |
                                +------------------+          +------------------+
                                                                      |
                                                              Transactional Outbox
                                                                      |
                                                                      v
+------------------+          +------------------+          +------------------+
|  Outbox Publisher | -------> |   Apache Kafka   | <------> | Consumer Services|
| (ShedLock poll)  |  Produce |  (KRaft mode)    | Consume  | (Assignment,     |
+------------------+          +------------------+          |  Notification,   |
                                                            |  Search, Workflow)|
                                                            +------------------+
                                                                      |
                                                              +-------+-------+
                                                              |               |
                                                              v               v
                                                     +-------------+  +-------------+
                                                     |  OpenSearch  |  |  Drools /   |
                                                     | (Full-text)  |  |  jBPM       |
                                                     +-------------+  +-------------+
```

### Key Architectural Patterns

1. **Microservices Architecture** - 15 independent services, each with its own responsibility
2. **Transactional Outbox Pattern** - Guarantees at-least-once Kafka delivery within DB transactions
3. **Event-Driven Architecture** - Services communicate asynchronously via Kafka topics
4. **API Gateway Pattern** - Single entry point with routing, rate limiting, and RBAC
5. **CQRS (partial)** - Writes go to Oracle, reads for search go to OpenSearch
6. **Business Rules Engine** - Drools DRL files externalize eligibility, assignment, and escalation logic
7. **BPM Workflow** - jBPM BPMN2 process models complaint lifecycle states
8. **Distributed Locking** - ShedLock prevents duplicate outbox publishing in multi-instance deployments

---

## 3. Module Structure

```
CMS2.0/
├── pom.xml                          (Parent POM - Maven multi-module)
├── cms-common/                      (Shared enums, DTOs, exceptions, utilities)
├── cms-portal-ui-contracts/         (API interface contracts + request/response DTOs)
├── cms-api-gateway/                 (Spring Cloud Gateway MVC, security, rate limiting)
├── cms-eligibility-service/         (Eligibility questionnaire + Drools rule evaluation)
├── cms-ingestion-service/           (Complaint registration + outbox event creation)
├── cms-workflow-service/            (jBPM process management + Kafka listener)
├── cms-rules-service/               (Drools container + DRL rules for assignment/escalation)
├── cms-assignment-service/          (Rule-driven complaint routing + Kafka consumer)
├── cms-sla-monitor-service/         (Scheduled SLA breach detection)
├── cms-notification-service/        (Email/SMS notifications + Kafka consumer)
├── cms-audit-service/               (Immutable audit log + REST API)
├── cms-storage-service/             (File/attachment storage)
├── cms-search-service/              (OpenSearch indexing + full-text search + Kafka consumer)
├── cms-outbox-publisher/            (Scheduled poller: Oracle outbox -> Kafka)
├── cms-infrastructure/              (Kafka topic config, cache, Keycloak JWT config)
├── cms-portal-frontend/             (Angular 21 SPA)
├── database/                        (Oracle DDL, Flyway migration scripts)
└── deployment/
    ├── docker/                      (docker-compose.yml, prometheus.yml, init-scripts)
    └── openshift/                   (Kubernetes/OpenShift manifests)
```

### Module Details

| Module | Type | Port | Dependencies |
|---|---|---|---|
| cms-common | Library JAR | N/A | Shared by all services |
| cms-portal-ui-contracts | Library JAR | N/A | API contracts for frontend |
| cms-api-gateway | Spring Boot App | 8080 | Spring Cloud Gateway, OAuth2, Bucket4j |
| cms-eligibility-service | Spring Boot App | 8081 | Drools, Oracle, MapStruct |
| cms-ingestion-service | Spring Boot App | 8082 | Oracle, Outbox pattern |
| cms-workflow-service | Spring Boot App | 8083 | jBPM, Kafka consumer |
| cms-rules-service | Spring Boot App | 8084 | Drools (assignment/escalation DRL) |
| cms-assignment-service | Spring Boot App | 8085 | Drools, Kafka consumer/producer |
| cms-sla-monitor-service | Spring Boot App | 8086 | Oracle, Scheduled tasks |
| cms-notification-service | Spring Boot App | 8087 | Email/SMS, Kafka consumer |
| cms-audit-service | Spring Boot App | 8088 | Oracle |
| cms-storage-service | Spring Boot App | 8089 | File system / object storage |
| cms-search-service | Spring Boot App | 8090 | OpenSearch, Kafka consumer |
| cms-outbox-publisher | Spring Boot App | 8091 | ShedLock, Oracle, Kafka producer |
| cms-infrastructure | Library JAR | N/A | Kafka topic config, Keycloak JWT |
| cms-portal-frontend | Angular App | 4200 | Angular 21, Signals |

---

## 4. Functional Flow

### 4.1 Complaint Registration Flow (End-to-End)

```
[Customer]  ---> [Angular Portal]  ---> [API Gateway]  ---> [Eligibility Service]
                        |                                           |
                        | (If eligible)                             | Drools rules
                        v                                           v
                 [Complaint Form]  ---> [API Gateway]  ---> [Ingestion Service]
                                                                    |
                                                          +-------- | --------+
                                                          |         |         |
                                                          v         v         v
                                                    [Oracle DB] [History] [Outbox]
                                                                              |
                                                              (Scheduled Poll)|
                                                                              v
                                                                    [Kafka: complaint.ingested]
                                                                              |
                                        +------------------+------------------+------------------+
                                        |                  |                  |                  |
                                        v                  v                  v                  v
                               [Assignment Svc]   [Workflow Svc]    [Search Svc]     [Notification Svc]
                                        |                  |                  |                  |
                                        | Drools           | jBPM             | OpenSearch       | Email/SMS
                                        v                  v                  v                  v
                              [Kafka: complaint.assigned] [Start Process]  [Index Doc]     [Welcome Email]
```

### 4.2 Eligibility Check Flow

1. Customer visits portal landing page
2. Clicks "File a Complaint" - eligibility questionnaire appears
3. Questions fetched dynamically from `QUESTION_MASTER` table
4. Customer answers all mandatory questions
5. Answers posted to Eligibility Service
6. Drools rules evaluate (`eligibility-rules.drl`):
   - Court matter pending? -> NOT ELIGIBLE
   - Bank not approached? -> NOT ELIGIBLE
   - 30-day waiting period not met? -> NOT ELIGIBLE
   - Duplicate complaint? -> NOT ELIGIBLE
   - All passed? -> ELIGIBLE
7. Result + reason stored in `ELIGIBILITY_AUDIT` table
8. If ELIGIBLE -> redirect to complaint registration form

### 4.3 Complaint Lifecycle (BPMN Process)

```
[START: Complaint Ingested]
       |
       v
[Business Rule Task: Auto Assignment] --- Drools assigns team based on category
       |
       v
[User Task: Officer Review & Accept] --- Human task, assigned to team
       |                    |
       |              [Boundary Timer: 30 days]
       |                    |
       v                    v
[Exclusive Gateway]   [Escalation Task]
   /         \              |
  v           v             |
[Under Review] [Escalation] |
  \           /             |
   v         v              |
[Resolution Gateway] <------+
       |
       v
[Service Task: Notify Customer]
       |
       v
[Intermediate Timer: 7 days wait]
       |
       v
[END: Complaint Closed]
```

### 4.4 SLA Monitoring Flow

- Scheduled cron job runs every hour
- Queries complaints approaching or breaching SLA
- Runs Drools escalation rules (`escalation-rules.drl`):
  - 80% SLA elapsed -> Level 1 (Notify Officer)
  - 100% SLA elapsed -> Level 2 (Reassign to Supervisor)
  - 150% SLA elapsed -> Level 3 (Escalate to Management)
  - High-value complaint (> 10 Lakh) -> Auto Level 2

### 4.5 Kafka Topic Map

| Topic | Producer | Consumers |
|---|---|---|
| `complaint.ingested` | Outbox Publisher | Assignment, Workflow, Search, Notification |
| `complaint.assigned` | Assignment Service | Workflow, Notification, Search |
| `complaint.inprogress` | Workflow Service | Notification, Search |
| `complaint.escalated` | SLA Monitor / Assignment | Workflow, Notification, Search |
| `complaint.resolved` | Workflow Service | Notification, Search |
| `complaint.closed` | Workflow Service | Search, Audit |
| `complaint.dlq` | Any (on failure) | Manual review |

### 4.6 CRPC Module Flow (Frontend — Angular)

The CRPC (Complaint Redressal Processing Centre) module handles the entire lifecycle of a complaint from ingestion through resolution. See `cms-portal-frontend/CRPC-PORTAL-DOCUMENTATION.md` for comprehensive details.

**End-to-End Flow:**

```
[Email/Physical Letter/Portal/CPGRAMS]
        |
        v
[Email Syndication — Admin Queue]
  - Auto-ingest emails → OCR → Dedup → Round-robin assign to DEO
        |
        v
[DEO Assessment (5 Tabs)]
  1. Summary (editable complainant details)
  2. Attachments (upload/view/remove)
  3. Maintainability Assessment (8 weighted questions)
  4. Auto-Closure Screening (sequential, triggers closure clauses)
  5. Route (Decision → Reviewer selection → Sent for Approval)
        |
        v
[Reviewer Queue]
  - 4 Modules: Summary | Email | Attachments | History
  - Actions: Approve → Complaint# generated
            Send Back → Returns to DEO
            Not a Complaint → Closed
```

**Key CRPC Roles:** DEO, Reviewer, CRPC Head, Admin, In-Charge, Help Desk

**Physical Letter Flow:** Scan/Upload → OCR pre-fill → Wizard → Auto-creates draft → Same 5-tab view

**Admin Management:**
- DEO Management: `/email-syndication/deo-management` (add/remove/threshold/leave)
- Reviewer Management: `/crpc/reviewer-management` (add/remove/threshold/leave/region)

---

## 5. Tech Stack Integration

### 5.1 Spring Boot + Oracle (JPA)

- **Connection**: Oracle JDBC driver `ojdbc11` version 23.4.0.24.05
- **Entities**: JPA annotations with Oracle-specific sequences (`@SequenceGenerator`)
- **Repositories**: Spring Data JPA (`JpaRepository`) for CRUD
- **Transactions**: `@Transactional` on service methods
- **Schema**: Managed via Flyway migration (`database/V1__initial_schema.sql`)

### 5.2 Transactional Outbox Pattern (Oracle + ShedLock + Kafka)

**Why**: Guarantees that every complaint event is published to Kafka exactly once, even if the application crashes between DB write and Kafka send.

**How it works**:
1. `IngestionService.registerComplaint()` saves both `ComplaintMaster` AND `OutboxEvent` in a **single transaction**
2. `OutboxPublisherService` runs on a fixed schedule (every 5 seconds)
3. `@SchedulerLock` (ShedLock + `SHEDLOCK` table) prevents multiple instances from polling simultaneously
4. Publisher reads PENDING events, sends to Kafka, marks as PUBLISHED
5. Failed events increment `retryCount`; after 5 retries, marked as FAILED

### 5.3 Drools Rule Engine Integration

**Configuration**: `KieContainer` bean created from classpath DRL files

**Rule Files**:
- `eligibility-rules.drl` - 5 rules for complaint eligibility pre-check
- `assignment-rules.drl` - 7 rules for category-based team assignment
- `escalation-rules.drl` - 4 rules for SLA breach level determination

**Integration Pattern**:
```java
KieSession kieSession = kieContainer.newKieSession();
kieSession.insert(fact);  // Insert fact object
kieSession.fireAllRules(); // Execute matching rules
kieSession.dispose();     // Clean up
// fact object now contains rule outputs (e.g., assignedTeam)
```

### 5.4 jBPM Workflow Engine

- **Process Definition**: `complaint-lifecycle.bpmn2` (BPMN2 XML)
- **Runtime**: `RuntimeManager` creates KIE sessions per process instance
- **Tasks**: BusinessRuleTask (Drools), UserTask (human), ServiceTask (code), Timer events
- **Integration**: Kafka listener starts process on `complaint.ingested` event
- **SLA Timer**: 30-day boundary timer triggers escalation if officer doesn't resolve

### 5.5 Keycloak OAuth2/OIDC

- **API Gateway** acts as OAuth2 Resource Server
- JWT tokens validated against Keycloak's JWKS endpoint
- **Role Extraction**: Custom `JwtAuthenticationConverter` reads `realm_access.roles` from JWT
- **RBAC**: Roles like `cms_admin`, `cms_officer`, `cms_supervisor` control API access
- **Gateway Routes** enforce role-based access per service path

### 5.6 Spring Cloud Gateway MVC

- Routes defined programmatically in `SecurityConfig`
- Rate limiting via Bucket4j (`RateLimitFilter`)
- CORS configured for Angular frontend origin
- Path-based routing to downstream microservices

### 5.7 OpenSearch Full-Text Search

- **Indexing**: Kafka consumer (`ComplaintIndexingListener`) indexes every complaint event
- **Search**: Multi-match query across subject, description, complainant name, entity name
- **Filter**: Term queries for status-based filtering
- **Transport**: Apache HttpClient5 async NIO connection manager

### 5.8 Angular Frontend + Backend Integration

- Angular services call REST APIs via `HttpClient`
- Environment-based API URL configuration (`environment.ts` / `environment.prod.ts`)
- Standalone components with lazy-loaded routes
- Angular Signals for reactive UI state

---

## 6. Developer Guidelines

### 6.1 Prerequisites

| Tool | Minimum Version | Installation |
|---|---|---|
| JDK | 21+ | Microsoft OpenJDK or Oracle JDK |
| Maven | 3.9+ | `C:\tools\maven\apache-maven-3.9.9` |
| Node.js | 20+ | For Angular CLI |
| Docker Desktop | Latest | For local infrastructure |
| Git | 2.x | Source control |

**Environment Variables**:
```bash
JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
PATH=%JAVA_HOME%\bin;%PATH%
```

### 6.2 Project Structure Conventions

**Package naming**:
```
com.rbi.cms.<module-name>
├── controller/    (REST controllers)
├── service/       (Business logic)
├── repository/    (JPA repositories)
├── entity/        (JPA entities)
├── dto/           (Request/Response DTOs)
├── mapper/        (MapStruct mappers)
├── config/        (Spring @Configuration)
├── listener/      (Kafka consumers)
└── validator/     (Input validation)
```

### 6.3 Adding a New Microservice

1. Create a new directory under `CMS2.0/`: e.g., `cms-new-service/`
2. Add a `pom.xml` inheriting from `cms-parent`
3. Add the module name to the parent `pom.xml` `<modules>` section
4. Add dependency on `cms-common` for shared DTOs/enums
5. Create `src/main/java/com/rbi/cms/<name>/` package structure
6. Create a Spring Boot main class annotated with `@SpringBootApplication`
7. Add `application.yml` with service-specific config
8. Build: `mvn clean install -DskipTests -rf :cms-new-service`

### 6.4 Adding a New Drools Rule

1. Create/edit `.drl` file in `src/main/resources/rules/` of the relevant service
2. Define the `import` for the fact class (use `com.rbi.cms.common.dto.*` for shared facts)
3. Set appropriate `salience` (higher = runs first)
4. Pattern match using `when` clause
5. Modify fact object in `then` clause, call `update($fact)`
6. No recompile needed if using file-based KieContainer refresh

**Example**:
```drl
rule "New Category - Assign to New Team"
    salience 100
    when
        $fact : AssignmentFact(category == "NEW_CATEGORY")
    then
        $fact.setAssignedTeam("NEW_TEAM");
        update($fact);
end
```

### 6.5 Adding a New Kafka Consumer

1. Add `spring-kafka` dependency to the service POM
2. Create a listener class annotated with `@Component`
3. Implement a method with `@KafkaListener(topics = KafkaTopics.TOPIC_NAME, groupId = "group-id")`
4. Deserialize the message using `ObjectMapper`
5. Implement idempotency (check if event already processed)
6. Use `Acknowledgment.acknowledge()` for manual offset commit
7. Handle exceptions: log + throw RuntimeException for DLQ routing

```java
@KafkaListener(topics = KafkaTopics.COMPLAINT_INGESTED, groupId = "my-group")
public void onEvent(String message, Acknowledgment ack) {
    ComplaintEvent event = objectMapper.readValue(message, ComplaintEvent.class);
    // Process...
    ack.acknowledge();
}
```

### 6.6 Adding a New API Endpoint

1. Define the API contract in `cms-portal-ui-contracts` (interface + DTOs)
2. Implement the controller in the target service
3. Use `ApiResponse<T>` wrapper from `cms-common` for consistent response format
4. Add the route in API Gateway's `SecurityConfig`
5. Document with SpringDoc annotations (`@Operation`, `@Tag`)
6. Add the path to Keycloak role mapping if protected

### 6.7 Adding a New Angular Component

1. Generate: `ng generate component components/my-component --standalone`
2. Add lazy-loaded route in `app.routes.ts`
3. Use Angular Signals for state (`signal()`, `computed()`)
4. Inject services using `inject()` function
5. Call backend via service classes (never direct HTTP in components)
6. Use `ApiResponse<T>` model for type-safe response handling

### 6.8 Database Changes

1. Create a new file: `database/V2__description.sql`
2. Follow Oracle DDL conventions: uppercase table/column names
3. Add sequences for new tables
4. Add indexes for frequently queried columns
5. Always add `CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL`
6. Use foreign keys for referential integrity
7. Apply via Flyway on application startup

### 6.9 Code Quality Standards

- **No raw SQL** in services - use JPA repositories
- **No business logic in controllers** - delegate to services
- **Use MapStruct** for entity <-> DTO mapping (no manual field copying)
- **Use Lombok** - `@Data`, `@Builder`, `@RequiredArgsConstructor`
- **Strict TypeScript** - no `any` types in Angular code
- **Immutable audit trail** - never update/delete audit records
- **Transactional boundaries** - `@Transactional` at service layer

---

## 7. Build & Run Instructions

### 7.1 Build the Backend (All Modules)

```bash
# Set Java 21
export JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.11.10-hotspot"
export PATH="$JAVA_HOME/bin:$PATH"

# Build all modules (skip tests for speed)
cd C:/Projects/My-HRMS-Frontend/CMS2.0
mvn clean install -DskipTests

# Build with tests
mvn clean install
```

### 7.2 Start Infrastructure (Docker)

```bash
cd deployment/docker
docker-compose up -d

# Verify all containers are healthy
docker-compose ps

# Services available at:
# Oracle: localhost:1521
# Kafka: localhost:9092
# Keycloak: http://localhost:8180 (admin/admin)
# OpenSearch: http://localhost:9200
# OpenSearch Dashboards: http://localhost:5601
# Kafka UI: http://localhost:8090
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
```

### 7.3 Run Individual Backend Services

```bash
# Eligibility Service
cd cms-eligibility-service
mvn spring-boot:run

# Ingestion Service
cd cms-ingestion-service
mvn spring-boot:run

# API Gateway
cd cms-api-gateway
mvn spring-boot:run

# Outbox Publisher
cd cms-outbox-publisher
mvn spring-boot:run
```

### 7.4 Run the Angular Frontend

```bash
cd cms-portal-frontend
npm install          # First time only
npx ng serve         # Development server at http://localhost:4200
npx ng build         # Production build
```

### 7.5 Run Integration Tests

```bash
# Requires Docker for Testcontainers
mvn verify -pl cms-eligibility-service,cms-ingestion-service
```

---

## 8. Build Fixes Applied

During the initial project setup, the following compilation issues were identified and resolved:

### Fix 1: WorkflowService - Return Type Mismatch

**Problem**: `ProcessInstance.getId()` returns `String` in KIE API, but method declared `Long` return type.

**File**: `cms-workflow-service/.../WorkflowService.java:41`

**Fix**: Changed return type from `Long` to `String`:
```java
// Before
public Long startComplaintWorkflow(ComplaintEvent event) { ... }

// After
public String startComplaintWorkflow(ComplaintEvent event) { ... }
```

### Fix 2: AssignmentService - Missing Dependency on Rules DTOs

**Problem**: `AssignmentFact` was defined in `cms-rules-service` package (`com.rbi.cms.rules.service`) but needed by `cms-assignment-service`. Cross-service dependency on a Spring Boot app JAR is not supported.

**Fix**: Moved `AssignmentFact` and `EscalationFact` DTOs to `cms-common` module under `com.rbi.cms.common.dto` package. Updated all imports in:
- `cms-assignment-service/.../AssignmentService.java`
- `cms-assignment-service/.../ComplaintIngestedAssignmentListener.java`
- `cms-rules-service/resources/rules/assignment-rules.drl`
- `cms-rules-service/resources/rules/escalation-rules.drl`

### Fix 3: SearchService - Apache HttpClient5 Missing

**Problem**: OpenSearch Java Client 2.18.0 uses `org.apache.hc.client5` but these weren't in the module's classpath.

**File**: `cms-search-service/pom.xml`

**Fix**: Added explicit dependencies:
```xml
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.httpcomponents.core5</groupId>
    <artifactId>httpcore5</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.httpcomponents.core5</groupId>
    <artifactId>httpcore5-h2</artifactId>
</dependency>
```

### Fix 4: SearchService - OpenSearch FieldValue API Change

**Problem**: OpenSearch Java Client 2.18 requires `FieldValue.of(string)` instead of passing string directly to `.value()`.

**File**: `cms-search-service/.../ComplaintSearchService.java:61`

**Fix**:
```java
// Before
.value(status)

// After
.value(FieldValue.of(status))
```

### Fix 5: Infrastructure Module - Missing Spring Security Dependency

**Problem**: `KeycloakJwtConfig` uses Spring Security OAuth2 classes but the POM didn't declare the dependency.

**File**: `cms-infrastructure/pom.xml`

**Fix**: Added:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

### Fix 6: Angular - TypeScript Params Type Error

**Problem**: Conditional params object `category ? { category } : {}` created a union type incompatible with HttpClient's expected `Record<string, string>`.

**File**: `cms-portal-frontend/src/app/services/eligibility.service.ts`

**Fix**:
```typescript
// Before
const params = category ? { category } : {};

// After
const params: Record<string, string> = {};
if (category) {
  params['category'] = category;
}
```

### Fix 7: JAVA_HOME Configuration

**Problem**: Maven was using Java 17.0.18 instead of Java 21.

**Fix**: Set JAVA_HOME permanently:
```bash
# PowerShell (persistent)
[Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot', 'User')
```

---

## 9. Sample Use Case with Flow

### Use Case: Customer Files an ATM Cash Withdrawal Complaint

**Scenario**: A customer attempted to withdraw Rs. 15,00,000 from an ATM but the cash was not dispensed, while the amount was debited from their account. They approached their bank 35 days ago but received no resolution.

---

#### Step 1: Customer Visits Portal

**URL**: `http://localhost:4200`

The Angular `LandingComponent` renders a hero section with "File a Complaint" and "Track Complaint" buttons.

---

#### Step 2: Eligibility Check

**Component**: `EligibilityQuestionnaireComponent`

**API Call**: `GET /api/v1/eligibility/questions`

**Response**: 6 questions from `QUESTION_MASTER` table

**Customer Answers**:
| Question | Answer |
|---|---|
| Court matter pending? | NO |
| Approached bank? | YES |
| 30 days elapsed? | YES |
| Duplicate complaint? | NO |
| Category? | ATM |
| Jurisdiction? | MAHARASHTRA |

**API Call**: `POST /api/v1/eligibility/check`

**Drools Execution** (eligibility-rules.drl):
- Rule "Court Matter Pending" -> skipped (courtMatterPending = false)
- Rule "Bank Not Approached" -> skipped (approachedBank = true)
- Rule "Waiting Period" -> skipped (waitingPeriodCompleted = true)
- Rule "Duplicate" -> skipped (duplicateComplaint = false)
- Rule "All Checks Passed" -> **FIRES** -> reasonCode = "ALL_CHECKS_PASSED"

**Result**: ELIGIBLE - redirect to complaint form

---

#### Step 3: Complaint Registration

**Component**: `ComplaintFormComponent`

**Customer fills**:
- Complainant Name: "Rajesh Kumar"
- Email: "rajesh@example.com"
- Phone: "9876543210"
- Entity: "State Bank of India, Andheri Branch"
- Subject: "ATM cash not dispensed"
- Description: "Attempted withdrawal of Rs 15,00,000..."
- Amount Involved: 1500000
- Transaction Date: 2026-04-20
- Attachments: bank_statement.pdf, atm_receipt.jpg

**API Call**: `POST /api/v1/complaints/register`

**Backend Processing** (`IngestionService.registerComplaint()`):

1. **Validation**: Channel, category, required fields checked
2. **ID Generation**: `ComplaintIdGenerator.generate()` -> `CMS-2026-000147`
3. **Priority**: Amount > 10 Lakh -> `Priority.HIGH`
4. **SLA**: `Instant.now() + 30 days` = 2026-06-25
5. **Save to Oracle**: `COMPLAINT_MASTER` record
6. **History**: Status `null -> NEW`, action "COMPLAINT_REGISTERED"
7. **Outbox Event**: `OUTBOX_EVENT` record with topic=`complaint.ingested`, status=PENDING

**Database State After Step 3**:
```sql
-- COMPLAINT_MASTER
COMPLAINT_ID='CMS-2026-000147', STATUS='NEW', PRIORITY='HIGH',
CATEGORY='ATM', ASSIGNED_TEAM=NULL, SLA_DUE_DATE='2026-06-25'

-- OUTBOX_EVENT
STATUS='PENDING', TOPIC='complaint.ingested', AGGREGATE_ID='CMS-2026-000147'
```

---

#### Step 4: Outbox Publisher Polls & Publishes

**Service**: `OutboxPublisherService` (runs every 5 seconds)

1. `@Scheduled(fixedDelay=5000)` triggers
2. `@SchedulerLock` acquires lock in `SHEDLOCK` table
3. Finds 1 PENDING event for `CMS-2026-000147`
4. `kafkaTemplate.send("complaint.ingested", "CMS-2026-000147", payload)`
5. On success: `status = PUBLISHED`, `publishedAt = now()`
6. Lock released

**Kafka State**: Message on `complaint.ingested` topic, partition keyed by complaint ID

---

#### Step 5: Assignment Service Consumes Event

**Listener**: `ComplaintIngestedAssignmentListener.onComplaintIngested()`

1. Deserializes `ComplaintEvent` from Kafka message
2. Calls `AssignmentService.assignComplaint("CMS-2026-000147", "ATM", "HIGH", 1500000.0)`
3. **Drools Execution** (assignment-rules.drl):
   - Rule "ATM Complaints - Assign to ATM Team" -> **FIRES** -> assignedTeam = "ATM_TEAM"
   - Rule "High Priority - Auto Escalate" -> **FIRES** -> escalated=true, escalationLevel=1
4. Publishes `complaint.assigned` event to Kafka with `assignedTo=ATM_TEAM`
5. Acknowledges original message

---

#### Step 6: Workflow Service Starts BPMN Process

**Listener**: `ComplaintIngestedListener` (in workflow service)

1. Receives `complaint.ingested` event
2. Calls `WorkflowService.startComplaintWorkflow(event)`
3. jBPM creates process instance from `complaint-lifecycle.bpmn2`
4. **Business Rule Task** ("Auto Assignment") executes
5. **User Task** ("Officer Review & Accept") created, assigned to "ATM_TEAM"
6. **Boundary Timer** starts: 30-day SLA clock

---

#### Step 7: Search Service Indexes Document

**Listener**: `ComplaintIndexingListener`

1. Receives `complaint.ingested` event
2. Creates OpenSearch document with all complaint fields
3. Indexes to `cms-complaints` index with ID = `CMS-2026-000147`
4. Full-text searchable immediately

---

#### Step 8: Notification Service Sends Welcome Email

**Listener**: `ComplaintEventNotificationListener`

1. Receives `complaint.ingested` event
2. Sends email to `rajesh@example.com`:
   - Subject: "Complaint Registered - CMS-2026-000147"
   - Body: "Your complaint has been registered. Expected resolution by 2026-06-25."

---

#### Step 9: Officer Reviews & Resolves (Day 5)

- Officer from ATM_TEAM logs in via Keycloak
- Sees pending task in their queue
- Reviews the complaint, contacts the bank
- Initiates resolution: "Amount refunded to customer account"
- BPMN process moves through Investigation Gateway -> Under Review -> Resolution

---

#### Step 10: Customer Notification & Auto-Close

1. `NotifyCustomerTask` (BPMN ServiceTask) sends resolution email
2. Intermediate Timer waits 7 days for customer response
3. Timer expires -> Process reaches EndEvent
4. Status: `CLOSED`
5. Final event published to `complaint.closed` topic

---

#### Final Database State:
```sql
-- COMPLAINT_MASTER
COMPLAINT_ID='CMS-2026-000147', STATUS='CLOSED', PRIORITY='HIGH',
CATEGORY='ATM', ASSIGNED_TEAM='ATM_TEAM', ASSIGNED_TO='officer_001',
RESOLUTION_SUMMARY='Amount refunded to customer account',
RESOLVED_AT='2026-05-31', CLOSED_AT='2026-06-07'

-- COMPLAINT_HISTORY (5 records)
1. null -> NEW (COMPLAINT_REGISTERED)
2. NEW -> ASSIGNED (AUTO_ASSIGNMENT)
3. ASSIGNED -> IN_PROGRESS (OFFICER_ACCEPTED)
4. IN_PROGRESS -> RESOLVED (RESOLUTION_SUBMITTED)
5. RESOLVED -> CLOSED (AUTO_CLOSED_AFTER_7_DAYS)
```

---

## 10. API Reference

### Eligibility Service

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/eligibility/questions` | Get active eligibility questions |
| GET | `/api/v1/eligibility/questions?category=ATM` | Filter questions by category |
| POST | `/api/v1/eligibility/check` | Submit answers and check eligibility |

### Ingestion Service

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/complaints/register` | Register a new complaint |
| POST | `/api/v1/complaints/{id}/attachments` | Upload file attachments |
| GET | `/api/v1/complaints/{id}` | Get complaint details |

### Workflow Service

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/workflow/{id}/transition` | Transition complaint state |
| POST | `/api/v1/workflow/{id}/escalate` | Escalate a complaint |

### Audit Service

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/audit/{entityType}/{entityId}` | Get audit trail for entity |
| GET | `/api/v1/audit/recent` | Get recent audit entries |

### Search Service

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/search?q=text&page=0&size=20` | Full-text search |
| GET | `/api/v1/search/status/{status}` | Filter by status |

### Standard Response Format

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... },
  "correlationId": "uuid-v4",
  "timestamp": "2026-05-26T10:30:00Z"
}
```

---

## 11. Deployment

### 11.1 Docker Compose (Local Development)

```bash
cd deployment/docker
docker-compose up -d     # Start all infrastructure
docker-compose logs -f   # View logs
docker-compose down -v   # Stop and remove volumes
```

**Infrastructure Containers**:
| Container | Image | Port | Purpose |
|---|---|---|---|
| cms-oracle | gvenzl/oracle-xe:21-slim | 1521 | Primary database |
| cms-kafka | apache/kafka:3.7.0 | 9092 | Event streaming (KRaft) |
| cms-keycloak | keycloak:26.0 | 8180 | IAM/SSO |
| cms-keycloak-db | postgres:16-alpine | 5432 | Keycloak backend DB |
| cms-opensearch | opensearch:2.18.0 | 9200 | Full-text search |
| cms-opensearch-dashboards | opensearch-dashboards:2.18.0 | 5601 | Search UI |
| cms-kafka-ui | kafka-ui:latest | 8090 | Kafka management |
| cms-prometheus | prometheus:v2.54.0 | 9090 | Metrics |
| cms-grafana | grafana:11.3.0 | 3000 | Dashboards |

### 11.2 OpenShift / Kubernetes (Production)

Manifests in `deployment/openshift/`:

| File | Description |
|---|---|
| `namespace.yaml` | Creates `rbi-cms` namespace |
| `configmap-common.yaml` | Shared configuration (DB URLs, Kafka bootstrap) |
| `secret-db.yaml` | Database credentials (base64 encoded) |
| `deployment-api-gateway.yaml` | Gateway + Route (TLS edge termination) |
| `deployment-eligibility-service.yaml` | With liveness/readiness probes |
| `deployment-ingestion-service.yaml` | With PVC for attachments (ReadWriteMany) |
| `deployment-workflow-service.yaml` | jBPM with higher memory limits |
| `deployment-outbox-publisher.yaml` | Recreate strategy (single instance) |

**Key Production Considerations**:
- Outbox Publisher uses `Recreate` deployment strategy (only 1 replica to prevent duplicate publishing)
- Ingestion Service uses `PersistentVolumeClaim` for file attachments (`ReadWriteMany`)
- All services have `/actuator/health/liveness` and `/actuator/health/readiness` probes
- API Gateway Route uses TLS edge termination
- Kafka topics configured with 6 partitions, 3 replicas (production)

### 11.3 Monitoring

- **Prometheus** scrapes `/actuator/prometheus` from each service
- **Grafana** dashboards show request rates, error rates, JVM metrics
- **Kafka UI** shows topic lag, consumer group offsets
- **OpenSearch Dashboards** for search index monitoring

---

## Appendix A: Database Tables

| Table | Purpose | Key Columns |
|---|---|---|
| QUESTION_MASTER | Eligibility questionnaire config | QUESTION_CODE, EXPECTED_ANSWER, RULE_ATTRIBUTE |
| ELIGIBILITY_AUDIT | Immutable eligibility check records | SESSION_ID, OUTCOME, REASON_CODE |
| COMPLAINT_MASTER | Primary complaint entity | COMPLAINT_ID, STATUS, ASSIGNED_TO, SLA_DUE_DATE |
| COMPLAINT_HISTORY | State transition audit trail | COMPLAINT_ID, PREVIOUS_STATUS, NEW_STATUS |
| ATTACHMENT_METADATA | File upload metadata | COMPLAINT_ID, STORAGE_PATH, CHECKSUM |
| OUTBOX_EVENT | Transactional outbox for Kafka | TOPIC, PAYLOAD, STATUS, RETRY_COUNT |
| AUDIT_LOG | Cross-cutting audit log | ENTITY_TYPE, ENTITY_ID, ACTION |
| SHEDLOCK | Distributed scheduler lock | NAME, LOCK_UNTIL, LOCKED_BY |

---

## Appendix B: Environment Configuration

Each service reads from `application.yml`. Key properties:

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521/XEPDB1
    username: cms_app
    password: cms_app_password
    driver-class-name: oracle.jdbc.OracleDriver
  kafka:
    bootstrap-servers: localhost:9092
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/rbi-cms

cms:
  outbox:
    poll-interval-ms: 5000
    batch-size: 100
    max-retries: 5
  opensearch:
    host: localhost
    port: 9200
    scheme: http
```

---

*Document generated: May 2026 | CMS 2.0 Phase 1 MVP*
