package com.hrms.cms.exception;

import lombok.Getter;

/**
 * Refuses the permanent destruction of a complaint that is still inside its retention period.
 *
 * <p>SERVER-SIDE ENFORCEMENT OF A STATUTORY OBLIGATION. The obligation was declared in
 * RETENTION_POLICY and honoured by the nightly sweep, but {@code DELETE /api/complaints/{id}} — the
 * only way to destroy a single named record on demand — consulted neither. Its service method was
 * one line, {@code complaintRepository.deleteById(id)}: no retention check, no audit write. A
 * complaint one day into a seven-year period could be erased irreversibly, and because nothing was
 * logged the record and the evidence of its destruction vanished together.
 *
 * <p>Carries the period that blocked the call and the closure date it is measured from, so the
 * refusal can state WHEN the record becomes deletable instead of only that it is not.
 *
 * <p>Deliberately NOT an {@code IllegalArgumentException}: {@code WorkflowController} maps that to
 * HTTP 200 with {@code success:false}, which would report a refused destruction as a successful
 * call. Mapped to 409 CONFLICT, following {@link UploadLinkActiveException} — the request is
 * well-formed and the caller authorised, but it conflicts with the record's current state and
 * becomes valid once the period lapses.
 */
@Getter
public class RetentionPeriodActiveException extends RuntimeException {

    private final String complaintNumber;

    public RetentionPeriodActiveException(String complaintNumber, String reason) {
        super("Cannot permanently delete " + complaintNumber + ": " + reason);
        this.complaintNumber = complaintNumber;
    }
}
