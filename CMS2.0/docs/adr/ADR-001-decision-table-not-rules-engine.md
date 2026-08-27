# ADR-001: Decision Table, Not a Rules Engine

**Status:** Accepted  
**Date:** 2026-08-17  
**Decision Makers:** Architecture team  

## Context

The CMS assignment logic currently uses Drools (KIE) with DRL files stored in the database. Business users cannot modify routing rules without developer intervention — every policy change requires writing Drools syntax, testing compilation, and deploying.

We evaluated three approaches:
1. **Keep Drools** with a UI abstraction that generates DRL
2. **General expression language** (SpEL, MVEL, or JEXL) with a rule-builder UI
3. **Hand-written decision-table interpreter** with an Excel-like grid

## Decision

We chose option 3: a custom, deterministic decision-table evaluator with no expression language dependency.

## Rationale

| Criteria | Drools | Expression Language | Decision Table |
|----------|--------|--------------------:|----------------|
| Business-user editable | No (DRL syntax) | Partial (still needs operators) | Yes (grid cells) |
| Auditable/readable | Poor (Rete network) | Medium | Full (table = spec) |
| Dependency weight | Heavy (20+ JARs) | Medium | Zero external |
| Determinism | Complex (salience + agenda) | Depends on eval order | Explicit priority + hit policy |
| Security surface | DRL injection risk | Expression injection risk | No eval of user strings |
| Performance predictability | Rete warmup, memory | Reflection overhead | Linear scan or indexed lookup |

Key factors:
- **Auditability**: RBI regulatory context demands that every assignment decision is traceable to a specific rule row. Drools' Rete algorithm makes this opaque.
- **Security**: No user-supplied string ever reaches an evaluator. The operator catalogue is closed.
- **Simplicity**: An auditor can read `RuleEvaluator` and understand it in ten minutes.

## Consequences

- We must implement and maintain the evaluator ourselves (~500 lines of core logic).
- New operators require a code change (but new dimensions do not).
- We lose Drools' temporal reasoning and backward chaining — acceptable since assignment is a pure function of current attributes.
- The existing `cms-rules-service` (eligibility/routing DRL) remains untouched.

## Risks

- If rule complexity grows beyond flat conditions (e.g., cross-attribute comparisons), we'll need derived attributes computed by the caller, not expressions in the table. This is a deliberate constraint, not a limitation.
