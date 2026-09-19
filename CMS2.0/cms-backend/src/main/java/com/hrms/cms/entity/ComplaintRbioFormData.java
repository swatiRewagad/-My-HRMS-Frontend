package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_RBIO_FORM_DATA", indexes = {
    @Index(name = "idx_crfd_draft", columnList = "draft_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintRbioFormData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false, unique = true)
    private Long complaintId;

    @Column(name = "draft_id", length = 50)
    private String draftId;

    // Basic details the public portal never asks for - the RBIO officer keys these in, or they
    // arrive pre-filled from the CRPC draft (EmailDraft) when the complaint came in by mail.
    @Column(name = "receipt_date")                          private LocalDate receiptDate;
    @Column(name = "mode_of_receipt", length = 50)          private String modeOfReceipt;
    @Column(name = "comments", length = 4000)               private String comments;
    @Column(name = "complaint_cpgram", length = 10)         private String complaintCpgram;
    @Column(name = "cpgram_number", length = 100)           private String cpgramNumber;

    // Entity/branch attributes not present on COMPLAINTS or REGULATED_ENTITIES.
    @Column(name = "module_name", length = 100)             private String moduleName;
    @Column(name = "entity_country", length = 100)          private String entityCountry;
    @Column(name = "branch_center_name", length = 200)      private String branchCenterName;
    @Column(name = "other_entity_name", length = 300)       private String otherEntityName;
    @Column(name = "registration_with_rbi_date")            private LocalDate registrationWithRbiDate;

    @Column(name = "complaint_registration_date_valid", length = 10) private String complaintRegistrationDateValid;
    @Column(name = "date_of_filing_complaint")              private LocalDate dateOfFilingComplaint;

    @Column(name = "legal_case_filed", length = 10)         private String legalCaseFiled;
    @Column(name = "legal_filing_date")                     private LocalDate legalFilingDate;
    @Column(name = "pre_enquiry_received", length = 10)     private String preEnquiryReceived;
    @Column(name = "high_priority_complaint", length = 10)  private String highPriorityComplaint;
    @Column(name = "loan_disposal_amount", precision = 15, scale = 2) private BigDecimal loanDisposalAmount;

    @Column(name = "vernacular_language", length = 100)     private String vernacularLanguage;

    // Distinct from "comments" above: that one backs Basic Details, this one backs the Additional
    // Information section further down the same form. They were previously aliased onto one column,
    // so editing either silently overwrote the other.
    @Column(name = "additional_comments", length = 4000)    private String additionalComments;

    @Column(name = "complaint_regarding_pension", length = 10) private String complaintRegardingPension;
    @Column(name = "atm_credit_debit_card", length = 10)    private String atmCreditDebitCard;
    @Column(name = "scheme_flag", length = 50)              private String schemeFlag;
    @Column(name = "rbo_cgpc_old", length = 100)            private String rboCgpcOld;
    @Column(name = "grounds_flag", length = 100)            private String groundsFlag;

    @Column(name = "free_marked_complaint", length = 10)    private String freeMarkedComplaint;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
