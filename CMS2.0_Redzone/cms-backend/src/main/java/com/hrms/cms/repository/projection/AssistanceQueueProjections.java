package com.hrms.cms.repository.projection;

/**
 * Constructor-expression carriers for the queue-scoped assistance signals (Brief 21 §5.3 item 4).
 *
 * <p>Separate from {@link AssistanceRailProjections} because the two answer different questions. The
 * rail's projections describe ONE complaint the officer is looking at; these describe the officer's
 * whole QUEUE and are never joined to a complaint number. Keeping them apart also keeps the "no PII"
 * rule trivially checkable: there is nothing in this file but numbers, so no reviewer has to read a
 * field list to know the queue endpoint cannot leak a complainant.
 */
public final class AssistanceQueueProjections {

    private AssistanceQueueProjections() {
    }

    /**
     * A numerator and a denominator from ONE statement.
     *
     * <h3>Why one conditional aggregate and not two counts</h3>
     * The signal is "N of your M cases breach within 48h", so both numbers are needed and both are
     * over the SAME row set. Two {@code COUNT(*)} queries would scan that set twice and — worse —
     * could disagree, because a complaint reassigned between the two reads would land in one count
     * and not the other, producing "4 of 3". A single pass cannot be internally inconsistent.
     *
     * <p>{@code COUNT(c)} plus two {@code SUM(CASE WHEN ...)} expressions is portable JPQL: no
     * {@code FILTER}, no {@code COUNT(DISTINCT CASE)}, nothing that needs a dialect. Measured plans are
     * recorded on the finders in {@code AssistanceQueueRepository}.
     *
     * <h3>Nullability is not optional here</h3>
     * {@code SUM} over an EMPTY row set returns NULL in both MySQL and Oracle while {@code COUNT}
     * returns 0, so a record declared with primitive {@code long} fields throws on construction for an
     * officer with an empty queue — which is the single most common case in a register where 4402 of
     * 4403 complaints carry no deadline. The fields are therefore boxed and the compact constructor
     * normalises, so the service never sees a null and never has to remember this.
     *
     * @param total     every open complaint in scope — the DENOMINATOR, the "14" in the example
     * @param breaching those whose deadline falls inside the configured window, counted from today
     *                  INCLUSIVE of already-overdue ones. The NUMERATOR, the "3" in the example
     * @param overdue   the subset of {@code breaching} whose deadline has already passed. Carried
     *                  separately so the client can say "including 2 already overdue" rather than
     *                  flattening a missed statutory window into "upcoming"
     */
    public record QueueDeadlineCounts(Long total, Long breaching, Long overdue) {

        public QueueDeadlineCounts {
            total = total == null ? 0L : total;
            breaching = breaching == null ? 0L : breaching;
            overdue = overdue == null ? 0L : overdue;
        }

        /** The all-zero reading, for a scope that could not be established. */
        public static QueueDeadlineCounts none() {
            return new QueueDeadlineCounts(0L, 0L, 0L);
        }

        public boolean isEmpty() {
            return total == 0L;
        }
    }
}
