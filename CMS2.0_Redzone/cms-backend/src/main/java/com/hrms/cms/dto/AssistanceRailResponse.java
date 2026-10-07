package com.hrms.cms.dto;

import java.util.List;
import java.util.Map;

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
 * {@code count} and {@code link}. {@code Signal.params} deliberately does NOT join that list: it is
 * normalised to {@code {}} in the constructor, so the client never has to distinguish "no variables"
 * from "variables absent".
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
     * <h3>Why {@code params} exists alongside an already-English {@code title}</h3>
     * CMS serves 13 languages, and {@code title}/{@code detail} are resolved ENGLISH prose. The client
     * therefore translates from {@code kind} — which is exactly what {@code kind} being a stable
     * machine key is for. But three signals interpolate a VARIABLE that exists nowhere else in the
     * payload: the section name, the humanised age, and the closure clause. {@code count} covers the
     * numeric ones and nothing else, so a client rebuilding "You were last in {{section}}" in Marathi
     * had no way to obtain the section without parsing the English sentence back apart.
     *
     * <p>{@code params} carries those interpolation variables as strings, keyed by the placeholder
     * name used in the {@code assistance.signal.*} i18n values seeded by
     * {@link com.hrms.cms.config.AssistanceRailTranslationSeeder}. THE PLACEHOLDER NAMES ARE A
     * CONTRACT — a renamed one leaves a hole in the localised sentence, and the client then throws the
     * whole sentence away and falls back to this English {@code title}, which looks exactly like the
     * locale never having been translated rather than like a bug.
     *
     * <p>The i18n key itself is {@code 'assistance.signal.' + kind} with the {@code kind} VERBATIM,
     * hyphens and all — {@code assistance.signal.last-section}, not {@code ...last_section}. An earlier
     * revision of this comment left that ambiguous and a seeder written to the underscored spelling
     * would have missed every lookup. The client does not actually concatenate: it holds a hardcoded
     * {@code KIND_LABEL_KEYS} map, hardcoded so that a seventh {@code kind} added here cannot mint a
     * plausible-looking key nobody has translated. That map, not this comment, is the authority on
     * spelling, and adding a kind means editing it and the seeder together. Per {@code kind}, the
     * PARAMS are:
     * <ul>
     *   <li>{@code unsaved-draft} — empty; the preview is {@code detail}, which is the officer's own
     *       text and must NOT be translated</li>
     *   <li>{@code last-section} — {@code section}</li>
     *   <li>{@code last-viewed} — {@code age}</li>
     *   <li>{@code complainant-history} — {@code count}</li>
     *   <li>{@code entity-clause-precedent} — {@code count}, {@code clause}</li>
     *   <li>{@code category-closure-time} — {@code days}, {@code sample}</li>
     *   <li>{@code next-action} — {@code action}, {@code count}, {@code total}, {@code percent}. The
     *       only kind that sends {@code count} EXPLICITLY rather than letting the client fold it in
     *       from the {@code count} field, and the reason is a trap worth naming: the client supplies
     *       {@code params['count']} from {@code count} only when params lacks it, so a kind whose
     *       {@code count} field meant something different from its {@code {{count}}} placeholder would
     *       render a false sentence in all 13 locales while the English {@code title} stayed correct.
     *       Here both are the numerator. {@code action} is a MACHINE key and is never translated.</li>
     * </ul>
     *
     * <p>PURELY ADDITIVE. {@code title} and {@code detail} keep their English values: the field is a
     * second way to render the same signal, not a replacement, so an existing consumer that reads
     * {@code title} is unaffected and a client that ignores {@code params} still shows English rather
     * than nothing. {@code params} is also never null — an empty map, so the client can index it
     * unconditionally, where a null would be the one shape that throws on the happy path.
     *
     * <p>{@code age} is a resolved English phrase ("3 days ago") rather than a timestamp, because that
     * is the value the English title already interpolates and this field's job is to let the client
     * reproduce the SAME sentence. A client wanting to format the age itself has
     * {@code detail}, which for {@code last-viewed} is the ISO instant.
     *
     * @param tier   0 for per-user memory, 1 for a precomputed prior. There is no tier 2 — see
     *               {@link com.hrms.cms.service.AssistanceRailService}.
     * @param kind   stable machine key; the client's icon and i18n lookup depend on it
     * @param title  short, already-resolved display text, in English
     * @param detail longer text, or null
     * @param count  a number the signal is about, or null when the signal is not a count
     * @param link   a RELATIVE app route, or null. Never absolute: an absolute URL from the server
     *               would be an open-redirect surface, and the client routes internally anyway.
     * @param params interpolation variables for the localised form of {@code title}/{@code detail};
     *               never null, possibly empty
     */
    public record Signal(int tier,
                         String kind,
                         String title,
                         String detail,
                         Long count,
                         String link,
                         Map<String, String> params) {

        /** Normalises {@code params} to an immutable empty map rather than letting null through. */
        public Signal {
            params = params == null ? Map.of() : Map.copyOf(params);
        }

        /** A tier-0 memory signal with no interpolated variable. */
        public static Signal memory(String kind, String title, String detail, String link) {
            return new Signal(0, kind, title, detail, null, link, Map.of());
        }

        /** A tier-0 memory signal whose text interpolates something the client must re-render. */
        public static Signal memory(String kind, String title, String detail, String link,
                                    Map<String, String> params) {
            return new Signal(0, kind, title, detail, null, link, params);
        }

        /** A tier-1 prior, which always carries the number it is reporting. */
        public static Signal prior(String kind, String title, String detail, long count, String link) {
            return new Signal(1, kind, title, detail, count, link, Map.of());
        }

        /** A tier-1 prior that also carries its interpolation variables. */
        public static Signal prior(String kind, String title, String detail, long count, String link,
                                   Map<String, String> params) {
            return new Signal(1, kind, title, detail, count, link, params);
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
