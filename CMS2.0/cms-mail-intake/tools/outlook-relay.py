"""
Local SMTP relay for testing with Outlook.

Outlook sends to this relay (port 587), relay adds X-Original-Sender header
and forwards to cms-mail-intake (port 2525).

Setup:
  1. Run this script: python outlook-relay.py
  2. In Outlook, add an account with SMTP server: localhost:587
  3. Send email TO: cms20bot@cms20.rbi.org.in
  4. Relay intercepts, adds header, forwards to cms-mail-intake

The relay extracts the real From: address and puts it in X-Original-Sender,
then rewrites From: to crpc@rbi.org.in (simulating what the RBI relay does).
"""

import asyncio
import email
import smtplib
from aiosmtpd.controller import Controller
from aiosmtpd.smtp import SMTP as SMTPServer

CMS_BOT_HOST = 'localhost'
CMS_BOT_PORT = 2525
RELAY_FROM = 'crpc@rbi.org.in'


class CmsRelayHandler:
    async def handle_DATA(self, server, session, envelope):
        original_from = envelope.mail_from
        rcpt_tos = envelope.rcpt_tos
        data = envelope.content.decode('utf-8', errors='replace')

        # Parse the message
        msg = email.message_from_string(data)

        # Add X-Original-Sender header (the actual sender from Outlook)
        msg['X-Original-Sender'] = original_from
        msg['X-CMS-Relay'] = 'outlook-dev-relay'

        # Rewrite From to simulate the RBI relay behavior
        original_from_header = msg['From']
        del msg['From']
        msg['From'] = RELAY_FROM

        print(f"\n{'='*60}")
        print(f"  RELAY INTERCEPTED")
        print(f"  Original From : {original_from_header}")
        print(f"  X-Original-Sender: {original_from}")
        print(f"  Rewritten From: {RELAY_FROM}")
        print(f"  To            : {', '.join(rcpt_tos)}")
        print(f"  Subject       : {msg.get('Subject', '(no subject)')}")
        print(f"{'='*60}")

        # Forward to cms-mail-intake
        try:
            with smtplib.SMTP(CMS_BOT_HOST, CMS_BOT_PORT) as smtp:
                smtp.sendmail(RELAY_FROM, ['cms20bot@cms20.rbi.org.in'], msg.as_string())
            print(f"  >> Forwarded to cms-mail-intake:{CMS_BOT_PORT} successfully!")
            return '250 Message forwarded to CMS mail intake'
        except Exception as e:
            print(f"  !! Forward FAILED: {e}")
            return f'451 Temporary failure: {e}'


def main():
    handler = CmsRelayHandler()
    controller = Controller(handler, hostname='127.0.0.1', port=587)
    controller.start()
    print("=" * 60)
    print("  CMS Outlook Relay running on port 587")
    print("")
    print("  Configure Outlook SMTP:")
    print("    Server  : localhost")
    print("    Port    : 587")
    print("    Security: None (no TLS, no auth)")
    print("    Send TO : cms20bot@cms20.rbi.org.in")
    print("")
    print("  This relay will:")
    print("    1. Capture your From address")
    print("    2. Add X-Original-Sender header (your real email)")
    print("    3. Rewrite From to crpc@rbi.org.in (simulates relay)")
    print("    4. Forward to cms-mail-intake on port 2525")
    print("")
    print("  Press Ctrl+C to stop")
    print("=" * 60)
    try:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        loop.run_forever()
    except KeyboardInterrupt:
        controller.stop()
        print("\nRelay stopped.")


if __name__ == '__main__':
    main()
