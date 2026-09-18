package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_ELIGIBILITY_ANSWERS", indexes = {
    @Index(name = "idx_cea_complaint", columnList = "complaint_id"),
    @Index(name = "idx_cea_draft", columnList = "draft_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintEligibilityAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false, unique = true)
    private Long complaintId;

    @Column(name = "draft_id", length = 50)
    private String draftId;

    @Column(name = "regulated_entity_id")
    private Long regulatedEntityId;

    // Answers keep the wizard's literal "yes"/"no" strings rather than becoming booleans:
    // for a clause-10 audit, "never asked" (NULL) must stay distinguishable from "answered no".
    @Column(name = "filed_with_re", length = 10)              private String filedWithRe;
    @Column(name = "received_reply", length = 10)             private String receivedReply;
    @Column(name = "sent_reminder", length = 10)              private String sentReminder;
    @Column(name = "is_sub_judice", length = 10)              private String isSubJudice;
    @Column(name = "already_settled", length = 10)            private String alreadySettled;
    @Column(name = "through_advocate", length = 10)           private String throughAdvocate;
    @Column(name = "pending_before_ombudsman", length = 10)   private String pendingBeforeOmbudsman;
    @Column(name = "settled_by_ombudsman", length = 10)       private String settledByOmbudsman;
    @Column(name = "staff_of_re", length = 10)                private String staffOfRe;
    @Column(name = "previously_filed_with_cepc", length = 10) private String previouslyFiledWithCepc;
    @Column(name = "employee_of_re", length = 10)             private String employeeOfRe;
    @Column(name = "employer_relationship", length = 10)      private String employerRelationship;

    // Clause-10 maintainability assessment the officer completes in the RBIO portal. The public
    // wizard never asks these, so they stay NULL until an officer answers them. Deliberately not
    // aliased onto the columns above even where the wording looks close: "already_settled" is about
    // the RE settling the grievance, not a court, and "staff_of_re" is not the same question as
    // "complaint against management".
    @Column(name = "entity_regulated_by_rbi", length = 10)    private String entityRegulatedByRbi;
    @Column(name = "not_directly_addressed_to_ombudsman", length = 10) private String complaintNotDirectlyAddressedToOmbudsman;
    @Column(name = "not_registered_with_entity", length = 10) private String complaintNotRegisteredWithEntity;
    @Column(name = "frivolous_vexatious_threatening", length = 10) private String frivolousVexatiousThreatening;
    @Column(name = "pending_before_court", length = 10)       private String sameGrievancePendingBeforeCourt;
    @Column(name = "settled_before_court", length = 10)       private String sameGrievanceSettledBeforeCourt;
    @Column(name = "complainant_is_advocate", length = 10)    private String complainantIsAdvocate;
    @Column(name = "complaint_against_management", length = 10) private String complaintAgainstManagement;
    @Column(name = "filed_with_cepc_or_rbi", length = 10)     private String complaintFiledWithCepcOrRbi;
    @Column(name = "dispute_between_res", length = 10)        private String disputeBetweenRes;
    @Column(name = "complete_information_unavailable", length = 10) private String completeInformationUnavailable;

    @Column(name = "proposed_complaint_type", length = 100)   private String proposedComplaintType;
    @Column(name = "first_filed_with_re_date")                private LocalDate firstFiledWithReDate;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

    @PrePersist
    protected void onCreate() {
        if (answeredAt == null) answeredAt = LocalDateTime.now();
    }
}
