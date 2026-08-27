package com.rbi.cms.mailintake.smtp;

/** RFC 5321 response lines this listener actually sends. Nothing exotic — the protocol surface is
 *  deliberately tiny (brief: no AUTH, no relaying, one recipient). */
final class SmtpResponses {

    private SmtpResponses() {}

    static final String BANNER = "220 cms20.rbi.org.in CMS Mail Intake ready";
    static final String GREETING_OK = "250 cms20.rbi.org.in";

    static String ehloExtensions(String domain, long maxMessageSizeBytes, boolean tlsAvailable) {
        StringBuilder sb = new StringBuilder();
        sb.append("250-cms20.rbi.org.in\r\n");
        sb.append("250-SIZE ").append(maxMessageSizeBytes).append("\r\n");
        sb.append("250-8BITMIME\r\n");
        if (tlsAvailable) {
            sb.append("250-STARTTLS\r\n");
        }
        sb.append("250 OK");
        return sb.toString();
    }

    static final String MAIL_OK = "250 OK";
    static final String RCPT_OK = "250 OK";
    static final String DATA_START = "354 Start mail input; end with <CRLF>.<CRLF>";
    static final String QUIT_BYE = "221 cms20.rbi.org.in closing connection";
    static final String RSET_OK = "250 OK";
    static final String STARTTLS_READY = "220 Ready to start TLS";

    // 4xx — transient, sender should retry. Rule 1: whenever durability isn't confirmed.
    static final String TRANSIENT_STORAGE_FAILURE = "451 4.3.0 Requested action aborted: local error in processing";
    static final String TRANSIENT_TOO_BUSY = "421 4.3.2 Service temporarily unavailable, too many connections";
    static final String TRANSIENT_TIMEOUT = "421 4.4.2 Connection timed out";

    // 5xx — permanent, sender should not retry unmodified.
    static final String REJECT_NOT_A_RELAY = "550 5.7.1 Relaying denied - recipient not accepted here";
    static final String REJECT_MULTIPLE_RECIPIENTS = "452 4.5.3 Too many recipients - one recipient per transaction";
    static final String REJECT_AUTH_NOT_SUPPORTED = "502 5.5.1 AUTH not supported";
    static final String REJECT_VRFY_NOT_SUPPORTED = "502 5.5.1 VRFY not supported";
    static final String REJECT_COMMAND_OUT_OF_SEQUENCE = "503 5.5.1 Command out of sequence";
    static final String REJECT_MESSAGE_TOO_LARGE = "552 5.3.4 Message size exceeds fixed maximum";
    static final String REJECT_LOOP_DETECTED = "550 5.4.6 Routing loop detected";
    static final String REJECT_SYNTAX_ERROR = "500 5.5.2 Syntax error, command unrecognised";
    static final String REJECT_TLS_ALREADY_ACTIVE = "503 5.5.1 TLS already active";
    static final String REJECT_TLS_NOT_SUPPORTED = "502 5.5.1 STARTTLS not supported";
    static final String REJECT_TLS_REQUIRED = "530 5.7.0 Must issue STARTTLS first";
    static final String REJECT_NOT_ALLOWLISTED = "554 5.7.1 Connection refused";
    static final String NOOP_OK = "250 OK";
}
