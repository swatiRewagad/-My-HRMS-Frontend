package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * One answer to one maintainability question, for a submitted complaint.
 *
 * <p>Fills the gap between the two places eligibility answers already live, neither of which can hold
 * these: {@link EligibilityQuestionMaster} is reference data, and
 * {@link ComplaintDraft#getEligibilityAnswersJson()} is scoped to a draft and is gone once the complaint is
 * registered.
 *
 * <p><b>Not a foreign key into {@link EligibilityQuestionMaster}, deliberately.</b> That master holds the
 * 14 questions of the CITIZEN intake wizard ({@code filedWithRE}, {@code isSubJudice}, …). The officer's
 * maintainability panel asks a different and larger set of 20 ({@code entityRegulatedByRbi},
 * {@code complaintNotDirectlyAddressedToOmbudsman}, …) whose keys do not overlap. Constraining to the
 * master would reject every answer the panel submits.
 *
 * <p>Key-value rather than 20 columns because the question set belongs to the officer panel and is revised
 * whenever the scheme is: a new question is a row here, but would be a schema migration there.
 *
 * <p>Two typed answer columns instead of one stringly-typed one. Most questions are yes/no, but two
 * ({@code firstFiledWithREDate}, {@code replyDate}) are dates that get compared against statutory windows,
 * and storing those as text is how a date comparison silently becomes a lexicographic one.
 */
@Entity
@Table(name = "COMPLAINT_ELIGIBILITY_ANSWERS",
        uniqueConstraints = @UniqueConstraint(name = "uk_eligibility_answer_question",
                columnNames = {"complaintNumber", "questionKey"}),
        indexes = @Index(name = "idx_eligibility_answer_complaint", columnList = "complaintNumber"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintEligibilityAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String complaintNumber;

    /** The panel's own key, e.g. {@code complaintNotDirectlyAddressedToOmbudsman}. */
    @Column(nullable = false, length = 100)
    private String questionKey;

    private Boolean booleanAnswer;

    private LocalDate dateAnswer;
}
