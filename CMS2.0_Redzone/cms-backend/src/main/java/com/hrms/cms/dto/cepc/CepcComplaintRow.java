package com.hrms.cms.dto.cepc;

/**
 * One grid row, matching {@code ComplaintColumn} in {@code models/cepc.model.ts} field for field.
 *
 * @param complaintId          the numeric primary key the row links to
 * @param slaBreachIn          human-readable time to the SLA deadline, e.g. {@code "3 days"} or
 *                             {@code "Overdue by 2 days"}; null when the complaint has no deadline
 * @param mode                 filing channel, from {@code FILING_TYPE}
 * @param entityName           resolved from {@code bankId}, falling back to the raw {@code entityCode}
 * @param complaintCategory    resolved from {@code categoryId}
 * @param createdDate          ISO-8601. Named {@code createdDate} because the grid sorts on that key, while
 *                             the column behind it is {@code CREATED_AT}
 * @param contactPerson        the most recently added entry on {@code CEPC_CONTACT_PERSONS} for this
 *                             complaint; null when the dealing officer has logged none yet
 * @param complaintColor       the row tint as a bare colour word, which the grid appends to {@code cc-row-};
 *                             null for the statuses that are meant to carry no tint, which is most of them
 * @param isRead               whether THIS caller has opened the complaint — per-user, not global
 * @param slaColor             a bare CSS colour word ({@code red}/{@code orange}/{@code green}), never a
 *                             class name: the template interpolates it into
 *                             {@code color-mix(in srgb, …)}, which silently renders transparent if handed
 *                             anything that is not a colour
 * @param statusColor          CSS class for the status chip
 * @param statusLabel          the human reading of {@code status}, e.g. {@code "Pending Office Head
 *                             Approval"}. Carried alongside the raw value rather than replacing it, because
 *                             the grid needs words while the callers that branch on a status need the token
 */
public record CepcComplaintRow(
        Long complaintId,
        String complaintNumber,
        String assignedTo,
        String slaBreachIn,
        String mode,
        String complainantName,
        String status,
        String entityName,
        String complaintCategory,
        String createdDate,
        String lastUpdatedDate,
        String priority,
        String subject,
        String contactPerson,
        String complaintColor,
        boolean isRead,
        String slaColor,
        String statusColor,
        String statusLabel,
        String statusLabelKey) {
}
