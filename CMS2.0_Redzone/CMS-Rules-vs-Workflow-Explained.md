# Rules vs. Workflow — How They Work Together

## Your Questions Answered

---

## 1. When Does Workflow Come Into Picture?

```
┌─────────────────────────────────────────────────────────────────────┐
│                    THE COMPLETE JOURNEY                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Customer submits complaint                                         │
│       │                                                             │
│       ▼                                                             │
│  ┌─────────────────────────────┐                                   │
│  │  RULES: Eligibility Check   │  ← Rules decide: CAN this         │
│  │  (Before workflow starts)    │     complaint be filed?            │
│  └─────────────────────────────┘                                   │
│       │                                                             │
│       │ If eligible                                                  │
│       ▼                                                             │
│  ═══════════════════════════════════════════════════════════════     │
│  ║  WORKFLOW STARTS HERE                                       ║    │
│  ║  (Complaint registered → Process instance created)          ║    │
│  ║                                                             ║    │
│  ║  Workflow is the ORCHESTRATOR — it decides:                 ║    │
│  ║  • What step comes NEXT                                     ║    │
│  ║  • Who needs to ACT                                         ║    │
│  ║  • How long to WAIT                                         ║    │
│  ║  • When to ESCALATE (timer fires)                           ║    │
│  ║  • When the complaint is DONE                               ║    │
│  ║                                                             ║    │
│  ║  At CERTAIN steps, workflow CALLS rules for decisions:      ║    │
│  ║  • "Which team should handle this?" → Assignment Rules      ║    │
│  ║  • "Has SLA breached?" → Escalation Rules                  ║    │
│  ║                                                             ║    │
│  ═══════════════════════════════════════════════════════════════     │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Answer:** Workflow starts the moment a complaint is registered. It lives for the ENTIRE lifecycle of that complaint (days/weeks/months) until it's closed.

---

## 2. Rules and Workflow — Together or Independent?

**They work TOGETHER. Workflow is the conductor, Rules are the musicians.**

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                     │
│  Think of it like a RAILWAY SYSTEM:                                 │
│                                                                     │
│  WORKFLOW = The railway TRACK                                       │
│  • Fixed structure (stations in order)                              │
│  • Defines the path the train takes                                 │
│  • Controls signals (wait, go, switch track)                        │
│  • Rarely changes (building new track is expensive)                 │
│                                                                     │
│  RULES = The SIGNALS/SWITCHES at junctions                          │
│  • Decide WHICH platform to send the train to                       │
│  • Can be changed quickly (flip a switch)                           │
│  • Different rules for different trains                             │
│  • Changed frequently without rebuilding track                     │
│                                                                     │
│  Neither works alone:                                               │
│  • Track without signals = train goes one fixed path always         │
│  • Signals without track = decisions but no structured journey      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Detailed: What Workflow Does vs. What Rules Do

### Step-by-Step Breakdown

```
STEP 1: Complaint Registered
━━━━━━━━━━━━━━━━━━━━━━━━━━━━

WORKFLOW does:
  • Creates a process instance (long-running, tracks this complaint)
  • Moves to "Assignment" step
  • CALLS the assignment rules

RULES do:
  • Given category=ATM, priority=HIGH
  • Return: team=ATM_TEAM, escalated=true

WORKFLOW then:
  • Records the assignment result
  • Publishes "complaint.assigned" event
  • Moves to "Officer Review" step
  • Starts 30-day SLA TIMER


STEP 2: Waiting for Officer Action
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

WORKFLOW does:
  • WAITS (this is a Human Task — paused until officer acts)
  • Monitors the SLA timer (counting down 30 days)
  • Status: ASSIGNED → IN_PROGRESS (when officer accepts)

RULES do:
  • Nothing at this point (waiting for human action)

INPUTS that move workflow forward:
  • Officer clicks "Accept" in their dashboard
  • OR SLA timer fires (30 days elapsed)


STEP 3: Officer Investigating
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

WORKFLOW does:
  • Status: IN_PROGRESS
  • WAITS for officer to submit findings
  • Still monitoring SLA timer

INPUTS that move workflow forward:
  • Officer submits resolution → goes to UNDER_REVIEW
  • Officer marks "needs escalation" → goes to ESCALATED
  • SLA timer fires → WORKFLOW auto-escalates (calls escalation rules)

When SLA timer fires, WORKFLOW calls RULES:
  • "SLA is at 80%, what action?" → Rules say: NOTIFY_OFFICER
  • "SLA is at 100%, what action?" → Rules say: REASSIGN_SUPERVISOR
  • "SLA is at 150%, what action?" → Rules say: ESCALATE_MANAGEMENT


STEP 4: Resolution
━━━━━━━━━━━━━━━━━━

WORKFLOW does:
  • Status: RESOLVED
  • Calls NotificationHandler (sends email/SMS to customer)
  • Starts 7-day auto-close timer

RULES do:
  • Nothing (notification is a workflow service task)

INPUTS that move workflow forward:
  • Customer confirms satisfaction → CLOSED
  • 7-day timer fires → auto-CLOSED


STEP 5: Closed
━━━━━━━━━━━━━━

WORKFLOW does:
  • Status: CLOSED
  • Process instance ENDS (complaint lifecycle complete)
  • Publishes "complaint.closed" event

RULES do:
  • Nothing
```

---

## 4. Summary: Who Decides What

| Decision | Who Decides | Why |
|----------|-------------|-----|
| Is complaint eligible to file? | **RULES** | Business logic that changes per regulation |
| What is the FIRST step after registration? | **WORKFLOW** | Fixed: always goes to Assignment |
| WHICH team handles it? | **RULES** | Category→Team mapping changes often |
| What is the NEXT step after assignment? | **WORKFLOW** | Fixed: always goes to Officer Review |
| HOW LONG to wait before escalation? | **WORKFLOW** | Timer defined in BPMN (30 days) |
| WHAT ACTION to take on escalation? | **RULES** | Notify? Reassign? Escalate to mgmt? |
| Can officer resolve OR must escalate? | **WORKFLOW** | Gateway condition in BPMN |
| WHO gets notified? | **WORKFLOW** | Service task calls notification handler |
| When is complaint auto-closed? | **WORKFLOW** | Timer: 7 days after resolution |

---

## 5. What Are the INPUTS That Move Workflow Between Stages?

| From | To | Input / Trigger |
|------|----|-----------------|
| — | NEW | Customer submits complaint form |
| NEW | ASSIGNED | Automatic (rules assign team immediately) |
| ASSIGNED | IN_PROGRESS | **Human action:** Officer clicks "Accept" |
| IN_PROGRESS | UNDER_REVIEW | **Human action:** Officer submits investigation findings |
| IN_PROGRESS | ESCALATED | **Timer:** SLA breaches OR **Human action:** Officer escalates manually |
| ESCALATED | UNDER_REVIEW | **Human action:** Senior officer resolves |
| UNDER_REVIEW | RESOLVED | **Human action:** Resolution approved |
| RESOLVED | CLOSED | **Timer:** 7 days auto-close OR **Human action:** Customer confirms |

```
Triggers are of 3 types:

1. AUTOMATIC (immediate, no waiting)
   • NEW → ASSIGNED (rules fire instantly)

2. HUMAN ACTION (waits for someone to do something)
   • Officer accepts task
   • Officer submits resolution
   • Manager approves escalation

3. TIMER (waits for time to pass)
   • 30-day SLA breach → escalation
   • 7-day auto-close after resolution
```

---

## 6. Visual: How Rules + Workflow Interact

```
                    WORKFLOW (The Orchestrator)
    ┌───────────────────────────────────────────────────────┐
    │                                                       │
    │   [START]                                             │
    │      │                                               │
    │      ▼                                               │
    │   ┌──────────────┐     ┌─────────────────────┐      │
    │   │ Assignment   │────►│  RULES ENGINE        │      │
    │   │ (call rules) │◄────│  Input: category,    │      │
    │   └──────────────┘     │         priority     │      │
    │      │                 │  Output: team,       │      │
    │      │                 │          escalated   │      │
    │      │                 └─────────────────────┘      │
    │      ▼                                               │
    │   ┌──────────────┐                                   │
    │   │Officer Review│  ← WAITS for human action         │
    │   │ (human task) │    (days/weeks)                   │
    │   └──────────────┘                                   │
    │      │         │                                     │
    │      │         │ [SLA TIMER: 30 days]                │
    │      │         ▼                                     │
    │      │    ┌──────────────┐     ┌───────────────┐    │
    │      │    │  Escalation  │────►│ RULES ENGINE   │    │
    │      │    │  (call rules)│◄────│ Input: SLA%,   │    │
    │      │    └──────────────┘     │        amount  │    │
    │      │                         │ Output: action,│    │
    │      ▼                         │         level  │    │
    │   ┌──────────────┐             └───────────────┘    │
    │   │  Resolution  │  ← WAITS for human action         │
    │   │ (human task) │                                   │
    │   └──────────────┘                                   │
    │      │                                               │
    │      ▼                                               │
    │   ┌──────────────┐                                   │
    │   │   Notify     │  ← Automatic (service task)       │
    │   │  Customer    │                                   │
    │   └──────────────┘                                   │
    │      │                                               │
    │      │ [TIMER: 7 days]                               │
    │      ▼                                               │
    │   [END: CLOSED]                                      │
    │                                                       │
    └───────────────────────────────────────────────────────┘
```

---

## 7. Why Do We Need BOTH?

**Without Workflow (only Rules):**
- Rules can decide "assign to ATM_TEAM"
- But WHO tracks that the officer hasn't responded for 30 days?
- WHO sends the reminder? WHO auto-escalates?
- WHO ensures Step 3 happens only after Step 2 is complete?
- → You'd have to build all this tracking/timer/sequencing logic in custom code

**Without Rules (only Workflow):**
- Workflow knows the steps: Assign → Review → Resolve → Close
- But it would need hardcoded logic: `if (category == "ATM") team = "ATM_TEAM"`
- Every time a new category is added → edit BPMN → redeploy
- Every time SLA threshold changes → edit BPMN → redeploy
- → Workflow becomes bloated and fragile

**With BOTH:**
- Workflow handles: sequencing, timers, waiting, orchestration
- Rules handle: decisions that change frequently
- Workflow CALLS rules when it needs a decision
- Rules return the answer, workflow continues on its path
- → Clean separation, each changes independently

---

## 8. One-Line Summary

> **Workflow = WHEN and in WHAT ORDER things happen**
> **Rules = WHAT DECISION to make at each point**
