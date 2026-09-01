# Claude Code Build Prompt — CMS 2.0 Inbound Email Intake Service

---

## PART 0 — Read this yourself before pasting (not for Claude Code)

Four things decide whether this design works at all. Confirm them with the Data Centre / mail team first, because two of them change the code.

**1. Ask for a *redirect* rule, not a *forward* rule.**
This is the single most important ask. On Exchange/M365:

| | What the receiving server sees |
|---|---|
| **Redirect** (`Redirect the message to…`) | Original `From:`, `To:`, `Date:`, `Message-ID` preserved. `Resent-From` / `X-MS-Exchange-*` headers added. Original body intact. |
| **Forward** (`Forward the message to…`) | `From:` becomes the CRPC mailbox. Original message becomes either a `message/rfc822` attachment or an inline `-----Original Message-----` block. Attachments may be re-encoded or dropped. |

Redirect makes the parser trivial and reliable. Forward makes it a heuristics problem forever. Push hard for redirect. The prompt below handles both, but ask for redirect.

**2. MX + firewall.** `cms20.rbi.org.in` needs an MX record pointing at your host, and inbound TCP/25 open from the RBI mail relay IP range only. Get that IP allowlist in writing — it's your authentication mechanism (see #4).

**3. Port 25 and root.** The JVM cannot bind :25 as a non-root user. Options: `setcap cap_net_bind_service`, `authbind`, listen on 2525 with an iptables/nftables REDIRECT, or terminate on the DC's existing Postfix and have it relay to your app on 2525. Decide this before build; it's a deploy concern, not a code concern, but the runbook must state it.

**4. SPF/DKIM will fail — by design.** Forwarded/redirected mail breaks SPF (the relay isn't the original domain's authorised sender) and often breaks DKIM if headers are rewritten. **Do not authenticate on SPF/DKIM.** Authenticate on: connecting IP ∈ RBI relay allowlist, plus `RCPT TO` exactly matching your bot address. Everything else gets `550`.

Also worth one line of thought: if the DC can offer Microsoft Graph delegated/application access to the CRPC mailbox, that is a cleaner path than running an MTA. It's usually blocked by the same "no non-human account" policy — but confirm, because it would make this whole build unnecessary.

---

## PART 1 — The prompt to paste into Claude Code

> Paste everything below this line.

---

You are building a production-grade inbound email intake service for the RBI CMS 2.0 platform. Read this entire brief before writing any code. Ask me about anything ambiguous rather than assuming.

### Context and constraint

CMS 2.0 must ingest complaint emails sent by citizens to `crpc@rbi.org.in`. The Data Centre policy forbids creating non-human/service mailbox accounts, so we cannot poll that mailbox over IMAP/POP3/Graph with service credentials.

The agreed workaround: human owners of `crpc@rbi.org.in` configure a server-side rule that **redirects** (preferred) or forwards each incoming mail to `cms20bot@cms20.rbi.org.in`. We control `cms20.rbi.org.in`. We will therefore run **our own SMTP receiver embedded in the Spring Boot service**, accept those messages directly over SMTP, persist them durably, parse them, and hand them to the CMS complaint pipeline.

This service is the front door to a regulated grievance-redress system. Losing a citizen complaint email is a compliance incident. Correctness and durability outrank throughput and elegance.

### Non-negotiable behavioural rules

1. **Never acknowledge before durability.** Return SMTP `250` only after the raw RFC 5322 bytes are written to the message store *and* the tracking row is committed. If either fails, return `451 4.3.0` so the sending relay retries. Never `250` then lose the mail.
2. **Never be an open relay.** Accept `RCPT TO` only for the configured bot address(es). Reject everything else with `550 5.7.1`. Reject `AUTH`. Reject any attempt to relay outbound.
3. **IP allowlist is the authentication.** Reject connections from outside the configured CIDR allowlist at the connection stage, before `MAIL FROM`. Log the rejection.
4. **Do not fail-closed on parse errors.** A message we cannot parse must still be stored and quarantined for manual review — never dropped, never NDR'd.
5. **Idempotency.** The same message delivered twice (relay retry, dual delivery, admin replay) must not create two complaints.
6. **PII discipline.** Encrypt raw message bytes at rest using the existing CMS field-encryption provider. Never log message bodies, attachment contents, or complainant contact details. Log only message ID, envelope addresses (masked), size, and status.

### Architecture

Single Spring Boot module, `cms-mail-intake`, deployable standalone or as a library into the CMS core service. Layered:

```
SMTP listener  →  RawMessageStore  →  IngestQueue (DB outbox)
                                          ↓
                            ParserPipeline (async workers)
                                          ↓
                            NormalisedInboundMail
                                          ↓
                            InboundMailHandler (SPI)  →  CMS complaint pipeline
```

The listener thread does the minimum: allowlist check, recipient check, size check, stream bytes to store, commit row, `250`. All parsing is asynchronous. A slow or broken parser must never block SMTP acceptance.

**Library choices** — verify current versions and Jakarta vs javax namespace compatibility against Spring Boot 3.x before committing; this is a known tripwire. Propose your picks to me with versions before you start:
- SMTP server: SubEtha SMTP (use a Jakarta-namespace maintained fork) or Apache James `james-server-protocols-smtp`. State your recommendation and why.
- MIME parsing: Jakarta Mail 2.x, or Apache James `mime4j` for damaged/non-conformant messages. I lean toward mime4j for robustness — argue if you disagree.
- Attachment text extraction: Apache Tika.
- HTML→text: jsoup.
- TNEF (`winmail.dat`) decoding: Apache POI HMEF. Exchange will send these; handle them.
- Tests: GreenMail, Testcontainers, plus a fixture corpus of `.eml` files.

### Data model (Flyway migration)

`inbound_email`
- `id` (UUID, PK)
- `smtp_message_id` — RFC 5322 `Message-ID`, nullable
- `content_sha256` — hash of raw bytes, for dedup
- `envelope_from`, `envelope_to`, `remote_ip`, `received_at`
- `raw_store_uri`, `raw_size_bytes`
- `status` — enum, see state machine
- `quarantine_reason` — enum, nullable
- `attempt_count`, `next_attempt_at`, `last_error`
- `original_from`, `original_subject`, `original_sent_at` — populated after parse
- `complaint_ref` — extracted CMS reference, nullable
- `linked_complaint_id` — nullable FK
- Unique index on `content_sha256`; non-unique index on `smtp_message_id`, `status`, `next_attempt_at`.

`inbound_email_attachment` — id, email_id FK, filename (sanitised), declared_content_type, detected_content_type (Tika), size, sha256, store_uri, scan_status, extracted_text_uri.

`inbound_email_event` — append-only audit: email_id, timestamp, from_status, to_status, actor, detail. Every transition writes a row. This is the evidence trail for a regulated system.

### State machine

```
RECEIVED → PARSED → NORMALISED → DISPATCHED → PROCESSED
     ↓         ↓          ↓            ↓
     └─────────┴──────────┴────────────┴──→ QUARANTINED
                                        └──→ FAILED (retryable)
```
`FAILED` retries with exponential backoff and a max attempt cap, then moves to `QUARANTINED`. `QUARANTINED` is terminal until an operator replays it.

### Parsing: extracting the original sender

This is the hard part. Implement as an ordered chain of resolvers; first confident match wins; record which resolver fired in the audit event.

1. **Redirect path.** If `Resent-From` present, or `X-MS-Exchange-Inbox-Rules-Loop` / `X-MS-Exchange-Organization-*` headers indicate a redirect, treat the top-level `From:` as the original sender. Fastest and most reliable.
2. **Custom header.** If the mail team can add `X-Original-Sender` / `X-Envelope-From` at the relay, prefer it. Make the header name configurable.
3. **Nested message.** If any part has content type `message/rfc822`, parse the inner message and treat it as canonical — its `From`, `Subject`, `Date`, body, and attachments become the complaint. Handle nesting depth >1 (forward of a forward), with a configurable depth cap.
4. **TNEF.** If a `winmail.dat` / `application/ms-tnef` part is present, decode it and re-run resolution on the decoded content.
5. **Inline forward block.** Regex the `-----Original Message-----` / `From: … Sent: … To: … Subject:` block. Make the patterns externally configurable — Outlook localises these labels, and CMS supports 10 languages, so `De:`, `Von:`, `प्रेषक:` etc. will appear. Ship English + Hindi patterns; make adding more a config change, not a code change.
6. **Fail.** Quarantine with `UNRESOLVED_ORIGINAL_SENDER`. Do not guess.

Also extract, where present: original recipients, original sent timestamp (with timezone), reply-to, and any CMS complaint reference number matched against a configurable regex — so replies thread onto an existing complaint rather than opening a duplicate.

### Content handling

- Decode RFC 2047 encoded-words in headers; handle base64 and quoted-printable bodies; respect declared charsets and fall back to a configurable default with detection rather than mangling.
- Prefer `text/plain` alternative; if only `text/html`, convert with jsoup and store both.
- Strip signature blocks and prior-thread quoting into a separate field rather than deleting them.
- Attachments: enforce a max count, max individual size, and max total size. Sanitise filenames (strip path separators, control characters, null bytes, leading dots; cap length). **Never** derive a filesystem path from an untrusted filename — store by UUID and keep the display name in the DB only.
- Guard against zip bombs (decompression ratio and depth caps) and XXE (disable external entities in every XML parser, including inside Tika config).
- ClamAV scan hook via a `AttachmentScanner` interface with a no-op default; infected → quarantine, do not delete the original.

### Loop and abuse protection

- Drop and log messages with `Auto-Submitted: auto-*` or `Precedence: bulk/list/junk` unless explicitly allowlisted.
- Track a `X-CMS-Loop-Guard` header we add on any outbound reply; if we ever receive it back, quarantine as `LOOP_DETECTED`.
- Per-IP connection rate limit, max concurrent connections, max recipients per transaction = 1, connection and command timeouts, max message size (default 25 MB, configurable).
- STARTTLS enabled with the `cms20.rbi.org.in` certificate; make TLS-required a config flag (may need to start permissive if the relay is old).

### Configuration

All under `cms.mail.intake.*` in `application.yml`, bound to a `@ConfigurationProperties` record with `@Validated`. Every threshold, regex, address, CIDR, and timeout mentioned above is configurable. No magic numbers in code. Include a fully commented sample config with production-sane defaults.

### Operations

- Micrometer metrics: messages received/accepted/rejected/quarantined, parse latency, queue depth, oldest unprocessed age, attachment scan failures.
- Spring Boot health indicator: listener bound, store writable, queue depth under threshold.
- Structured JSON logs with an ingest correlation ID propagated through the pipeline.
- Admin REST endpoints, secured with the existing CMS role model and following the **maker-checker** governance pattern already used elsewhere in CMS: list quarantined mail, view redacted metadata, download raw `.eml` (audited), replay a message, force-link to a complaint. Every action writes an `inbound_email_event`.
- Retention job honouring the CMS retention policy — purge raw bytes after N days while keeping the metadata row and audit trail.

### Extension point

```java
public interface InboundMailHandler {
    HandlerResult handle(NormalisedInboundMail mail);
}
```
Ship a `LoggingInboundMailHandler` default. The actual complaint-creation logic lives outside this module — do not implement CMS business rules here.

### Tests (required, not optional)

- Unit tests for every resolver in the sender-resolution chain.
- A `src/test/resources/eml/` corpus with at least: Exchange redirect; Exchange forward with nested `message/rfc822`; inline `-----Original Message-----` forward; forward-of-a-forward; `winmail.dat` TNEF; RFC 2047 encoded non-ASCII subject; Devanagari body; base64 PDF attachment; 20 MB attachment; malformed MIME with unterminated boundary; message with no `Message-ID`.
- GreenMail integration test proving the full SMTP→persist→parse→dispatch path.
- **Durability test**: kill persistence mid-transaction, assert `451` returned and no orphan row.
- **Idempotency test**: deliver the same bytes twice, assert one complaint dispatch.
- **Security tests**: relay attempt rejected; off-allowlist IP rejected; path-traversal filename neutralised; zip bomb rejected; XXE payload inert.

### Deliverables

1. Working Maven/Gradle module matching the existing CMS build conventions — inspect the repo and follow them rather than inventing new ones.
2. Flyway migration.
3. Fully commented sample configuration.
4. Tests as specified, passing.
5. `RUNBOOK.md`: DNS/MX setup, firewall and relay allowlist, port-25 binding options, TLS cert install, how to replay quarantined mail, how to read the metrics, first-response steps for "complaints stopped arriving".
6. `README.md`: architecture diagram, sender-resolution chain, state machine, extension point.

### How to proceed

Do not write all of this in one pass. Work in these stages, and stop for my review at the end of each:

1. **Stage 1** — Read the existing repo. Report back: build conventions, persistence stack, encryption provider, security/role model, config patterns you'll reuse. Propose your library choices with versions. **Write no production code yet.**
2. **Stage 2** — Data model, Flyway migration, config properties, state machine, and the `InboundMailHandler` SPI.
3. **Stage 3** — SMTP listener with allowlist, recipient check, durable persist, correct `250`/`451`/`550` semantics. Plus the durability and security tests.
4. **Stage 4** — Parser pipeline and the sender-resolution chain. Plus the `.eml` corpus tests.
5. **Stage 5** — Admin endpoints, metrics, health, retention job, runbook, README.

At each stage, flag anything in this brief that you think is wrong, over-engineered for our scale, or that conflicts with what you found in the repo. I would rather argue at design time than refactor later.

---

## PART 2 — Optional additions

Consider bolting these on if they fit the CMS roadmap:

- **Fallback ingest path.** If the SMTP listener is ever down for longer than the relay's retry window, mail bounces back to CRPC humans. A secondary drop-folder ingest (SFTP or shared path) that accepts `.eml` files gives operators a manual recovery route using the same pipeline.
- **Classification hand-off.** Once `NormalisedInboundMail` exists, it's the natural input to the CMS intake-triage capability — category, language, and jurisdiction prediction before a human sees it.
- **Delivery assurance.** A daily reconciliation report comparing count of messages the relay says it delivered against count ingested. In a regulated intake path, "we think we got everything" is not an answer.
