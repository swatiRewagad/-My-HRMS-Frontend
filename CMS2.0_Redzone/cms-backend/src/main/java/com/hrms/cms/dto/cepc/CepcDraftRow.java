package com.hrms.cms.dto.cepc;

/**
 * One row of the Draft tab, matching {@code DraftComplaintColumn} in {@code models/cepc.model.ts}.
 *
 * <p>Backed by {@code STAFF_DRAFT} — an officer's half-finished intake form, which has no complaint row
 * yet. Hence no complaint number, no SLA and no read state: there is nothing to number, no clock running and
 * nobody but the owner to have read it.
 *
 * @param complaintId the draft's own id, so the row can link back into the intake wizard
 */
public record CepcDraftRow(
        Long complaintId,
        String assignedTo,
        String mode,
        String complainantName,
        String status,
        String entityName,
        String complaintCategory,
        String createdDate,
        String lastUpdatedDate,
        String priority) {
}
