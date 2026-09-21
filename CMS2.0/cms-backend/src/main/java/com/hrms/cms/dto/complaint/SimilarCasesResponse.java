package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Similar past complaints, plus which matcher produced them.
 *
 * <p>{@link #matchMethod} used to sit outside the payload as a sibling of the envelope's {@code data},
 * where the standard envelope has no slot for it, so it moves in here. It was also wrong: it was derived
 * as "empty means none, otherwise groq-ai", which labelled keyword-fallback results as AI matches
 * whenever Groq was unconfigured, rate-limited or circuit-broken. It is now reported by the code that
 * actually chose the path.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarCasesResponse {

    public static final String METHOD_AI = "groq-ai";
    public static final String METHOD_KEYWORD = "keyword-fallback";
    public static final String METHOD_NONE = "none";

    @Builder.Default
    private List<PastComplaintSummary> cases = List.of();

    /** One of {@code groq-ai}, {@code keyword-fallback} or {@code none}. */
    private String matchMethod;
}
