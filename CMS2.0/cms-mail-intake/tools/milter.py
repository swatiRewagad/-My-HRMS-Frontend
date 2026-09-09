#!/usr/bin/env python3
"""
CMS Mail Intake — Postfix Milter

Adds X-Original-Sender header to mail destined for the CMS bot address
before Postfix redirects it via virtual_alias_maps. This preserves the
original complainant's email address so cms-mail-intake can resolve it.

Installation:
    pip3 install pymilter

Postfix main.cf:
    smtpd_milters = inet:127.0.0.1:8899
    non_smtpd_milters = inet:127.0.0.1:8899
    milter_default_action = accept

Systemd service:
    See cms-header-milter.service in this directory.

Usage:
    python3 milter.py                          # default port 8899
    python3 milter.py --port 8899              # explicit port
    python3 milter.py --socket /run/cms-milter/milter.sock  # unix socket
"""

import argparse
import logging
import sys
import os

try:
    import Milter
except ImportError:
    print("ERROR: pymilter not installed. Run: pip3 install pymilter")
    sys.exit(1)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)
log = logging.getLogger("cms-milter")

# Addresses that trigger the X-Original-Sender header injection.
# Mail to any of these gets the header added before Postfix redirects it.
TARGET_ADDRESSES = {
    "crpc@rbi.org.in",
    "crpc@rebit.org.in",
    "crpc@rebit.co.in",
    "cms20bot@cms20.rbi.org.in",
    "cmsbot@rebit.org.in",
    "cmsbot@rebit.co.in",
}

HEADER_NAME = "X-Original-Sender"


class CmsHeaderMilter(Milter.Base):
    """
    For each SMTP transaction, captures the envelope sender (MAIL FROM)
    and recipient list (RCPT TO). At end-of-message, if any recipient
    matches a CMS target address, injects X-Original-Sender with the
    envelope sender value.
    """

    def __init__(self):
        self.id = Milter.uniqueID()
        self.sender = None
        self.recipients = []
        self.from_header = None

    @Milter.noreply
    def connect(self, hostname, family, hostaddr):
        self.sender = None
        self.recipients = []
        self.from_header = None
        return Milter.CONTINUE

    def envfrom(self, mailfrom, *params):
        self.sender = mailfrom.strip("<>").strip()
        self.recipients = []
        self.from_header = None
        log.debug("[%d] MAIL FROM: %s", self.id, self.sender)
        return Milter.CONTINUE

    def envrcpt(self, to, *params):
        rcpt = to.strip("<>").strip().lower()
        self.recipients.append(rcpt)
        log.debug("[%d] RCPT TO: %s", self.id, rcpt)
        return Milter.CONTINUE

    def header(self, name, value):
        if name.lower() == "from" and self.from_header is None:
            self.from_header = value.strip()
        return Milter.CONTINUE

    def eom(self):
        matched = [r for r in self.recipients if r in TARGET_ADDRESSES]
        if not matched:
            return Milter.CONTINUE

        # Use envelope sender (MAIL FROM), which is the actual complainant.
        # Fall back to From: header if envelope sender is empty (bounce).
        original_sender = self.sender if self.sender else self.from_header

        if not original_sender:
            log.warning("[%d] No sender found for CMS-bound mail to %s — skipping header",
                        self.id, matched)
            return Milter.CONTINUE

        # Don't add if already present (e.g. from upstream relay)
        # Milter API doesn't have a "get header" method, so we always add.
        # cms-mail-intake handles duplicates by taking the first one.
        self.addheader(HEADER_NAME, original_sender)
        log.info("[%d] Added %s: %s (recipients: %s)",
                 self.id, HEADER_NAME, original_sender, matched)

        return Milter.CONTINUE

    def abort(self):
        return Milter.CONTINUE

    def close(self):
        return Milter.CONTINUE


def main():
    parser = argparse.ArgumentParser(description="CMS X-Original-Sender Postfix Milter")
    parser.add_argument("--port", type=int, default=8899,
                        help="TCP port to listen on (default: 8899)")
    parser.add_argument("--socket", type=str, default=None,
                        help="Unix socket path (overrides --port)")
    parser.add_argument("--debug", action="store_true",
                        help="Enable debug logging")
    args = parser.parse_args()

    if args.debug:
        logging.getLogger().setLevel(logging.DEBUG)

    if args.socket:
        listen_addr = args.socket
        if os.path.exists(listen_addr):
            os.unlink(listen_addr)
        log.info("CMS Header Milter starting on unix:%s", listen_addr)
    else:
        listen_addr = "inet:%d@127.0.0.1" % args.port
        log.info("CMS Header Milter starting on %s", listen_addr)

    log.info("Target addresses: %s", TARGET_ADDRESSES)

    Milter.factory = CmsHeaderMilter
    Milter.set_flags(Milter.ADDHDRS)
    Milter.runmilter("CmsHeaderMilter", listen_addr, timeout=600)
    log.info("CMS Header Milter stopped.")


if __name__ == "__main__":
    main()
