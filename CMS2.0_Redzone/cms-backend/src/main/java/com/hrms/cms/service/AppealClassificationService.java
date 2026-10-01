package com.hrms.cms.service;

import com.hrms.cms.entity.ClosureClauseMaster;
import com.hrms.cms.entity.ClosureClauseMaster.AppealParty;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ClosureClauseMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Decides whether an escalation is an APPEAL or a REPRESENTATION, from the parent complaint's
 * closure clause and the party raising it.
 *
 * This replaces two things that were both wrong. Classification used to be taken from a request
 * parameter, defaulting to "APPEAL" (AppealController), so the client decided a legal question; and
 * the only server-side "derivation" anywhere was a heuristic on whether the complaint happened to
 * carry advisory text, which has no basis in the Scheme.
 *
 * The ruling encoded here: a COMPLAINANT may appeal a closure under 15(1)(a) or 15(1)(b); a
 * regulated ENTITY may appeal only under 15(1)(b). Everything else is a REPRESENTATION. The clause
 * data lives in CLOSURE_CLAUSE_MASTER, so changing the mapping is a data change, not a release.
 *
 * FAIL CLOSED: if the parent's clause is missing, or is not present in the Clause Master for the
 * applicable scheme version, this throws rather than guessing. Classifying an escalation wrongly
 * would deny a citizen statutory recourse, which is strictly worse than asking them to retry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppealClassificationService {

    public static final String APPEAL = "APPEAL";
    public static final String REPRESENTATION = "REPRESENTATION";

    private final ClosureClauseMasterRepository clauseRepository;

    /** Source of truth for the scheme; never hardcoded at a call site. */
    @Value("${cms.eligibility.scheme-version:RBIOS_2021}")
    private String defaultSchemeVersion;

    /**
     * Raised when classification cannot be determined. Deliberately NOT an IllegalArgumentException:
     * this is a configuration/data problem that an AA Admin must resolve, and it must not be
     * mistaken for ordinary field validation.
     */
    public static class UnmappedClauseException extends RuntimeException {
        private final String clauseCode;
        private final String schemeVersion;

        public UnmappedClauseException(String message, String clauseCode, String schemeVersion) {
            super(message);
            this.clauseCode = clauseCode;
            this.schemeVersion = schemeVersion;
        }

        public String getClauseCode() {
            return clauseCode;
        }

        public String getSchemeVersion() {
            return schemeVersion;
        }
    }

    /**
     * Classifies an escalation against the parent complaint.
     *
     * @param complaint the parent, already loaded
     * @param party     who is raising it — appealability differs between complainant and entity
     * @return APPEAL or REPRESENTATION
     */
    public String classify(Complaint complaint, AppealParty party) {
        ClosureClauseMaster clause = resolveClause(complaint);
        boolean appealable = clause.isAppealableBy(party);

        log.debug("Classified complaint {} clause {} for {} as {}",
                complaint.getComplaintNumber(), clause.getClauseCode(), party,
                appealable ? APPEAL : REPRESENTATION);

        return appealable ? APPEAL : REPRESENTATION;
    }

    /** True when the given party may appeal this complaint's closure. */
    public boolean isAppealable(Complaint complaint, AppealParty party) {
        return APPEAL.equals(classify(complaint, party));
    }

    /**
     * The Clause Master row governing this complaint's closure.
     *
     * The scheme version comes from the complaint when it carries one, so a complaint filed under an
     * earlier Scheme keeps being judged under that Scheme.
     */
    public ClosureClauseMaster resolveClause(Complaint complaint) {
        String clauseCode = complaint.getClosureClause();
        String schemeVersion = applicableSchemeVersion(complaint);

        if (clauseCode == null || clauseCode.isBlank()) {
            throw new UnmappedClauseException(
                    "Complaint " + complaint.getComplaintNumber()
                            + " has no closure clause recorded, so it cannot be classified as an appeal"
                            + " or a representation.",
                    null, schemeVersion);
        }

        LocalDate onDate = complaint.getCreatedAt() != null
                ? complaint.getCreatedAt().toLocalDate()
                : LocalDate.now();

        return clauseRepository.findInForce(schemeVersion, clauseCode.trim(), onDate)
                .orElseThrow(() -> new UnmappedClauseException(
                        "Closure clause '" + clauseCode + "' is not configured for scheme "
                                + schemeVersion + ". An AA Admin must add it to the Clause Master"
                                + " before this complaint can be escalated.",
                        clauseCode, schemeVersion));
    }

    /**
     * The scheme version applicable to a complaint.
     *
     * Falls back to the configured current scheme only when the complaint carries none — never to a
     * compiled-in literal.
     */
    public String applicableSchemeVersion(Complaint complaint) {
        return Optional.ofNullable(complaint.getSchemeVersion())
                .filter(v -> !v.isBlank())
                .map(String::trim)
                .orElse(defaultSchemeVersion);
    }
}
