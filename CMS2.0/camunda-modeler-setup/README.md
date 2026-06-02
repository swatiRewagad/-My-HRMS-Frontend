# Camunda Modeler Setup for CMS 2.0

## Quick Start

### 1. Download Camunda Modeler
- Go to: https://camunda.com/download/modeler/
- Download for Windows (latest version)
- Extract and run `Camunda Modeler.exe`

### 2. Open Existing Workflow
- File → Open → Navigate to:
  ```
  CMS2.0/cms-workflow-service/src/main/resources/processes/complaint-lifecycle.bpmn2
  ```

### 3. Edit Visually
- Drag & drop to add tasks, gateways, timers
- Click elements to configure properties
- Use the Properties Panel (right side) for detailed configuration

### 4. Save and Deploy
- File → Save (overwrites the .bpmn2 file)
- Restart workflow service to pick up changes
- Future: hot-deploy via REST API (Phase 2)

---

## Project Structure

```
cms-workflow-service/
└── src/main/resources/
    └── processes/
        ├── complaint-lifecycle.bpmn2      ← Main workflow (edit this)
        └── [future workflows go here]
```

## Workflow Process Variables

These variables are available in the BPMN process:

| Variable | Type | Set By | Used In |
|----------|------|--------|---------|
| complaintId | String | Kafka listener | All tasks |
| category | String | Kafka listener | Assignment rules |
| priority | String | Kafka listener | Assignment rules |
| assignedTeam | String | Drools rules | Officer task ownership |
| assignedOfficer | String | Manual assignment | Task routing |
| resolutionSummary | String | Officer input | Notification |
| escalationReason | String | Officer input | Gateway condition |

## BPMN Element Types Used

| Element | BPMN Type | Purpose |
|---------|-----------|---------|
| Start | startEvent | Process begins (triggered by Kafka) |
| Assignment | businessRuleTask | Calls Drools rules (ruleFlowGroup) |
| Officer Review | userTask | Waits for human action |
| Investigation Gateway | exclusiveGateway | Routes based on condition |
| Draft Resolution | userTask | Officer writes resolution |
| Escalated Review | userTask | Senior officer handles |
| Notify Customer | serviceTask | Calls Java handler |
| Closure Timer | intermediateCatchEvent (timer) | Waits 7 days |
| SLA Timer | boundaryEvent (timer) | 30-day SLA on review |
| End | endEvent | Process instance ends |

## Tips for Camunda Modeler

1. **Process ID** must match what the code expects: `com.rbi.cms.complaint-lifecycle`
2. **ruleFlowGroup** on businessRuleTask must match Drools rule groups
3. **Timer durations** use ISO 8601: P30D = 30 days, PT2H = 2 hours
4. **potentialOwner** expressions reference process variables: `#{assignedTeam}`
5. **Gateway conditions** use MVEL expressions: `#{escalationReason == null}`
