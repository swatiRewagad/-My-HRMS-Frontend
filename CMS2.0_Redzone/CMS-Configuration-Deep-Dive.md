# CMS 2.0 — Configuration Deep Dive: How Everything Connects

## What This Document Covers

For each step in the complaint lifecycle, this shows:
- **Which file** configures it
- **Which code** executes it
- **What triggers** the next step
- **How to change** it in the future

---

## STEP-BY-STEP: The Complete Configuration Chain

---

### t=0ms — Customer Clicks "Submit Complaint"

**What happens:** Angular frontend calls the API

**Configuration:**

| File | What It Configures |
|------|-------------------|
| `cms-portal-frontend/src/app/services/complaint.service.ts` | Frontend API call URL |
| `cms-api-gateway/src/main/resources/application.yml` | Gateway routes `/api/v1/complaints/**` → Ingestion Service |

**Code that executes:**
```
Frontend → POST http://localhost:8080/api/v1/complaints
Gateway  → routes to http://localhost:8082/cms-ingestion/api/v1/complaints
```

---

### t=50ms — Complaint Saved to Database

**What happens:** Ingestion Service saves complaint and publishes Kafka event

**Configuration:**

| File | What It Configures |
|------|-------------------|
| `cms-ingestion-service/.../service/IngestionService.java` (line 42-71) | The `registerComplaint()` method — saves to DB + publishes event |
| `cms-ingestion-service/.../service/DirectKafkaEventPublisher.java` | HOW the Kafka event is published (dev-local: directly to Kafka) |
| `cms-ingestion-service/src/main/resources/application-dev-local.yml` | Kafka broker address, producer config |
| `cms-common/.../config/KafkaTopics.java` (line 8) | Topic name: `complaint.ingested` |

**Code flow:**
```java
// IngestionService.java line 55-59
ComplaintMaster saved = complaintRepository.save(complaint);     // → DATABASE
recordHistory(saved.getComplaintId(), ...);                      // → History table
insertOutboxEvent(saved);                                        // → KAFKA EVENT

// insertOutboxEvent() at line 130-151
ComplaintEvent event = ComplaintEvent.builder()
    .complaintId(complaint.getComplaintId())
    .currentStatus(ComplaintStatus.NEW)
    .payload("{\"category\":\"ATM\",\"priority\":\"HIGH\"}")     // ← Rules need this
    .build();

eventPublisher.publishComplaintEvent(
    KafkaTopics.COMPLAINT_INGESTED,    // topic = "complaint.ingested"
    complaint.getComplaintId(),         // key (for Kafka partitioning)
    event                               // payload
);
```

**To change in future:**
- Add more data to the event → edit `insertOutboxEvent()` method
- Change topic name → edit `KafkaTopics.java` (all consumers must update too)
- Add a new event (e.g., complaint.validated) → add new topic + new publisher call

---

### t=200ms — Kafka Event Published

**What happens:** Message sits in Kafka topic `complaint.ingested`, waiting for consumers

**Configuration:**

| File | What It Configures |
|------|-------------------|
| Kafka broker (localhost:9092) | Message retention, partitions |
| `KafkaTopics.COMPLAINT_INGESTED` | Topic name = `"complaint.ingested"` |

**The Kafka message looks like:**
```json
{
  "eventId": "fb6e992e-...",
  "complaintId": "CMP-20260527-000001",
  "currentStatus": "NEW",
  "payload": "{\"category\":\"ATM\",\"priority\":\"HIGH\"}",
  "occurredAt": "2026-05-27T06:47:09Z",
  "correlationId": "ddaf94a7-..."
}
```

**Who is listening to this topic:**
- Workflow Service (consumer group: `cms-workflow-group`)
- Assignment Service (consumer group: `cms-assignment-group`)

Both consume the SAME message independently (different consumer groups).

---

### t=500ms — Workflow Service: Process Instance Started

**What happens:** Workflow Service consumes Kafka event → starts jBPM process

**Configuration:**

| File | What It Configures |
|------|-------------------|
| `cms-workflow-service/.../listener/ComplaintIngestedListener.java` | Kafka listener — WHAT triggers the workflow |
| `cms-workflow-service/.../service/WorkflowService.java` | HOW the jBPM process is started |
| `cms-workflow-service/src/main/resources/processes/complaint-lifecycle.bpmn2` | The PROCESS DEFINITION — what steps exist |
| `cms-workflow-service/src/main/resources/application-dev-local.yml` | Kafka consumer config (group-id, ack-mode) |

**Code flow:**
```java
// ComplaintIngestedListener.java — THE TRIGGER
@KafkaListener(topics = KafkaTopics.COMPLAINT_INGESTED, groupId = "cms-workflow-group")
public void onComplaintIngested(String message, Acknowledgment ack) {
    ComplaintEvent event = objectMapper.readValue(message, ComplaintEvent.class);
    workflowService.startComplaintWorkflow(event);    // ← Starts the process
    ack.acknowledge();                                 // ← Tells Kafka "processed"
}

// WorkflowService.java — STARTS THE BPMN PROCESS (production)
public String startComplaintWorkflow(ComplaintEvent event) {
    KieSession kieSession = runtimeEngine.getKieSession();

    Map<String, Object> params = new HashMap<>();
    params.put("complaintId", event.getComplaintId());    // process variable
    params.put("category", event.getPayload());            // process variable
    params.put("correlationId", event.getCorrelationId()); // process variable

    // This starts the BPMN process defined in complaint-lifecycle.bpmn2
    // Process ID must match: "com.rbi.cms.complaint-lifecycle"
    ProcessInstance processInstance = kieSession.startProcess(PROCESS_ID, params);

    return processInstance.getId();
}
```

**What happens inside the BPMN after process starts:**
```xml
<!-- complaint-lifecycle.bpmn2 -->

<!-- 1. START EVENT — process begins here -->
<startEvent id="StartEvent" name="Complaint Ingested">
    <outgoing>Flow_Start_Assignment</outgoing>
</startEvent>

<!-- 2. AUTOMATICALLY moves to Assignment Task (no waiting) -->
<sequenceFlow id="Flow_Start_Assignment" sourceRef="StartEvent" targetRef="AssignmentTask"/>

<!-- 3. BUSINESS RULE TASK — calls Drools rules automatically -->
<businessRuleTask id="AssignmentTask" name="Auto Assignment"
                  implementation="http://www.jboss.org/drools/rule"
                  tns:ruleFlowGroup="assignment">
    <!-- jBPM fires all rules in ruleFlowGroup="assignment" -->
    <!-- This is how BPMN CALLS rules — configured right here in XML -->
</businessRuleTask>

<!-- 4. AUTOMATICALLY moves to Human Task (no waiting) -->
<sequenceFlow id="Flow_Assignment_HumanTask" sourceRef="AssignmentTask" targetRef="OfficerReviewTask"/>

<!-- 5. HUMAN TASK — STOPS HERE and WAITS for officer action -->
<userTask id="OfficerReviewTask" name="Officer Review &amp; Accept">
    <potentialOwner>
        <formalExpression>#{assignedTeam}</formalExpression>
        <!-- Only officers in the assigned team can see/claim this task -->
    </potentialOwner>
</userTask>

<!-- 6. SLA TIMER attached to the human task (30 days) -->
<boundaryEvent id="SLATimerBoundary" attachedToRef="OfficerReviewTask" cancelActivity="false">
    <timerEventDefinition>
        <timeDuration>P30D</timeDuration>  <!-- ISO 8601: 30 days -->
    </timerEventDefinition>
</boundaryEvent>
```

**To change in future:**
- New workflow step → edit `.bpmn2` (add new task + sequence flow)
- Change SLA timer → edit `<timeDuration>P30D</timeDuration>` to `P15D` (15 days)
- Change which rules are called → edit `tns:ruleFlowGroup="assignment"` attribute
- New process for different complaint type → create new `.bpmn2` file + new listener

---

### t=500ms — Assignment Service: Drools Rules Fire

**What happens:** Assignment Service consumes SAME Kafka event → runs assignment rules

**Configuration:**

| File | What It Configures |
|------|-------------------|
| `cms-assignment-service/.../listener/ComplaintIngestedAssignmentListener.java` | Kafka listener — triggers assignment |
| `cms-assignment-service/.../service/AssignmentService.java` | HOW Drools is called |
| `cms-assignment-service/.../config/DroolsConfig.java` | Loads .drl files into KieContainer |
| `cms-assignment-service/src/main/resources/rules/assignment-rules.drl` | THE RULES themselves |
| `cms-rules-service` (Admin UI) | Database-managed rules (future: replaces .drl files) |

**Code flow:**
```java
// ComplaintIngestedAssignmentListener.java — THE TRIGGER
@KafkaListener(topics = KafkaTopics.COMPLAINT_INGESTED, groupId = "cms-assignment-group")
public void onComplaintIngested(String message, Acknowledgment ack) {
    ComplaintEvent event = objectMapper.readValue(message, ComplaintEvent.class);

    // Extract category and priority from the event payload
    String category = extractCategory(event.getPayload());  // "ATM"
    String priority = extractPriority(event.getPayload());  // "HIGH"

    // Call assignment service (which calls Drools)
    AssignmentFact result = assignmentService.assignComplaint(
        event.getComplaintId(), category, priority, null
    );

    // Publish result as new Kafka event
    ComplaintEvent assignedEvent = ComplaintEvent.builder()
        .complaintId(event.getComplaintId())
        .currentStatus(ComplaintStatus.ASSIGNED)
        .assignedTo(result.getAssignedTeam())        // "ATM_TEAM"
        .build();

    kafkaTemplate.send(KafkaTopics.COMPLAINT_ASSIGNED, event.getComplaintId(), payload);
    ack.acknowledge();
}

// AssignmentService.java — HOW DROOLS EXECUTES
public AssignmentFact assignComplaint(String complaintId, String category, String priority, Double amount) {
    // Build the fact object (input to rules)
    AssignmentFact fact = AssignmentFact.builder()
        .complaintId(complaintId)
        .category(category)          // "ATM"
        .priority(priority)          // "HIGH"
        .amountInvolved(amount)
        .build();

    // Create Drools session and fire rules
    KieSession kieSession = kieContainer.newKieSession();
    try {
        kieSession.insert(fact);     // Put fact into rule engine
        kieSession.fireAllRules();   // Execute all matching rules
    } finally {
        kieSession.dispose();
    }

    // fact is now modified by rules:
    // fact.assignedTeam = "ATM_TEAM"
    // fact.escalated = true
    // fact.escalationLevel = 1
    return fact;
}
```

**The rules that fire (from assignment-rules.drl):**
```drools
rule "ATM Complaints - Assign to ATM Team"
    salience 100                                    ← fires FIRST (highest priority)
    when
        $fact : AssignmentFact(category == "ATM")   ← CONDITION: category matches
    then
        $fact.setAssignedTeam("ATM_TEAM");          ← ACTION: set team
        update($fact);
end

rule "High Priority - Auto Escalate to Senior Officer"
    salience 50                                     ← fires SECOND
    when
        $fact : AssignmentFact(priority == "HIGH" || priority == "CRITICAL")
    then
        $fact.setEscalated(true);                   ← ACTION: mark escalated
        $fact.setEscalationLevel(1);
        update($fact);
end
```

**To change in future:**
- New team assignment → Create rule via Admin UI (http://localhost:4200/admin/rules)
- Change team for a category → Edit rule via Admin UI → Deploy
- New escalation threshold → Create/edit escalation rule via Admin UI
- NO code change, NO restart needed for rule changes

---

### t=600ms — Kafka Event: "complaint.assigned" Published

**What happens:** Assignment Service publishes the result

**Configuration:**

| File | What It Configures |
|------|-------------------|
| `ComplaintIngestedAssignmentListener.java` (line 48) | Publishes to `KafkaTopics.COMPLAINT_ASSIGNED` |
| `KafkaTopics.java` (line 9) | Topic name = `"complaint.assigned"` |

**Who listens to `complaint.assigned`:**
- Workflow Service (to update process instance with assignment info)
- Potentially: Notification Service (to inform the officer)

---

### t=700ms — Complaint Status Updated to ASSIGNED in DB

**What happens:** Workflow Service (or Ingestion Service) updates the complaint record

**Configuration:**

| File | What It Configures |
|------|-------------------|
| Currently: handled by the workflow process variable update | In-process |
| Future: A listener on `complaint.assigned` topic updates DB | Separate listener |

**To change in future:**
- Add more listeners to `complaint.assigned` → create new `@KafkaListener` class
- Example: Audit Service logs the assignment, Search Service indexes it

---

### t=1 day — Officer Logs In, Sees Task, Clicks "Accept"

**What happens:** Human Task in jBPM is claimed and completed by officer

**Configuration:**

| File | What It Configures |
|------|-------------------|
| `complaint-lifecycle.bpmn2` (line 47-68) | The `<userTask>` definition — who can claim it |
| `WorkflowController.java` (line 22-28) | The REST API that officer calls to transition state |
| Officer Dashboard UI (to be built) | Frontend for officers to view/manage tasks |

**How it works in jBPM (production):**
```xml
<!-- BPMN defines WHO can work on this task -->
<userTask id="OfficerReviewTask" name="Officer Review &amp; Accept">
    <potentialOwner>
        <formalExpression>#{assignedTeam}</formalExpression>
        <!-- assignedTeam = "ATM_TEAM" (set by rules earlier) -->
        <!-- Only users in group "ATM_TEAM" see this task -->
    </potentialOwner>
</userTask>
```

**Officer's action triggers workflow transition:**
```
Officer calls: POST /api/v1/workflow/{complaintId}/transition?targetStatus=IN_PROGRESS

// WorkflowController.java
@PostMapping("/{complaintId}/transition")
public ResponseEntity<ApiResponse<Void>> transitionState(
    @PathVariable String complaintId,
    @RequestParam ComplaintStatus targetStatus,    // IN_PROGRESS
    @RequestParam(required = false) String remarks) {

    workflowService.transitionState(complaintId, targetStatus, remarks);
}

// WorkflowService.java — signals the jBPM process
public void transitionState(String complaintId, ComplaintStatus targetStatus, String remarks) {
    KieSession kieSession = runtimeEngine.getKieSession();

    Map<String, Object> params = new HashMap<>();
    params.put("complaintId", complaintId);
    params.put("targetStatus", targetStatus.name());   // "IN_PROGRESS"
    params.put("remarks", remarks);

    kieSession.signalEvent("transition", params);      // ← Wakes up the process
    // The human task completes, process moves to next step
}
```

**To change in future:**
- New status/state → Add to `ComplaintStatus` enum + add step in BPMN
- Different approval flow → Modify BPMN gateway conditions
- Different task assignment → Change `<potentialOwner>` expression in BPMN

---

### t=5 days — Officer Submits Resolution

**What happens:** Officer completes investigation, submits resolution

**Configuration:**

| File | What It Configures |
|------|-------------------|
| `complaint-lifecycle.bpmn2` (line 71-75) | Gateway: checks if escalation or resolution |
| `complaint-lifecycle.bpmn2` (line 141-145) | Condition: `#{escalationReason == null}` → goes to resolution |
| `complaint-lifecycle.bpmn2` (line 108-113) | Service Task: notify customer |

**BPMN gateway logic:**
```xml
<!-- After officer submits, gateway decides the path -->
<exclusiveGateway id="InvestigationGateway" name="Investigation Outcome">

<!-- Path 1: Normal resolution (no escalation) -->
<sequenceFlow id="Flow_ToUnderReview" sourceRef="InvestigationGateway" targetRef="UnderReviewTask">
    <conditionExpression>#{escalationReason == null}</conditionExpression>
    <!-- If no escalation reason → go to Draft Resolution -->
</sequenceFlow>

<!-- Path 2: Needs escalation -->
<sequenceFlow id="Flow_ToEscalation" sourceRef="InvestigationGateway" targetRef="EscalationTask">
    <conditionExpression>#{escalationReason != null}</conditionExpression>
    <!-- If escalation reason exists → go to Escalated Review -->
</sequenceFlow>
```

**THIS is where BPMN conditions differ from Rules:**
- This condition (`#{escalationReason == null}`) is in the WORKFLOW
- It's a simple routing condition: "which path to take"
- Rules decide complex business logic: "which team, what level"
- This condition rarely changes (structural)
- Rules change frequently (business logic)

---

### t=5 days — Customer Notified

**What happens:** Service Task in BPMN automatically calls notification handler

**Configuration:**

| File | What It Configures |
|------|-------------------|
| `complaint-lifecycle.bpmn2` (line 108-113) | Service Task definition |
| `NotificationHandler.java` (to be implemented) | Actual email/SMS sending logic |

```xml
<serviceTask id="NotifyCustomerTask" name="Notify Customer of Resolution"
             implementation="java"
             operationRef="com.rbi.cms.workflow.handler.NotificationHandler">
    <!-- jBPM automatically calls this Java class -->
    <!-- No human action needed — automatic -->
</serviceTask>
```

---

### t=12 days — Auto-Close Timer Fires

**What happens:** 7-day timer expires, workflow auto-closes complaint

**Configuration:**

| File | What It Configures |
|------|-------------------|
| `complaint-lifecycle.bpmn2` (line 116-122) | Timer event: 7 days |

```xml
<!-- Timer waits 7 days after notification -->
<intermediateCatchEvent id="ClosureTimer" name="Wait for Confirmation (7 days)">
    <timerEventDefinition>
        <timeDuration>P7D</timeDuration>    <!-- ISO 8601: 7 days -->
    </timerEventDefinition>
</intermediateCatchEvent>

<!-- After timer fires → END -->
<sequenceFlow id="Flow_Timer_Close" sourceRef="ClosureTimer" targetRef="EndEvent"/>

<endEvent id="EndEvent" name="Complaint Closed">
    <!-- Process instance ends. Complaint lifecycle complete. -->
</endEvent>
```

**To change in future:**
- Change auto-close from 7 days to 14 days → edit `P7D` to `P14D` in BPMN
- Change SLA from 30 days to 15 days → edit `P30D` to `P15D` in BPMN
- Remove auto-close entirely → remove the timer event from BPMN

---

## Summary: Configuration Locations for Future Changes

### Changes That Need ONLY Rules (Admin UI — no restart):

| Change | Where to Configure |
|--------|-------------------|
| Add new team (e.g., FOREX_TEAM) | Admin UI → Create Rule → Deploy |
| Change category→team mapping | Admin UI → Edit Rule → Deploy |
| Change escalation thresholds (80%→70%) | Admin UI → Edit Escalation Rule → Deploy |
| Add high-value amount threshold | Admin UI → Create Escalation Rule → Deploy |
| Add new complaint category | Admin UI → Create Assignment Rule → Deploy |

### Changes That Need BPMN Edit (Rare — requires redeploy):

| Change | Where to Configure |
|--------|-------------------|
| Add new workflow state (e.g., VERIFICATION) | `complaint-lifecycle.bpmn2` → add userTask + sequenceFlow |
| Change SLA timer (30 days → 15 days) | `complaint-lifecycle.bpmn2` → edit `<timeDuration>` |
| Add parallel approval | `complaint-lifecycle.bpmn2` → add parallelGateway |
| Change task sequence | `complaint-lifecycle.bpmn2` → rewire sequenceFlows |
| Add automatic notification at a different step | `complaint-lifecycle.bpmn2` → add serviceTask |

### Changes That Need Code (Very rare):

| Change | Where to Configure |
|--------|-------------------|
| Add new Kafka topic | `KafkaTopics.java` + new listener class |
| Change event payload structure | `IngestionService.insertOutboxEvent()` + consumers |
| Add new service to the event chain | New `@KafkaListener` class in new service |
| Change how process variables are passed to jBPM | `WorkflowService.startComplaintWorkflow()` |

---

## Configuration Files Master Index

```
┌─────────────────────────────────────────────────────────────────────────┐
│                   WHAT CONFIGURES WHAT                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  KAFKA EVENT FLOW:                                                      │
│  ├── Topic names          → cms-common/.../KafkaTopics.java             │
│  ├── Who publishes        → IngestionService.insertOutboxEvent()        │
│  ├── Who consumes         → *Listener.java classes (@KafkaListener)     │
│  ├── Consumer groups      → application-dev-local.yml (each service)    │
│  └── Broker address       → application-dev-local.yml (bootstrap-servers)│
│                                                                         │
│  WORKFLOW (BPMN):                                                       │
│  ├── Process definition   → processes/complaint-lifecycle.bpmn2         │
│  ├── Process ID           → "com.rbi.cms.complaint-lifecycle"           │
│  ├── Timers (SLA/close)   → <timeDuration> in .bpmn2                   │
│  ├── Gateway conditions   → <conditionExpression> in .bpmn2            │
│  ├── Task ownership       → <potentialOwner> in .bpmn2                 │
│  └── Which rules to call  → tns:ruleFlowGroup attribute in .bpmn2      │
│                                                                         │
│  RULES (Drools):                                                        │
│  ├── Rule definitions     → Database (managed via Admin UI)             │
│  ├── Static rules (dev)   → src/main/resources/rules/*.drl             │
│  ├── Drools config        → DroolsConfig.java (loads rules)            │
│  ├── Fact classes         → cms-common/.../dto/AssignmentFact.java     │
│  └── Engine reload        → Rules Service /api/v1/rules/deploy         │
│                                                                         │
│  ROUTING & INFRASTRUCTURE:                                              │
│  ├── API Gateway routes   → cms-api-gateway/application.yml            │
│  ├── Service ports        → each service's application.yml             │
│  ├── CORS config          → DevLocalSecurityConfig.java                │
│  └── Database config      → application-dev-local.yml per service      │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Adding a Completely NEW Workflow (e.g., "Loan Complaint Workflow")

If in future you need a different process flow for a specific complaint type:

```
Step 1: Create new BPMN file
        → cms-workflow-service/src/main/resources/processes/loan-complaint-lifecycle.bpmn2
        → Process ID: "com.rbi.cms.loan-complaint-lifecycle"

Step 2: Modify the Kafka listener to decide which workflow to start
        → ComplaintIngestedListener.java:
          if (event.category == "LOAN") {
              workflowService.startProcess("com.rbi.cms.loan-complaint-lifecycle", params);
          } else {
              workflowService.startProcess("com.rbi.cms.complaint-lifecycle", params);
          }

Step 3: Create rules specific to loan complaints
        → Admin UI: Create new rules with category "LOAN_ASSIGNMENT"

Step 4: Deploy
        → Restart workflow service (picks up new .bpmn2)
        → Deploy rules via Admin UI (no restart)
```
