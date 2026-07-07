package com.hrms.cms.service.mre;

public enum MreGround {

    ENTITY_NOT_COVERED("Q13", "Entity not covered under RB-IOS 2026"),
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
