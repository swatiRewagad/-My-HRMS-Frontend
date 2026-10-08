package com.hrms.cms.dto.cepc;

/**
 * Every complaint-table count the CEPC dashboard shows, gathered in one pass.
 *
 * <p>The target of a JPQL constructor expression, which is why it is a top-level type: Hibernate resolves the
 * class by its fully-qualified name in the query string, and a nested type would have to be named with a
 * {@code $} that no reader would recognise as a class reference.
 *
 * <p><b>Component order is the contract.</b> Hibernate matches the constructor positionally against the
 * {@code SELECT new …} list, and every component has the same type, so a reordering compiles, runs, and puts
 * each count under the wrong label. Add at the end.
 *
 * <p>Components are boxed {@code Long} because {@code coalesce(sum(…), 0L)} produces a {@code Long}, and
 * Hibernate matches the constructor on declared argument types rather than unboxing to fit.
 */
public record CepcCountSnapshot(
        Long totalPending,
        Long pendingWithMe,
        Long pendingWithRe,
        Long pendingAtMeetingScheduled,
        Long slaBreached,
        Long sla0To15Days,
        Long sla16To30Days,
        Long meetingScheduled,
        Long sentBackToMe,
        Long sentToRe,
        Long responseFromCp,
        Long withdrawn,

        /**
         * Complaints in status DRAFT.
         *
         * <p>Last rather than beside the other tab counts because the arms are positional in the JPQL
         * constructor expression, and appending is the only change that cannot silently shift another count
         * into the wrong field.
         */
        Long draft) {

    /**
     * Zero for every count.
     *
     * <p>Needed because the aggregate query returns no row at all when the complaint table holds nothing in
     * scope, and the dashboard must then show zeroes rather than failing.
     */
    public static CepcCountSnapshot empty() {
        return new CepcCountSnapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    /** Null-safe unboxing, for callers assembling the primitive-valued response records. */
    public static long or0(Long v) {
        return v == null ? 0L : v;
    }
}
