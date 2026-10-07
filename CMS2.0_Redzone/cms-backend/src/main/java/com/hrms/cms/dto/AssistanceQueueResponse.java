package com.hrms.cms.dto;

import java.util.List;
import java.util.Map;

/**
 * The queue-scoped assistance payload (Brief 21 §5.3 item 4, "deadline triage").
 *
 * <h2>Why this is a NEW contract and not a signal on {@code AssistanceRailResponse}</h2>
 * Every signal in the rail's contract belongs to ONE complaint: the response is keyed
 * {@code complaintNumber} and the endpoint is {@code GET /rail?complaintId=...}. Deadline triage is a
 * statement about the officer's WHOLE QUEUE — "3 of your 14 cases breach within 48h" names no
 * complaint and would be identical on every screen the officer opened. Folding it in would have meant
 * one of three bad things: inventing a sentinel {@code complaintNumber}, making the rail's
 * {@code complaintNumber} nullable, or shipping a signal whose {@code link} points somewhere the
 * surrounding {@code complaintNumber} contradicts.
 *
 * <p>It would also have broken a pinned contract. {@code AssistanceRailResponse} documents itself as
 * "a contract, not an implementation detail" because a parallel frontend was built against that exact
 * JSON, and its {@code glow} rule is {@code glow == !signals.isEmpty()} BY CONSTRUCTION. A queue signal
 * mixed into {@code signals} would make the per-complaint rail glow for a reason that has nothing to do
 * with the complaint being viewed, and the officer would dismiss it on one screen and meet it again on
 * the next — the rail's dismissal state is keyed by complaint number.
 *
 * <h2>The shape DELIBERATELY mirrors the rail's</h2>
 * {@code glow}/{@code signals}/{@code count}, {@code Signal} with {@code kind}/{@code title}/{@code
 * detail}/{@code count}/{@code link}/{@code params}, {@code empty()} and {@code of()} factories, and the
 * same {@code params}-normalised-to-{@code Map.of()} compact constructor. Not copy-paste inertia: the
 * frontend already has a renderer, an i18n resolver and a dismissal store for that shape, so a
 * structurally identical payload is consumed by extending what exists rather than by writing a second
 * renderer that would drift. The two differences are the ABSENT {@code complaintNumber} — the whole
 * reason this type exists — and the absent {@code tier}, because a queue fact is neither per-user
 * memory nor a precomputed prior and reporting {@code 0} or {@code 1} would be inventing a fact.
 *
 * <p>{@code kind} is a STABLE MACHINE KEY on the same terms as the rail's: the client selects an icon
 * and an i18n key from it, so renaming one is a breaking change even though nothing in Java reads it.
 * The kinds are declared as constants on {@code AssistanceQueueService}.
 *
 * @param glow     true iff {@code signals} is non-empty. Derived here rather than left to the client so
 *                 "glowing" and "has content" cannot drift apart; a rail that glows with nothing behind
 *                 it trains officers to ignore it. Note this is the SHAPE-level rule — whether a
 *                 breaching count deserves a signal at all is the service's floor, applied before this.
 * @param signals  what is worth saying about the queue, possibly empty
 * @param count    {@code signals.size()}, carried explicitly to match the rail's contract
 */
public record AssistanceQueueResponse(
        boolean glow,
        List<Signal> signals,
        int count) {

    /**
     * One thing worth saying about the queue.
     *
     * <h3>{@code count} is the NUMERATOR, and that is a trap worth naming</h3>
     * The frontend's {@code labelParams} supplies {@code params['count']} from the {@code count} field
     * ONLY when {@code params} lacks the key. A signal whose {@code count} meant something other than
     * its {@code {{count}}} placeholder would therefore render a FALSE sentence in all 13 locales while
     * the English {@code title} stayed correct — a bug invisible to anyone testing in English. So
     * {@code count} here is the BREACHING count, the "3", identical to {@code params['count']}, and
     * {@code params['total']} carries the "14" separately. Both are also sent explicitly in
     * {@code params} rather than relying on the fold, so the two can never disagree.
     *
     * <p>Per {@code kind}, the params are:
     * <ul>
     *   <li>{@code queue-deadline-triage} — {@code count} (breaching), {@code total} (queue size),
     *       {@code hours} (the configured window), {@code overdue} (already past due, may be "0")</li>
     * </ul>
     * THE PLACEHOLDER NAMES ARE A CONTRACT: a renamed one leaves a hole in the localised sentence, the
     * client throws the whole sentence away and falls back to the English {@code title}, and the result
     * looks exactly like an untranslated locale rather than like a bug.
     *
     * @param kind   stable machine key; the client's icon and i18n lookup depend on it
     * @param title  short, already-resolved display text, in English
     * @param detail longer text, or null
     * @param count  the NUMERATOR the signal is about — see above — or null when the signal is not a count
     * @param link   a RELATIVE app route, or null. Never absolute: an absolute URL from the server would
     *               be an open-redirect surface, and the client routes internally anyway.
     * @param params interpolation variables for the localised form; never null, possibly empty
     */
    public record Signal(String kind,
                         String title,
                         String detail,
                         Long count,
                         String link,
                         Map<String, String> params) {

        /** Normalises {@code params} to an immutable empty map rather than letting null through. */
        public Signal {
            params = params == null ? Map.of() : Map.copyOf(params);
        }
    }

    /** The degraded response: a queue with nothing to say, which is a normal state and not an error. */
    public static AssistanceQueueResponse empty() {
        return new AssistanceQueueResponse(false, List.of(), 0);
    }

    /** Keeps {@code glow} and {@code count} consistent with {@code signals} by construction. */
    public static AssistanceQueueResponse of(List<Signal> signals) {
        List<Signal> safe = signals == null ? List.of() : List.copyOf(signals);
        return new AssistanceQueueResponse(!safe.isEmpty(), safe, safe.size());
    }
}
