# Postfix Configuration for cms-mail-intake

## Quick Setup (3 files + 1 service)

> **IMPORTANT:** This adds CMS mail routing alongside existing Mail-in-a-Box config.
> Do NOT replace existing settings — APPEND to them.

### Step 1: Install milter

```bash
pip3 install pymilter
mkdir -p /opt/cms-mail-intake
cp milter.py /opt/cms-mail-intake/
cp cms-header-milter.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now cms-header-milter
```

Verify milter is running on TCP port 8899:
```bash
systemctl status cms-header-milter
ss -tlnp | grep 8899
```

### Step 2: Postfix config — APPEND to `/etc/postfix/main.cf`

**Do NOT remove existing settings.** Only add/modify these lines:

```ini
# --- CMS Mail Intake additions ---

# APPEND hash file to existing virtual_alias_maps (keep sqlite!)
virtual_alias_maps = sqlite:/etc/postfix/virtual-alias-maps.cf, hash:/etc/postfix/virtual_aliases

# Add transport_maps for smart host routing to OpenShift
transport_maps = hash:/etc/postfix/transport

# APPEND CMS milter to existing DKIM/DMARC milters (keep 8891 + 8893!)
smtpd_milters = inet:127.0.0.1:8891 inet:127.0.0.1:8893 inet:127.0.0.1:8899
non_smtpd_milters = inet:127.0.0.1:8891 inet:127.0.0.1:8893 inet:127.0.0.1:8899
milter_default_action = accept
```

**What NOT to change:**
- `virtual_transport = lmtp:[127.0.0.1]:10025` — leave as-is (default for local mailboxes)
- `virtual_mailbox_domains` — leave as-is
- OpenDKIM (8891) and OpenDMARC (8893) — keep them in the milter list

### Step 3: Redirect rule

Create `/etc/postfix/virtual_aliases`:

```
crpc@rebit.co.in    cms20bot@cms20.rbi.org.in
```

This rewrites the recipient. Postfix then looks up `cms20.rbi.org.in` in transport_maps.

### Step 4: Smart host routing

Create `/etc/postfix/transport`:

```
cms20.rbi.org.in    smtp:[172.17.15.55]:10025
```

Replace `172.17.15.55` with your OpenShift node IP.
This tells Postfix to deliver mail for `cms20.rbi.org.in` via SMTP (not LMTP!) to OpenShift.

### Step 5: Apply

```bash
postmap /etc/postfix/virtual_aliases
postmap /etc/postfix/transport
postfix reload
```

### Step 6: Test

```bash
echo "Test complaint" | mail -s "Test CMP-001" -r "test@rebit.co.in" crpc@rebit.co.in
tail -f /var/log/mail.log
```

Expected log flow:
1. milter adds `X-Original-Sender: test@rebit.co.in`
2. virtual_alias rewrites `crpc@rebit.co.in` → `cms20bot@cms20.rbi.org.in`
3. transport_maps routes to `smtp:[172.17.15.55]:10025`
4. cms-mail-intake receives the email via SMTP

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Mail still goes to local INBOX | virtual_alias_maps not loaded | Run `postmap /etc/postfix/virtual_aliases && postfix reload` |
| `Syntax error, command unrecognised` | Using LMTP instead of SMTP | Check transport file uses `smtp:` not `lmtp:` |
| No X-Original-Sender header | Milter not running or socket mismatch | Verify `ss -tlnp \| grep 8899` and main.cf uses `inet:127.0.0.1:8899` |
| DKIM failures on outbound mail | Removed OpenDKIM milter | Restore `inet:127.0.0.1:8891 inet:127.0.0.1:8893` in smtpd_milters |
| Existing aliases broken | sqlite maps removed | Restore `sqlite:/etc/postfix/virtual-alias-maps.cf` in virtual_alias_maps |
