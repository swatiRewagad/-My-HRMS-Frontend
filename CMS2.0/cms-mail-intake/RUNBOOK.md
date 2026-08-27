# cms-mail-intake — Operations Runbook

See `README.md` for architecture. This document is for deployment setup and incident response.

## 1. DNS / mail-relay setup (one-time, coordinate with the RBI mail/network team)

This service does **not** need its own MX record — it never receives mail directly from the
public internet. It only needs to be reachable, over the internal network, from whichever MTA
CRPC's redirect rule ultimately relays through.

1. Confirm with the mail team which host(s) will connect to this service — typically the
   Exchange/relay server that already redirects `crpc@rbi.org.in` mail to
   `cms20bot@cms20.rbi.org.in`.
2. Get their outbound IP range(s) in writing and set `cms.mail.intake.allowlist.cidrs`
   (`MAIL_INTAKE_ALLOWLIST_CIDRS`, comma-separated). **This list is the authentication mechanism**
   — SPF/DKIM will legitimately fail on redirected/forwarded mail, so this service deliberately
   does not check them. It starts with an empty allowlist (refuses everything) until this is set.
3. Confirm `RCPT TO` will be exactly `cms20bot@cms20.rbi.org.in` (`cms.mail.intake.recipients.bot-addresses`)
   — anything else gets `550 5.7.1`.

## 2. Firewall

Open the configured port (`cms.mail.intake.listener.port`, default `2525`) from the relay's CIDR
only — not from the wider network, and never from the public internet. `max-connections-per-ip`
(default 5) and `max-concurrent-connections` (default 50) are a second line of defense, not a
substitute for the firewall rule.

## 3. Binding port 25

The JVM cannot bind port 25 (a privileged port) as a non-root user, and this service should not
run as root. Pick one, in order of preference:

- **Relay through the existing enterprise MTA on 25, deliver to this service on a high port**
  (recommended) — the mail team's MTA already exists and already handles port 25 exposure/hardening;
  point its final-hop delivery for `cms20bot@cms20.rbi.org.in` at this service's
  `cms.mail.intake.listener.port` instead.
- **`setcap`**: `sudo setcap 'cap_net_bind_service=+ep' $(readlink -f $(which java))` lets that
  specific JRE binary bind privileged ports without running as root. Re-apply after any JRE
  upgrade.
- **`authbind`** (Debian/Ubuntu-style): run under `authbind --deep`, with `/etc/authbind/byport/25`
  owned by the service account.
- **iptables/nftables REDIRECT**: `iptables -t nat -A PREROUTING -p tcp --dport 25 -j REDIRECT
  --to-port 2525` — keep the service bound to 2525 and let the kernel do the port translation.

## 4. TLS (STARTTLS)

`cms.mail.intake.tls.required` defaults to `false` — start there if the relay's STARTTLS support
is unverified, confirm interoperability, then flip to `true`.

1. Obtain a certificate for `cms20.rbi.org.in` (whatever CA RBI's other internal services use).
2. Set `cms.mail.intake.tls.cert-path` / `cms.mail.intake.tls.key-path`
   (`MAIL_INTAKE_TLS_CERT_PATH` / `MAIL_INTAKE_TLS_KEY_PATH`) to PEM files readable by the service
   account only (`chmod 600`).
3. Restart. `SmtpTlsConfig` builds the `SslContext` at startup and **fails fast** if
   `tls.required=true` but no valid cert/key is configured — check startup logs first if the
   service won't come up after enabling this.
4. Verify with `openssl s_client -starttls smtp -connect <host>:<port>`.

## 5. Reading the metrics

`/actuator/prometheus` (Prometheus format) and `/actuator/metrics/<name>` (JSON, one metric) —
both actuator endpoints are `permitAll` (see `MailIntakeSecurityConfig`), scrape without a token.

| Metric | Type | What it tells you |
|---|---|---|
| `mailintake.messages.received` | counter | SMTP DATA payloads fully read off the wire |
| `mailintake.messages.accepted{duplicate}` | counter | Durably persisted; `duplicate=true` means it matched an existing `content_sha256` |
| `mailintake.messages.rejected{reason}` | counter | SMTP-level reject — `reason` is `NOT_ALLOWLISTED`, `CONNECTION_LIMIT`, `NOT_A_RELAY`, `MULTIPLE_RECIPIENTS`, `TLS_REQUIRED`, or `STORAGE_FAILURE` |
| `mailintake.messages.quarantined{reason}` | counter | Terminal quarantine — `reason` matches `QuarantineReason` (`UNRESOLVED_ORIGINAL_SENDER`, `LOOP_DETECTED`, etc.) |
| `mailintake.parse.latency` | timer | `ParserPipeline.process()` wall time, RECEIVED row → terminal/failed |
| `mailintake.queue.depth` | gauge | RECEIVED rows + FAILED rows past their retry time — a parser-pool backlog |
| `mailintake.queue.oldest_unprocessed_age_seconds` | gauge | Age of the oldest non-terminal row — how far behind the worst case is |
| `mailintake.attachments.scan_failures{verdict}` | counter | `verdict` is `INFECTED` or `SCAN_FAILED` |

A steadily climbing `mailintake.queue.depth` with a flat `mailintake.messages.received` almost
always means the parser worker pool is stuck (see §7) — the SMTP listener and the parser pool are
fully decoupled by design, so a broken parser never shows up as SMTP rejections.

## 6. `/actuator/health`

Goes DOWN if any of: the SMTP listener isn't bound, the raw-message store directory isn't
writable, or the backlog exceeds `cms.mail.intake.health.queue-depth-warning-threshold` (default
500) — see `MailIntakeHealthIndicator`. `show-details: when_authorized` — an unauthenticated probe
sees `UP`/`DOWN` only; the `listenerBound`/`storeWritable`/`queueDepth` breakdown needs a valid
admin JWT.

## 7. First-response: "complaints stopped arriving"

1. **Is the listener even up?** `GET /actuator/health` — `listenerBound: false` means the Netty
   bootstrap isn't bound; check startup logs for a bind failure (wrong port, permission issue —
   see §3, or the port is already in use).
2. **Is mail even reaching this host?** Ask the mail team to check their side first — a relay
   config change, an expired allowlist entry (an IP range changed), or a firewall rule regression
   upstream of this service are all more common than a bug here. `mailintake.messages.received`
   at zero with the listener healthy points this way.
3. **Is it arriving but being rejected at the SMTP level?** Check
   `mailintake.messages.rejected{reason}` — `NOT_ALLOWLISTED` means the relay's IP changed and
   `cms.mail.intake.allowlist.cidrs` needs updating; `NOT_A_RELAY` means RCPT TO isn't
   `cms20bot@cms20.rbi.org.in` (a redirect-rule regression on the mail side).
4. **Is it arriving but stuck unprocessed?** `mailintake.queue.depth` climbing +
   `mailintake.queue.oldest_unprocessed_age_seconds` growing: the parser pool is stuck or
   crash-looping — check application logs (filter on `correlationId` starting `mail-` — see
   README's architecture section) for repeated exceptions from `ParserPipeline`.
5. **Is it arriving and processing, but ending up QUARANTINED?** Check
   `GET /admin/mail-intake/quarantined` and `mailintake.messages.quarantined{reason}` — a spike in
   one specific reason (e.g. everything suddenly `UNRESOLVED_ORIGINAL_SENDER`) usually means the
   relay changed how it forwards mail (a template/signature change breaks the inline-forward
   regex) rather than anything wrong in this service.

## 8. Replaying a quarantined message

Requires two different operators with the `MAIL_INTAKE_ADMIN` role (maker-checker — see README).

```bash
# 1. Maker: look at what's quarantined and why
curl -H "Authorization: Bearer $TOKEN" https://<host>/cms-mail-intake/admin/mail-intake/quarantined

# 2. Maker: inspect one item's full detail + timeline
curl -H "Authorization: Bearer $TOKEN" https://<host>/cms-mail-intake/admin/mail-intake/emails/{id}

# 3a. If the resolver chain should now catch it (e.g. after fixing an inline-forward-patterns
#     config or an allowlist/relay issue), request a REPLAY:
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"reason":"relay template fixed, resolver should match now"}' \
  https://<host>/cms-mail-intake/admin/mail-intake/emails/{id}/replay-requests

# 3b. If the original sender can't be resolved automatically but a human has confirmed which
#     complaint this belongs to, request a FORCE_LINK instead:
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"reason":"confirmed via phone with citizen","targetComplaintId":"CMP-2026-000123"}' \
  https://<host>/cms-mail-intake/admin/mail-intake/emails/{id}/force-link-requests

# 4. Checker (a DIFFERENT operator — the service rejects a self-approval): approve or reject
curl -X POST -H "Authorization: Bearer $TOKEN2" -H "Content-Type: application/json" \
  -d '{"note":"confirmed, approving"}' \
  https://<host>/cms-mail-intake/admin/mail-intake/actions/{actionId}/approve
```

A REPLAY resets the row to `RECEIVED` (attempt count, error, and quarantine reason all cleared)
and it's picked up by the next `ParserScheduler` poll (`cms.mail.intake.parser.poll-interval-seconds`,
default 5s). A FORCE_LINK only sets `linked_complaint_id`/`complaint_ref` — it does not change the
row's status; replay separately afterward if you also want it to go through the pipeline again.

## 9. Downloading a raw message for investigation

`GET /admin/mail-intake/emails/{id}/raw` — returns the decrypted `.eml` and writes an audit event
recording who downloaded it and when. Returns `410 Gone` if the retention job has already purged
the raw bytes for that row (check `rawPurged` on the detail endpoint first) — the metadata row and
full audit trail remain regardless; only the raw bytes are gone.

## 10. Retention

Raw message + attachment bytes are purged `cms.mail.intake.retention.raw-bytes-retention-days`
(default 180) after `received_at`, for `PROCESSED`/`QUARANTINED` rows only — never mid-pipeline.
Runs once daily (`RetentionPurgeJob`); the `INBOUND_EMAIL`/`INBOUND_EMAIL_EVENT` rows themselves
are never deleted. A quarantined-then-replayed row is naturally excluded from purge again once
it's back in a non-terminal status.
