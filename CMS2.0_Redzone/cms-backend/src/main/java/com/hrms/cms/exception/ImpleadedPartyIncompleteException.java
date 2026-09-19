package com.hrms.cms.exception;

import lombok.Getter;

import java.util.List;

/**
 * Refuses a closure while an impleaded party's required data is still outstanding (UST546).
 *
 * <p>WHY THIS IS A SERVER RULE. The frontend called {@code GET /complaints/{id}/implead-validation} to decide
 * whether closure could proceed — an endpoint that did not exist in any module. The client swallowed the
 * resulting 404 as {@code catchError(() => of({ valid: true, incomplete: [] }))}, so a missing validation
 * endpoint reported every complaint as valid, and its one caller additionally read the result synchronously
 * before the subscribe resolved. A closure could therefore finalise over parties the Ombudsman had brought
 * into the proceeding but never cited a clause against.
 *
 * <p>Carries a {@code messageKey} for translation and the outstanding party names, so the refusal can say
 * which parties are incomplete rather than making an officer hunt for them.
 *
 * <p>Deliberately NOT an {@code IllegalArgumentException}: {@code WorkflowController} maps that to HTTP 200
 * with {@code success:false}, which would report a statutory refusal as a successful call. This maps to 409
 * CONFLICT.
 */
@Getter
public class ImpleadedPartyIncompleteException extends RuntimeException {

    private final String messageKey;
    private final String complaintNumber;
    private final List<String> incompleteParties;

    public ImpleadedPartyIncompleteException(String messageKey, String message, String complaintNumber,
                                             List<String> incompleteParties) {
        super(message);
        this.messageKey = messageKey;
        this.complaintNumber = complaintNumber;
        this.incompleteParties = incompleteParties == null ? List.of() : List.copyOf(incompleteParties);
    }
}
