# RE Activity Status Ladder — Acceptance Criteria (UST846–UST852)

Source stories: RE module backlog `RE-US-015`, `RE-US-016`, `RE-US-017`, `RE-US-029`
(`Archive/RE_CMS2.0_Traceable_Backlog.csv`). Traces to CEPC BRD **Gap 04**, FR-RE-026, FR-RE-027.

The business problem: `COMPLAINTS.status` says where a complaint sits in the regulatory process, but
nothing said whether the entity had *started work*. Staff resolved that by telephoning the bank.

---

## UST846 — The ladder is derived from RE actions

**Given** a complaint has been forwarded to a regulated entity and no RE user has opened it
**When** a CEPC or RBI user views the record
**Then** the activity status reads `NOT_OPENED`
**And** no backfill was required for records that predate this feature — a null column reads as
`NOT_OPENED`.

**Given** a complaint at `NOT_OPENED`
**When** an RE user fetches the complaint detail via `GET /api/v1/re-portal/complaints/{n}`
**Then** the status becomes `OPENED`
**And** a `COMPLAINT_TIMELINE` row is written with action `RE_ACTIVITY_OPENED` and
`event_source = MANUAL`.

**Given** a complaint at `OPENED`
**When** the RE user saves a draft with **no** response text
**Then** the status becomes `UNDER_REVIEW` — the entity is looking, not yet writing.

**Given** a complaint at `OPENED` or `UNDER_REVIEW`
**When** the RE user saves a draft **containing** response text
**Then** the status becomes `RESPONSE_BEING_PREPARED`.

**Given** an RE user submits a response with attachments
**When** the files are successfully stored
**Then** `DOCUMENTS_UPLOADED` is recorded in history
**And** the current status becomes `RESPONSE_SUBMITTED`, because it outranks it.
**And** if file storage fails, the ladder does **not** advance.

**Given** a complaint at `RESPONSE_BEING_PREPARED`
**When** an RE user re-opens the detail page (an `OPENED` signal)
**Then** the status does **not** regress to `OPENED`
**And** no duplicate timeline row is written — the badge must not appear to go backwards to staff,
and page views must not flood the history.

**Given** an RE response is submitted inside a transaction that later rolls back
**When** the transaction fails
**Then** the status does **not** remain `RESPONSE_SUBMITTED` — the flip shares the caller's
transaction, so a rolled-back submission cannot report the entity as having answered.

---

## UST847 — RE and RBI render the status identically

**Given** the four portals previously used four different rendering conventions
**When** any portal displays a status
**Then** it uses the shared `app-status-badge` component
**And** the value is bound as `[attr.data-status]` in UPPERCASE
**And** a value arriving as `re_responded` and one arriving as `RE_RESPONDED` render identically.

**Given** the RE detail page previously bound `[class]="'status-' + status"`
**When** the badge renders
**Then** the base `.status-badge` class survives — the old `[class]` binding replaced the entire
class attribute, destroying the padding and border-radius at runtime.

**Given** the server writes `re_responded` while the old stylesheet expected `responded`
**When** an RE responds
**Then** the badge is styled, not blank, and the label reads "Entity Responded", not "Re_responded".

**Given** a user has selected any of the ten supported locales
(`en, hi, bn, mr, te, ta, gu, ur, kn, ml`)
**When** a status badge renders
**Then** the label resolves from a DB-seeded translation key
**And** the domain wording from the deleted per-component maps is preserved — `in_progress` still
reads "Under Examination", `forwarded` still reads "Forwarded to Dept"
**And** an unseeded key falls back to a humanised value rather than displaying the raw key.

---

## UST848 — History distinguishes automatic from manual

**Given** `COMPLAINT_TIMELINE` previously recorded the actor as `"SYSTEM"`, `"System"` and
`"system"` depending on which service wrote the row
**When** any timeline entry is created
**Then** it carries an explicit `event_source` of `AUTOMATIC` or `MANUAL`.

**Given** existing timeline rows written before this column existed
**When** the migration runs
**Then** rows whose actor is `SYSTEM`, `SCHEDULER`, `AUTO` or `AUTOMATED` become `AUTOMATIC`
**And** all other rows become `MANUAL`
**And** rows written by `RE_PORTAL` stay `MANUAL`, because that literal marks queries an RE user
actually raised — labelling them `AUTOMATIC` would misattribute real entity work as machine output.
*(Applied result on `cms_db`: 86 `AUTOMATIC`, 697 `MANUAL`.)*

**Given** staff are reading a record's progression
**When** they request the complaint detail
**Then** `reActivityHistory` contains only the ladder movements, each with from/to status, actor and
event source.

---

## UST849 — Overdue flip and escalation fire from ONE trigger

**Given** a complaint's RE response window has expired and the entity has not submitted
**When** the scheduled sweep runs
**Then** the status becomes `OVERDUE` **and** the owning officer is notified, in a single
transaction — a record can never be marked overdue without the escalation being raised, or vice
versa.

**Given** the window expired but the entity **did** submit a response
**When** the sweep runs
**Then** the record is left at `RESPONSE_SUBMITTED` and **no** escalation is sent.

**Given** a record is already `OVERDUE`
**When** the sweep runs again
**Then** no second notification is sent and no duplicate timeline row is written.
*(The equivalent code in `cms-sla-monitor-service` has no idempotency marker and re-publishes on
every tick; this sweep does not.)*

**Given** an entity responds after being marked `OVERDUE`
**When** the response is submitted
**Then** the ladder may advance to `RESPONSE_SUBMITTED` — a late response still counts.

**Given** `cms.re.activity.overdue_escalation_enabled` is `false`
**When** the sweep runs
**Then** nothing is flipped and no query is issued.

> **Design note.** The trigger lives in `cms-backend`, not `cms-sla-monitor-service`. That service
> queries `COMPLAINT_MASTER` (owned by cms-ingestion, uppercase `ComplaintStatus` enum); the ladder
> lives on `COMPLAINTS` (owned by cms-backend). The monitor cannot see these rows at all, holds only
> a raw `EntityManager` and a `KafkaTemplate`, and has no transaction — so "one trigger" is only
> achievable here.

---

## UST850 — The nudge threshold is snapshotted

**Given** a record enters an early activity status
**When** the transition is recorded
**Then** the nudge threshold in force at that moment is written onto the record.

**Given** a record entered `OPENED` when the threshold was 30 days and is 5 days old
**When** an administrator later lowers the live threshold to 1 day
**Then** the record is **not** nudged — the snapshot governs, so a config change cannot
retroactively re-judge history, nor silently forgive records already past due.

**Given** a record has sat in a nudgeable status beyond its snapshotted threshold
**When** the nudge sweep runs
**Then** the owning officer is notified once
**And** the record is stamped so the next run stays silent
**And** the stamp clears on the next transition, so the record becomes nudgeable again at the next
level.

**Given** a record has no snapshot (it never transitioned through the service)
**When** the sweep runs
**Then** it is skipped rather than judged against a guessed threshold.

**Given** a record has reached `DOCUMENTS_UPLOADED`, `RESPONSE_SUBMITTED` or `OVERDUE`
**When** the sweep runs
**Then** it is not nudged — once documents are in, silence is not a problem, and nudging an
already-`OVERDUE` record would duplicate the SLA escalation that this story says nudges must not
replace.

---

## UST851 — Maker-checker on threshold changes

**Given** an administrator proposes a new nudge threshold
**When** the request is submitted
**Then** it is staged as `PENDING` and the live value is **unchanged**.

**Given** a pending request raised by `admin_a`
**When** `admin_a` (in any letter casing) attempts to approve it
**Then** it is refused as a Maker-Checker violation — Keycloak usernames are case-insensitive, so a
case variant is the same person.

**Given** a pending request raised by `admin_a`
**When** a different administrator approves it
**Then** the value takes effect, `CONFIG_AUDIT_LOG` records the change with both identities, and
records already in a status keep their snapshotted threshold.

**Given** a pending request whose live value was changed by someone else in the meantime
**When** an approver approves it
**Then** it is refused — the approver would not be approving what they think they are.

**Given** an administrator wants to cancel their own request
**When** they reject it
**Then** it is recorded as `WITHDRAWN`, not `REJECTED` — conflating the two would overstate the
review that took place.

**Given** a proposed threshold outside 1–365 days, a non-numeric value, or an unknown config key
**When** the request is submitted
**Then** it is refused with a message naming the constraint.

> ⚠ **Known limitation.** `SecurityConfig` is `anyRequest().permitAll()` and the role-guard aspects
> proceed when no roles are present, so a caller can present an arbitrary second identity and
> self-approve. The separation-of-duties logic is real and tested, but it only becomes a genuine
> control once authentication is enforced. Do not represent this as an effective control today.

---

## UST852 — Read-only, and staff-only

**Given** an RE user attempts `PUT /api/v1/re-portal/complaints/{n}/activity-status`
**When** the request is made
**Then** it is refused with **403** and message key `re.activity.manual_set_forbidden`
**And** the stored status is unchanged.
*(403 rather than 404 deliberately: an integration that guesses the URL gets a documented refusal
instead of a not-found that invites trying another path.)*

**Given** a complainant tracks their own complaint publicly
**When** the tracker returns the detail
**Then** `reActivity` and `reActivityHistory` are **absent** — telling a citizen their bank "has not
opened" the record invites a conversation RBI has not agreed to have, and it is not their data.

**Given** a caller presents only a bearer token with no resolvable identity
**When** they request the detail
**Then** the activity status is still withheld.

**Given** a CEPC or RBI staff caller with a resolved non-RE identity
**When** they request the complaint detail
**Then** they receive the status, its label key, the timestamp of last change, the snapshotted nudge
threshold, the ladder history, and `readOnly: true`
**And** there is no setter on the staff side at all — the ladder is derived from RE actions only.

---

## Configuration (no hardcoded values)

All read from `SYSTEM_CONFIG`, seeded by MySQL `V18` / Oracle `V17`:

| Key | Default | Purpose |
|---|---|---|
| `cms.re.activity.nudge_days.default` | 3 | Fallback threshold |
| `cms.re.activity.nudge_days.not_opened` | 2 | Unopened records |
| `cms.re.activity.nudge_days.opened` | 3 | Opened, no progress |
| `cms.re.activity.nudge_days.under_review` | 3 | Under review |
| `cms.re.activity.nudge_days.response_being_prepared` | 5 | Drafting |
| `cms.re.activity.nudge_enabled` | true | Nudge kill switch |
| `cms.re.activity.overdue_escalation_enabled` | true | Overdue kill switch |
| `cms.re.activity.sweep_batch_size` | 500 | Records per sweep |

Cron expressions are properties, not compiled in: `cms.re.activity.overdue-cron`
(default every 15 min), `cms.re.activity.nudge-cron` (default 09:00 daily).

Thresholds are per-status rather than one global number because "opened but untouched for 3 days"
and "documents uploaded but not submitted for 3 days" are not equally concerning.

---

## Verification

- **53 JUnit** tests pass (`ReActivitySweepServiceTest` 15, `ReActivityConfigServiceTest` 13,
  `RePortalServiceTest` 25).
- **15 Playwright API-level** tests pass against a real MySQL-backed instance.
- **6 mutations applied, 6 caught.** Notably the staff-privacy gate (UST852) survived JUnit and was
  caught only by Playwright — which is why both layers exist.
- Browser-level RE tests are **not** possible: the `cms` realm has no `RE_*` roles or users, and the
  suite logs in as `cms.admin`, which carries no `entity_code`. Seeding RE users is the prerequisite.
  These 7 failures reproduce identically against the untouched backend on 8082.
