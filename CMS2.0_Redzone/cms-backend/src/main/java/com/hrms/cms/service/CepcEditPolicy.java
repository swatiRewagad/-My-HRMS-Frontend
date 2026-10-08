package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;

import java.util.Locale;
import java.util.Set;

/**
 * Whether a CEPC caller may change a complaint, for every tab that offers a save button.
 *
 * <p>One rule in one place because the client asks it once and applies the answer everywhere: the Summary
 * tab's {@code canEdit} drives {@code isReadOnlyViewer}, which drives {@code canMutate}, which is what
 * disables the Conciliation and Final Decision saves. If a tab's own endpoint disagreed with that answer,
 * the officer would be offered a button that 403s — or worse, be refused a save the screen said was allowed.
 */
public final class CepcEditPolicy {

    /**
     * Statuses after which a complaint is a record rather than a working document.
     *
     * <p>Deliberately just these two. Several longer "closed status" lists exist in this codebase and they
     * disagree with each other; a complaint that is merely {@code resolved} or {@code escalated} is still
     * being worked and locking it would strand the officer holding it.
     *
     * <p>Derived from {@link CepcStatus} rather than spelled out, because the literals {@code closed} and
     * {@code withdrawn} that used to sit here matched nothing once the workflow began writing the canonical
     * names — which left every closed complaint editable.
     */
    private static final Set<String> TERMINAL_STATUSES =
            java.util.stream.Stream.of(CepcStatus.COMPLAINT_CLOSED, CepcStatus.COMPLAINT_WITHDRAWN)
                    .flatMap(s -> CepcStatus.allSpellings(s).stream())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private CepcEditPolicy() {
    }

    /**
     * <p>The officer holding the complaint may edit, and so may an administrator. An unassigned complaint is
     * open to any CEPC caller who reached the endpoint — a freshly registered one has no holder yet, and
     * locking it would leave nobody able to complete the assessment.
     *
     * <p>A terminal complaint is locked for everyone, administrators included: its summary and its
     * conciliation record are the account of what was decided, and an edit after closure is a rewrite of
     * that account rather than a correction to work in progress.
     *
     * <p>Reaching a call site at all already required a CEPC role, enforced by the controller guards.
     */
    public static boolean canEdit(Complaint complaint, String callerUserId, boolean admin) {
        if (isTerminal(complaint)) {
            return false;
        }
        if (admin) {
            return true;
        }
        String holder = complaint.getAssignedOfficer();
        if (holder == null || holder.isBlank()) {
            return true;
        }
        return callerUserId != null && holder.trim().equalsIgnoreCase(callerUserId.trim());
    }

    /**
     * Whether the complaint is closed to edits regardless of who is asking.
     *
     * <p>Exposed separately because one caller needs the terminal half without the ownership half: after a
     * record is forwarded to the entity, the complaint's assigned officer is the entity's code rather than
     * any CEPC user, so ownership has to be established from elsewhere while closure still binds.
     */
    public static boolean isTerminal(Complaint complaint) {
        String status = complaint.getStatus() == null
                ? "" : complaint.getStatus().trim().toLowerCase(Locale.ROOT);
        return TERMINAL_STATUSES.contains(status);
    }
}
