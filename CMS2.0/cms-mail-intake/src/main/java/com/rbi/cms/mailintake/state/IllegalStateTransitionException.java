package com.rbi.cms.mailintake.state;

import com.rbi.cms.mailintake.entity.InboundEmailStatus;

public class IllegalStateTransitionException extends RuntimeException {
    public IllegalStateTransitionException(InboundEmailStatus from, InboundEmailStatus to) {
        super("Illegal inbound_email transition: " + from + " -> " + to);
    }
}
