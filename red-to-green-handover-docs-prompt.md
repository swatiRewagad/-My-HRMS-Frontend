# Red-to-Green Handover Documentation Prompt (for Claude Code)

*Run this after the feature-build work is complete, tested, and signed off (Phase 13 of the companion build prompt) — not concurrently with it. Its output is the only knowledge transfer that happens before the code moves to the human-maintained, agent-free production network.*

## Context for the agent
This codebase is about to move from an environment where an AI coding agent had access, to a production network where humans maintain it with **no further agent access**, per corporate policy. Write every document here as if you will never be asked a follow-up question — because you won't be.

## Prime directives
1. **Every file path, class/component name, command, and code snippet must be real and verified against this repo** — not generic Spring Boot/Angular boilerplate, not remembered from training. Read the actual file before citing it. If a doc tells someone to run a command, run that command yourself first and confirm the result.
2. **Prioritize what's actually new.** This system existed before the recent feature work, and most of its pre-existing functionality already has institutional knowledge behind it. Put your depth into the areas that were built or materially changed recently — OTP/CAPTCHA/cooloff, PII encryption, the DB-driven i18n platform, asset caching, mobile responsiveness, the geo-notice, the TAT timer, the officer dashboard, the similar-cases widget, and any WCAG remediation — because that's where zero human tacit knowledge currently exists. Give pre-existing areas a lighter-touch orientation only.
3. **Mine what already exists before writing from scratch.** If `AUDIT.md`, `PLAN.md`, `DECISIONS.md`, or `VERIFICATION.md` (or equivalents) exist in the repo from the feature-build work, read them first — they already capture what changed, why, and what judgment calls were made. Verify they're still accurate against the current code rather than trusting them blindly.
4. **Write for zero follow-up.** No "ask the AI," no "check with the previous developer" — assume the reader has only this document, the code, and normal engineering tools.
5. **Never put a secret, key, credential, or connection string in these docs, even as an illustration.** Describe the pattern and where the value lives, never the value itself.
6. **Don't invent who to contact.** Every role file ends with an "If this doesn't cover it" line — leave it as a clearly marked `TODO: escalation contact` rather than guessing a name or team you have no way of actually knowing.

## Where this lives
Create `/docs/handover/` in the repo (or match whatever docs convention already exists — check first). One file per role below, plus two shared files every role file links into rather than re-explaining independently:
- `00-system-overview.md` — the concepts that cut across roles: the workflow engine, the session-key encryption model, the DB-driven i18n architecture, the config mechanism, plus a short glossary (TAT, OTP, WCAG, ELK, etc.) for anyone new to the domain.
- `00-file-map.md` — an actual directory-tree excerpt of the repo, annotated, focused on the recently-changed areas.

Add a one-line header to every file recording the commit hash/date it was last verified against — these docs will have no agent maintaining them, and a future reader should be able to tell at a glance how stale they might be.

**Before writing full content: present a table of contents for all nine files and stop for approval.** Discovering the structure is wrong after several thousand lines of content is an expensive way to find out.

**Suggested order once approved:** overview + file map first → backend and security (highest-risk new surface, and the other docs will reference their explanations) → frontend → DevOps → performance tuning and performance testers → other testers (benefits from the dev docs already being real).

---

## The shared template — every role file follows this shape

1. **Why this matters to you** — a few role-specific sentences, not generic.
2. **System map** — the actual files/packages/components relevant to this role's slice of the recent work, as a real file-tree excerpt.
3. **Trace one flow end to end** — pick one representative request/flow for this role and walk it through every file it touches, in order, with real file and method/class names. This is the single most important section for building real understanding — don't generalize it away.
4. **Worked examples** — from the required list below, the ones relevant to this role, each with exact file paths, a concrete diff or step sequence, and a self-verification step ("run X, you should see Y / this test should now pass").
5. **Danger zones** — things that look simple to change but have hidden coupling or risk (a config value read by three services; a table that looks safe to query directly but bypasses a cache). Specific and real, not generic caution.
6. **What and how to test** — which suite(s) cover this area, the exact command to run them, what a good new test here should cover.
7. **Sample scenarios** — 2–4 realistic tickets this role might actually receive. Point toward where to start looking, not a full solution — the goal is practiced investigation, not a script to copy.
8. **Practice checklist** — 3–5 small hands-on tasks, increasing difficulty, each with a way to self-verify success (a test that passes, a UI change that's visible, a log line that appears).

---

## Roles and what their documentation should emphasize

**Frontend developers** — Angular structure for the new work: the OTP/login components, the language switcher and how a component pulls a label from the DB-backed i18n system (show the real pattern — never hardcode English), the dashboard widget structure, the similar-cases widget, Urdu RTL handling, and the accessibility conventions actually used (focus management, ARIA, `prefers-reduced-motion`).

**Backend developers** — Spring Boot structure for the new services: OTP/cooloff/rate-limiting logic, the TAT calculation service, similar-cases scoring, the PII encryption interceptor and exactly how to mark a new field as PII correctly if one is added later, the DB-backed `MessageSource`, and migration conventions (tool, location, how to write one safely).

**DevOps** — the build/deploy pipeline changes (asset hashing, compression, cache headers), where every new configurable value lives and how to change each one safely — cooloff durations, dashboard refresh interval, TAT thresholds, similarity threshold, cache TTLs — spelling out hot-reloadable vs. requires-redeploy per value, any new infrastructure introduced (e.g. Elasticsearch, if that path was taken) including backup/monitoring basics, how migrations run in this pipeline, and the rollback procedure.

**Security** — how OTP/CAPTCHA/cooloff actually works and how to tune thresholds safely, how the per-session PII encryption key is derived and what to check if decryption is failing, where security-relevant audit logs live and what normal vs. suspicious looks like, the anti-automation heuristics and their false-positive/negative tradeoffs, how to re-run the adversarial test suite from the feature-build work (bypass/tamper/replay attempts) and when that re-run should happen (any change touching auth, encryption, or rate-limiting), and a short incident-response note — what to check first if a session-key compromise or an unusual OTP-failure spike is suspected.

**Performance tuning** — where the caches are and how they behave (translation cache, dashboard response cache), including invalidation across multiple instances; where the N+1/index risk areas are in the new tables; how to adjust a cache TTL or a query without breaking the dashboard's "no manual refresh" guarantee.

**Performance testers** — how to run the load/soak tests produced during the feature-build work (exact command/tooling, whatever was actually used), the SLA targets to validate against (dashboard cache holding under concurrent load, OTP/rate-limit behavior under burst load, similar-cases query latency), and the baseline figures to compare new results against if they were captured.

**Other testers (functional/QA)** — end-to-end citizen and officer journeys including edge cases (OTP failure → cooloff → email fallback; complaint submission across languages including Urdu RTL; missing-translation fallback), basic accessibility verification (keyboard-only walkthrough, screen-reader spot check) if there's no separate accessibility owner, and how to extend the existing automated suite following its actual conventions rather than inventing a new one.

---

## Required worked-example recipes
Produce all of these; each shows up in every role file it's relevant to, written from that role's angle, describing the same underlying change consistently across files:

1. **Add a new field to the citizen complaint form** — frontend (form + validation + i18n key), backend (DTO/entity + migration), security (decide and document whether it needs PII-marking for encryption), testers (new validation cases).
2. **Change the TAT calculation or a warning threshold** — backend, frontend (timer display), testers (verify the new threshold triggers correctly), performance tuning (cost of recalculating averages).
3. **Add a new widget to the officer dashboard** — frontend (component + slot), backend (endpoint if needed), performance tuning (respecting the server-side refresh cache), testers (widget correctness and refresh behavior).
4. **Change a security setting** (cooloff duration, CAPTCHA difficulty, a rate-limit threshold) — security, DevOps (how the change is deployed), testers (re-run the relevant security tests at the new threshold).
5. **Add a language, or correct a translation** — frontend/backend (confirm zero code deploy is really needed), DevOps (cache invalidation across instances), testers (fallback and coverage check).
6. **Adjust the similar-cases matching threshold or the fields it scores on** — backend, performance tuning, testers.
7. **Troubleshoot the PII encryption path** (a decryption failure, a stuck session key) — security, backend, DevOps. A runbook-style "what do I check first," not a feature change.

---

## Optional, clearly flagged as extra scope: a practice branch
If time allows, create a `practice/onboarding` branch with small, intentional gaps — a TODO-stubbed "add a field" exercise, a disabled test that should pass once a small fix is made — so the practice checklists have somewhere real to be attempted without risk to the actual codebase. Present this as a separate deliverable in your plan rather than folding it silently into the core docs; it's valuable but meaningfully more work, and Gaurav should decide whether it's worth the time before you build it.

## Definition of done
- Every file path, command, and code reference in every document has been checked against the real repo, not written from memory of a typical stack.
- Someone with no prior exposure to this codebase and no further agent access could complete each role's practice checklist using only that document and the code.
- The table of contents was approved before full content was written.
- Nothing in the docs assumes continued AI access, and no secret/credential value appears anywhere in them.
