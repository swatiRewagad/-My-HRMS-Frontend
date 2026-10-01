package com.hrms.cms.dto;

import java.util.List;

/**
 * The assistance-rail payload (Brief 21).
 *
 * <h2>The shape is a contract, not an implementation detail</h2>
 * A parallel frontend was built against this exact JSON, so field names, nullability and the
 * {@code tier}/{@code kind} vocabulary are fixed. {@code kind} in particular is a STABLE MACHINE KEY —
 * the client selects an icon and an i18n key from it — so renaming one is a breaking change even
 * though nothing in Java reads it. The kinds are therefore declared as constants in
 * {@link com.hrms.cms.service.AssistanceRailService} rather than spelled as inline literals.
 *
 * <h2>Why records and not a Map</h2>
 * Every neighbouring controller in this codebase hand-builds a {@code LinkedHashMap}. That is avoided
 * here precisely because the shape is pinned: a map lets a typo'd key compile and ship, whereas a
 * record makes the contract the compiler's problem. Jackson serialises records field-order-stable,
 * and {@code null} is emitted rather than omitted, which the contract requires for {@code detail},
 * {@code count} and {@code link}.
 */
/**
 * @param complaintNumber the complaint the rail was asked about, echoed back
 * @param glow            true iff {@code signals} is non-empty — the rail only draws attention when it
 *                        has something. Derived here rather than left to the client so "glowing" and
 *                        "has content" cannot drift apart; a rail that glows with nothing behind it
 *                        trains officers to ignore it, which costs more than the feature is worth.
 * @param signals         what is worth saying, possibly empty
 * @param count           {@code signals.size()}, carried explicitly because the contract asks for it
 */
public record AssistanceRailResponse(
        String complaintNumber,
        boolean glow,
        List<Signal> signals,
        int count) {

    /**
     * One thing worth saying.
     *
     * @param tier   0 for per-user memory, 1 for a precomputed prior. There is no tier 2 — see
     *               {@link com.hrms.cms.service.AssistanceRailService}.
     * @param kind   stable machine key; the client's icon and i18n lookup depend on it
     * @param title  short, already-resolved display text
     * @param detail longer text, or null
     * @param count  a number the signal is about, or null when the signal is not a count
     * @param link   a RELATIVE app route, or null. Never absolute: an absolute URL from the server
     *               would be an open-redirect surface, and the client routes internally anyway.
     */
    public record Signal(int tier,
                         String kind,
                         String title,
                         String detail,
                         Long count,
                         String link) {

        /** A tier-0 memory signal. */
        public static Signal memory(String kind, String title, String detail, String link) {
            return new Signal(0, kind, title, detail, null, link);
        }

        /** A tier-1 prior, which always carries the number it is reporting. */
        public static Signal prior(String kind, String title, String detail, long count, String link) {
            return new Signal(1, kind, title, detail, count, link);
        }
    }

    /** The degraded response: a rail with nothing to say, which is a normal state and not an error. */
    public static AssistanceRailResponse empty(String complaintNumber) {
        return new AssistanceRailResponse(complaintNumber, false, List.of(), 0);
    }

    /** Keeps {@code glow} and {@code count} consistent with {@code signals} by construction. */
    public static AssistanceRailResponse of(String complaintNumber, List<Signal> signals) {
        List<Signal> safe = signals == null ? List.of() : List.copyOf(signals);
        return new AssistanceRailResponse(complaintNumber, !safe.isEmpty(), safe, safe.size());
    }
}
