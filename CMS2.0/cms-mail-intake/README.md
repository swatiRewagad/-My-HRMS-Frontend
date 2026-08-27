# cms-mail-intake

Embedded SMTP receiver for mail redirected/forwarded from `crpc@rbi.org.in` to
`cms20bot@cms20.rbi.org.in`. Durably persists every accepted message, parses it, resolves the
original (pre-forward) sender, and hands the normalised result to the existing CMS complaint
pipeline through a small SPI — without CMS ever having to run its own always-on mailbox poller or
depend on IMAP/POP3 credentials.

## Why an embedded SMTP server, not a mailbox poller

The brief's own framing: CRPC already redirects mail server-side to `cms20bot@cms20.rbi.org.in`.
The simplest, most durable way to receive that is to *be* an SMTP endpoint the RBI mail relay can
deliver straight to — no polling interval, no "did we miss one between polls," no shared mailbox
credentials to rotate. The cost is that this module owns a hand-rolled (if deliberately minimal)
SMTP protocol implementation — see "Library choices" below for why that's on Netty rather than an
existing SMTP *server* library.

## Architecture

```
 relay.rbi.org.in                                    cms-mail-intake (this module)
 ┌────────────────┐   SMTP (port 2525/25,   ┌──────────────────────────────────────────────┐
 │ crpc@rbi.org.in │──  optional STARTTLS)──▶│ SmtpServer (Netty, SmartLifecycle)            │
 │ redirect rule    │                        │  └─ SmtpCommandHandler (per connection)       │
 └────────────────┘                          │       • CIDR allowlist / RCPT TO / size caps   │
                                              │       • durable write BEFORE the 250 (rule 1) │
                                              └───────────────────┬────────────────────────────┘
                                                                  │ INBOUND_EMAIL row (RECEIVED)
                                                                  ▼
                                              ┌──────────────────────────────────────────────┐
                                              │ ParserScheduler (fixed worker pool, polls DB) │
                                              │  └─ ParserPipeline                             │
                                              │       • mime4j parse                           │
                                              │       • SenderResolutionChain (5 resolvers)    │
                                              │       • loop-guard / attachment processing     │
                                              │       • InboundEmailStateMachine transitions   │
                                              └───────────────────┬────────────────────────────┘
                                                                  │ NormalisedInboundMail
                                                                  ▼
                                              ┌──────────────────────────────────────────────┐
                                              │ InboundMailHandler (SPI — see below)          │
                                              │  default: LoggingInboundMailHandler (logs,    │
                                              │  reports failure — quarantines everything      │
                                              │  until a real handler bean is supplied)        │
                                              └──────────────────────────────────────────────┘

 /admin/mail-intake/**  (Stage 5, JWT + MAIL_INTAKE_ADMIN role) — quarantine review, raw .eml
 download (audited), maker-checker replay / force-link, metrics/health under /actuator/**.
```

## Data model

Three Stage 2 tables (`database/V11__mail_intake.sql` / `database/oracle/V11__mail_intake.sql`)
plus one Stage 5 table (`V12__mail_intake_admin.sql`):

- **`INBOUND_EMAIL`** — one row per accepted SMTP transaction. `content_sha256` is the idempotency
  key (unique). Everything from `status` onward is owned exclusively by
  `InboundEmailStateMachine` — nothing else in the module calls `setStatus(...)` directly.
- **`INBOUND_EMAIL_ATTACHMENT`** — one row per *accepted* attachment (rejected ones — over a size
  limit, zip-bomb-suspected — have no durable blob to reference, so no row; see
  `AttachmentProcessor.ProcessedAttachment`). Written by `NormalisedMailBuilder` once
  `AttachmentProcessor` has durably stored the bytes.
- **`INBOUND_EMAIL_EVENT`** — append-only audit trail. Every status transition *and* every
  audit-only action (an admin downloading a raw `.eml`, a retention purge, a maker-checker
  request) writes exactly one row here, always through
  `InboundEmailStateMachine.transition/quarantine/replay/recordAuditEvent`.
- **`MAIL_INTAKE_ADMIN_ACTION`** — maker-checker requests (REPLAY / FORCE_LINK). A row moves
  PENDING → APPROVED/REJECTED exactly once; a mis-decided request gets a fresh row, never an edit
  of the old one.

## The state machine

```
RECEIVED → PARSED → NORMALISED → DISPATCHED → PROCESSED
    ↓          ↓          ↓            ↓
    └──────────┴──────────┴────────────┴──→ QUARANTINED (terminal until an operator replays it)
                                        └──→ FAILED (retryable, exponential backoff, see
                                             cms.mail.intake.retry.*)
```

See `InboundEmailStateMachine` for the full legal-transition table. FAILED is a real, persisted
status (not a flag layered on another one) so a crash mid-retry never loses which forward stage
was being attempted (`failed_stage`).

## Sender-resolution chain

The hard problem this module exists to solve: CRPC's redirect makes every message's SMTP envelope
and `From:` header say `crpc@rbi.org.in`, not the citizen who actually complained. Five resolvers
run in order (`@Order` on each `SenderResolver` bean), first confident match wins:

1. **`RedirectHeaderSenderResolver`** — `X-Original-Sender` (or whatever
   `cms.mail.intake.resolver.original-sender-header-name` is configured to), if the RBI mail team
   can add it at the relay. Best case: unambiguous, zero parsing.
2. **`CustomHeaderSenderResolver`** — other known Exchange/relay headers that sometimes carry the
   original sender.
3. **`NestedMessageSenderResolver`** — a true forward (`message/rfc822` attachment or nested MIME
   part) is unwrapped recursively up to `resolver.nested-message-depth-cap`.
4. **`TnefSenderResolver`** — Outlook's `winmail.dat` (TNEF) is decoded via POI's `HMEFMessage`,
   and the inline-forward regex (below) is re-run against the decoded body.
5. **`InlineForwardBlockSenderResolver`** — the common case: someone hit "Forward" and the
   original headers are now just text in the body ("-----Original Message-----" / the Hindi
   प्रेषक: equivalent). Matched via `resolver.inline-forward-patterns`, keyed by language — adding
   a language RBI's citizens actually use is a **config change**, not a code change; see
   `MailIntakeProperties.Resolver#defaultInlineForwardPatterns` for the English/Hindi defaults.

If none match: `QUARANTINED` with `UNRESOLVED_ORIGINAL_SENDER`, never a guess. An operator
resolves it by hand via the admin endpoints (see RUNBOOK.md) and either replays it (if the real
resolver chain should now catch it — e.g. after a config fix) or force-links it directly to a
complaint.

## Extension point: wiring this into the real CMS pipeline

This module deliberately does not depend on `cms-backend` or call it directly — see Stage 1. It
publishes exactly one interface, `com.rbi.cms.mailintake.spi.InboundMailHandler`:

```java
public interface InboundMailHandler {
    HandlerResult handle(NormalisedInboundMail mail);
}
```

Supply a `@Bean` of this type (any module that depends on `cms-mail-intake`, or a
`@Configuration` class added to this module directly) and it replaces the default
`LoggingInboundMailHandler` automatically — see `InboundMailHandlerConfig`'s
`@ConditionalOnMissingBean` factory method. `NormalisedInboundMail` carries the resolved sender,
subject, text/HTML body (quoted trailing content already split off — see `QuoteStripper`), any
matched `complaintRef`, and every accepted attachment (with extracted text where Tika could get
it). Return `HandlerResult.success(linkedComplaintId)`,
`HandlerResult.retryableFailure(reason)`, or `HandlerResult.permanentFailure(reason)` — the
pipeline turns each into the matching `InboundEmailStateMachine` transition.

## Library choices (Stage 1 summary)

| Concern | Choice | Why |
|---|---|---|
| SMTP protocol | Hand-rolled `SmtpCommandHandler` on Netty | SubEthaSMTP is unmaintained/javax-only; Apache James is a full mail server — too much machinery for a single-recipient, no-AUTH listener |
| MIME parsing | Apache James Mime4j (lenient mode) | Must never fail-closed on a malformed message (rule 4) — Mime4j tolerates what Jakarta Mail rejects |
| Attachment typing/extraction | Apache Tika | Detects real content type from bytes, independent of the declared one (rule: never trust a declared type) |
| HTML → text | jsoup | Body fallback when a message is HTML-only |
| TNEF/winmail.dat | Apache POI `poi-scratchpad` (read-only) | Only realistic library for decoding Outlook's proprietary rich-text wrapper |
| Encryption at rest | `PayloadEncryptionService` (AES-256-GCM, `cms-common`) | Raw bytes on disk are PII; envelope-encrypted with a versioned blob format |

## Configuration

Every threshold/regex/address/CIDR/timeout lives under `cms.mail.intake.*` — see
`src/main/resources/application.yml` for the fully commented reference and
`MailIntakeProperties` for the validated, typed binding. Nothing under that prefix should ever
need a code change to retune.
