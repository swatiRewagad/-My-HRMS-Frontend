package com.hrms.cms.service.mre;

/**
 * Objective maintainability grounds (Q13/Q16/Q17).
 *
 * The descriptions deliberately carry NO Scheme year. ENTITY_NOT_COVERED used to read "not covered
 * under RB-IOS 2026", which named a Scheme that is not in force — the current one is the Reserve Bank -
 * Integrated Ombudsman Scheme, 2021. This text reaches a citizen as the stated reason their complaint
 * is non-maintainable, so a wrong year there misstates the legal basis for denying statutory recourse.
 *
 * A year is not hardcoded back in its place either: an enum constant is initialised long before any
 * configuration is available, so it cannot read cms.eligibility.scheme-name. Callers that need to cite
 * the Scheme inject that property and compose the sentence themselves; see MaintainabilityRulesEngine.
 */
public enum MreGround {

    ENTITY_NOT_COVERED("Q13", "Entity not covered under the Scheme"),
    NO_PRIOR_RE_COMPLAINT("Q16", "No prior complaint to the Regulated Entity"),
    FILED_BEFORE_WINDOW("Q17", "Filed before the RE response window has elapsed"),
    FILED_BEYOND_DEADLINE("Q16/Q17", "Filed beyond 90 days of timeline expiry or last RE communication"),
    RE_COMPLAINT_BEYOND_LIMITATION("Q16", "Complaint to RE made after Limitation Act 1963 period"),
    SAME_GRIEVANCE_PENDING("Q16", "Same grievance already pending or decided by Ombudsman or court/tribunal");

    private final String clause;
    private final String description;

    MreGround(String clause, String description) {
        this.clause = clause;
        this.description = description;
    }

    public String getClause() { return clause; }
    public String getDescription() { return description; }
}
