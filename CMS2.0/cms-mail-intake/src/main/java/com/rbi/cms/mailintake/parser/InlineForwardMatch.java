package com.rbi.cms.mailintake.parser;

/** Raw capture-group text from an inline "-----Original Message-----" style block, before any
 *  further parsing (the "sent" field is deliberately left as raw text — free-form date formats
 *  across Outlook locales aren't worth a universal parser here; NormalisedMailBuilder attempts a
 *  best-effort parse and falls back to leaving originalSentAt null rather than failing). */
record InlineForwardMatch(String from, String sentRaw, String to, String subject) {}
