# ADR-002: Hit Policy and Null Semantics

**Status:** Accepted  
**Date:** 2026-08-17  
**Decision Makers:** Architecture team  

## Context

A decision table needs clear semantics for:
1. What happens when multiple rules match (hit policy)
2. How null/missing context values interact with conditions

Ambiguity in either area leads to silent misrouting.

## Decision — Hit Policies

Four hit policies, configured per ruleset:

| Policy | Behaviour |
|--------|-----------|
| `FIRST` (default) | Evaluate in ascending `(priority, rowOrder)`, return first match |
| `PRIORITY_SPECIFICITY` | Among all matches, pick lowest priority; break ties by specificity (fewest wildcards), then rowOrder |
| `UNIQUE` | Exactly one rule may match; >1 is a runtime error → falls to default + raises alert |
| `COLLECT` | Return all matching outcomes (for broadcast/multi-assign) |

Determinism guarantee: given the same context and ruleset version, the result is always the same.

## Decision — Null Semantics

| Scenario | Result | Rationale |
|----------|--------|-----------|
| Context value null + condition present (except IS_NULL/NOT_IN/IS_NOT_NULL) | Condition is **false** | Safest default; avoids accidental matches |
| Context value null + wildcard (no condition) | **Match** | Wildcards mean "don't care" |
| Context value null + `IS_NULL` | **true** | Direct semantic |
| Context value null + `NOT_IN(set)` | **true** | A null is not a member of any set |
| Context value null + `IS_NOT_NULL` | **false** | Direct semantic |

## Decision — Range Semantics

`BETWEEN(lo, hi)` is **inclusive lower, exclusive upper: [lo, hi)**.

Rationale: consecutive monetary bands (0–500000, 500000–2500000, 2500000+) must neither gap nor overlap at boundaries. Half-open intervals guarantee this by construction.

The grid renders this explicitly as `[lo, hi)` and the validator uses these semantics for gap analysis.

## Decision — Numeric Comparison

All numeric comparisons use `BigDecimal.compareTo()`. Never `equals()` (which checks scale), never `double` (which loses precision). Money values in Indian banking context routinely have paisa precision.

## Decision — String Comparison

Default: case-insensitive, trimmed. The attribute registry can flag an attribute as `caseSensitive = true` for exact matching.

## Consequences

- The `NOT_IN` on null → true behaviour is counter-intuitive. The UI must show a tooltip explaining it.
- `[lo, hi)` semantics must be consistently enforced in validation, rendering, and documentation.
- The `UNIQUE` hit policy adds validation complexity (overlap detection) but prevents silent non-determinism.
