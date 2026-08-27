package com.rbi.cms.mailintake.smtp;

import lombok.Getter;
import lombok.Setter;

import java.io.ByteArrayOutputStream;

/** Mutable per-connection state. One instance lives in the Netty channel's attribute map for the
 *  lifetime of the TCP connection; RSET/successful DATA clear the per-transaction fields but keep
 *  the connection-level ones (remoteIp, tlsActive, heloDomain). */
@Getter
@Setter
class SmtpSession {

    enum Phase { CONNECTED, GREETED, MAIL, RCPT, DATA }

    private final String remoteIp;
    private Phase phase = Phase.CONNECTED;
    private boolean tlsActive = false;
    private String heloDomain;

    private String mailFrom;
    private String rcptTo;
    private final ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
    private long dataBytesRead = 0;

    SmtpSession(String remoteIp) {
        this.remoteIp = remoteIp;
    }

    void resetTransaction() {
        phase = tlsActive || heloDomain != null ? Phase.GREETED : Phase.CONNECTED;
        mailFrom = null;
        rcptTo = null;
        dataBuffer.reset();
        dataBytesRead = 0;
    }
}
