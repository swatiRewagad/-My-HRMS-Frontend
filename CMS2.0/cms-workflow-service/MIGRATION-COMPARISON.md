# jBPM 7 → jBPM 10 Migration — Change Summary

## Overview

| Aspect | Before (jBPM 7.74.1) | After (jBPM 10.2.0) |
|--------|----------------------|---------------------|
| Spring Boot | Incompatible with 3.x (uses `javax.persistence`) | Fully compatible with Spring Boot 3.4.x |
| Persistence API | `javax.persistence` | `jakarta.persistence` |
| Process Engine | KIE Server / RuntimeManager | Kogito Process API (embedded) |
| WorkItem Handler | `org.kie.api.runtime.process.WorkItemHandler` | `DefaultKogitoWorkItemHandler` (lifecycle-based) |
| Configuration | Manual `RuntimeManager` bean setup | Auto-discovery from `src/main/resources/processes/` |
| BPMN2 Process File | Same file (no change needed) | Same file (backward compatible) |

---

## Files Changed

### 1. Parent POM (`CMS2.0/pom.xml`)

| Property | Old | New |
|----------|-----|-----|
| `jbpm.version` | `7.74.1.Final` | `10.2.0` |

**Dependency Management section:**
- Removed: `org.jbpm:jbpm-runtime-manager`, `org.jbpm:jbpm-human-task-core`, `org.jbpm:jbpm-persistence-jpa`
- Added: `org.jbpm:jbpm-with-drools-spring-boot-starter:10.2.0`, `org.jbpm:jbpm-spring-boot-starter:10.2.0`

---

### 2. Workflow Service POM (`cms-workflow-service/pom.xml`)

| Section | Old | New |
|---------|-----|-----|
| Description | Kogito BPMN workflow engine | jBPM 10 BPMN workflow engine |
| Properties | `kogito.version=9.44.0.Final` | `jbpm.version=10.2.0` |
| Main dependency | `org.kie.kogito:kogito-spring-boot-starter` + `kogito-addons-*` (3 deps) | `org.jbpm:jbpm-with-drools-spring-boot-starter:10.2.0` (1 dep) |
| Drools deps | `drools-compiler`, `drools-mvel`, `kie-api` (explicit) | Removed (pulled transitively by jBPM 10 starter) |

---

### 3. Deleted Files

| File | Reason |
|------|--------|
| `config/JbpmConfig.java` | Replaced by auto-configuration (KogitoConfig.java) |
| `config/CmsWorkItemHandlerFactory.java` | jBPM 10 uses Spring bean discovery for handlers |
| `service/WorkflowService.java` | Replaced by KogitoWorkflowService.java |
| `service/JbpmTaskQueryService.java` | Replaced by KogitoTaskQueryService.java |

---

### 4. New/Rewritten Files

#### `config/KogitoConfig.java` (NEW)
- Empty `@Configuration` class — jBPM 10 auto-discovers BPMN2 from `src/main/resources/processes/`
- No manual RuntimeManager, no KieSession setup

#### `service/KogitoWorkflowService.java` (NEW — replaces WorkflowService.java)

| Aspect | Old (jBPM 7) | New (jBPM 10) |
|--------|-------------|---------------|
| Process reference | `RuntimeManager` → `KieSession` | `Process<? extends Model>` bean (auto-generated) |
| Start workflow | `kieSession.startProcess("id", params)` | `complaintProcess.createInstance(model).start()` |
| Complete task | `taskService.start(taskId, userId)` + `taskService.complete(taskId, userId, data)` | `instance.completeWorkItem(workItem.getId(), results)` |
| Signal event | `kieSession.signalEvent("signal", data, processId)` | `instance.send(SignalFactory.of("signal", data))` |
| Query tasks | `taskService.getTasksAssignedAsPotentialOwner(userId)` | `process.instances().stream()` + filter work items |
| Profile | None (always active) | `@Profile("!dev-local")` — skipped in local dev |

#### `service/KogitoTaskQueryService.java` (NEW — replaces JbpmTaskQueryService.java)
- Uses `complaintProcess.instances().stream()` to iterate active process instances
- Filters by `ProcessInstance.STATE_ACTIVE`
- Extracts `WorkItem` parameters for task details

#### `handler/DraftCreationHandler.java` (REWRITTEN)

| Aspect | Old (jBPM 7) | New (jBPM 10) |
|--------|-------------|---------------|
| Interface | `implements WorkItemHandler` | `extends DefaultKogitoWorkItemHandler` |
| Execute method | `executeWorkItem(WorkItem, WorkItemManager)` | `activateWorkItemHandler(KogitoWorkItemManager, KogitoWorkItemHandler, KogitoWorkItem, WorkItemTransition)` |
| Complete signal | `manager.completeWorkItem(id, results)` | `return Optional.of(this.completeTransition(phaseId, results))` |
| Abort method | `abortWorkItem(WorkItem, WorkItemManager)` | Inherited from DefaultKogitoWorkItemHandler |
| Bean registration | `@Component` + factory registration | `@Component("fully.qualified.name")` + `getName()` |
| Import package | `org.kie.api.runtime.process.*` | `org.kie.kogito.internal.process.workitem.*` |

#### `handler/NotificationHandler.java` (REWRITTEN)
- Same pattern as DraftCreationHandler above

#### `handler/PortalRegistrationHandler.java` (REWRITTEN)
- Same pattern as DraftCreationHandler above

---

### 5. Modified Files

#### `cms-common/.../enums/ComplaintStatus.java`
- Added enum values: `APPROVED`, `REJECTED`, `SENT_BACK`

#### `controller/WorkflowController.java`
- References `ComplaintWorkflowProcessor` interface (unchanged)
- Uses `instanceof KogitoWorkflowService kogitoService` pattern for `completeHumanTask`

#### `application-dev-local.yml`
- Added: `spring.jpa.defer-datasource-initialization: true`
- Changed: `kogito.persistence.type: infinispan` (in-memory for dev)

---

### 6. Unchanged Files

| File | Notes |
|------|-------|
| `processes/complaint-lifecycle.bpmn2` | Same BPMN2 spec works with both jBPM 7 and 10 |
| `service/DevLocalWorkflowService.java` | Dev-local simulation — no jBPM dependency |
| `service/DevLocalTaskQueryService.java` | Dev-local simulation |
| `service/ComplaintWorkflowProcessor.java` | Interface unchanged |
| `service/RoundRobinAssignmentService.java` | No jBPM dependency |
| `listener/ComplaintIngestedListener.java` | Kafka listener unchanged |
| `entity/*`, `repository/*`, `dto/*` | No jBPM dependency |

---

## Key API Mapping Reference

```java
// OLD (jBPM 7)
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;

// NEW (jBPM 10)
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;

// Process API
import org.kie.kogito.Model;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.WorkItem;
import org.kie.kogito.process.SignalFactory;  // was Sig in earlier Kogito versions
```

---

## Maven Dependency Diff

```xml
<!-- REMOVED from cms-workflow-service/pom.xml -->
<dependency>
    <groupId>org.kie.kogito</groupId>
    <artifactId>kogito-spring-boot-starter</artifactId>
    <version>9.44.0.Final</version>
</dependency>
<dependency>
    <groupId>org.kie.kogito</groupId>
    <artifactId>kogito-addons-spring-boot-process-management</artifactId>
    <version>9.44.0.Final</version>
</dependency>
<dependency>
    <groupId>org.kie.kogito</groupId>
    <artifactId>kogito-addons-spring-boot-persistence-jdbc</artifactId>
    <version>9.44.0.Final</version>
</dependency>
<dependency>
    <groupId>org.drools</groupId>
    <artifactId>drools-compiler</artifactId>
</dependency>
<dependency>
    <groupId>org.drools</groupId>
    <artifactId>drools-mvel</artifactId>
</dependency>
<dependency>
    <groupId>org.kie</groupId>
    <artifactId>kie-api</artifactId>
</dependency>

<!-- ADDED to cms-workflow-service/pom.xml -->
<dependency>
    <groupId>org.jbpm</groupId>
    <artifactId>jbpm-with-drools-spring-boot-starter</artifactId>
    <version>10.2.0</version>
</dependency>
```

---

## PR Checklist

When reviewing the pull request, focus on:

1. **Parent POM** — `jbpm.version` property change + new dependencyManagement entries
2. **Workflow POM** — dependency swap (Kogito starters → jBPM 10 starter)
3. **Handler classes** — new base class (`DefaultKogitoWorkItemHandler`), lifecycle-based API
4. **Service classes** — `KogitoWorkflowService` + `KogitoTaskQueryService` (new files)
5. **Config** — `KogitoConfig.java` replaces `JbpmConfig.java`
6. **Enum** — 3 new `ComplaintStatus` values
7. **BPMN2** — unchanged, no merge conflicts expected
8. **DevLocal services** — unchanged, verify no regressions
