package com.hrms.cms.exception;

import lombok.Getter;

/**
 * An edit was attempted on a complaint whose status has settled (closed, withdrawn or rejected).
 *
 * <p>409 CONFLICT rather than 400: the payload is well-formed and the caller is authorised — the
 * request conflicts with the CURRENT STATE of the record. Reopening is a workflow action with its own
 * audit entry, so a settled complaint becomes editable again only by going through it.
 */
@Getter
public class ComplaintNotEditableException extends RuntimeException {

    private final String complaintNumber;
    private final String status;

    public ComplaintNotEditableException(String complaintNumber, String status) {
        super("Complaint " + complaintNumber + " has status '" + status
                + "' and can no longer be edited. Reopen it first if it must be changed.");
        this.complaintNumber = complaintNumber;
        this.status = status;
    }
}
