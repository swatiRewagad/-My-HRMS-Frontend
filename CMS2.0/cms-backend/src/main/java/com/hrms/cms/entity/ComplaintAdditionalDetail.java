package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_ADDITIONAL_DETAILS", indexes = {
    @Index(name = "idx_cad_complaint", columnList = "complaint_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintAdditionalDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false, unique = true)
    private Long complaintId;

    @Column(name = "draft_id", length = 50)
    private String draftId;

    @Column(name = "age")
    private Integer age;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "complainant_category", length = 50)
    private String complainantCategory;

    @Column(name = "is_complainant_self", length = 10)
    private String isComplainantSelf;

    @Column(name = "organization_name", length = 300)
    private String organizationName;

    @Column(name = "org_landline", length = 30)
    private String orgLandline;

    @Column(name = "has_account_with_re", length = 10)
    private String hasAccountWithRe;

    @Column(name = "account_type", length = 200)
    private String accountType;

    @Column(name = "savings_account_number", length = 50)
    private String savingsAccountNumber;

    @Column(name = "atm_debit_card_number", length = 50)
    private String atmDebitCardNumber;

    @Column(name = "card_number", length = 50)
    private String cardNumber;

    @Column(name = "credit_card_number", length = 50)
    private String creditCardNumber;

    @Column(name = "is_credit_card_complaint", length = 10)
    private String isCreditCardComplaint;

    @Column(name = "loan_account_number", length = 50)
    private String loanAccountNumber;

    @Column(name = "is_wallet_complaint", length = 10)
    private String isWalletComplaint;

    @Column(name = "wallet_name", length = 200)
    private String walletName;

    @Column(name = "is_business_correspondent", length = 10)
    private String isBusinessCorrespondent;

    @Column(name = "transaction_ref_number", length = 100)
    private String transactionRefNumber;

    @Column(name = "dispute_date")
    private LocalDate disputeDate;

    @Column(name = "compensation_sought", precision = 15, scale = 2)
    private BigDecimal compensationSought;

    @Column(name = "received_reply_from_entity", length = 10)
    private String receivedReplyFromEntity;

    @Column(name = "reply_date")
    private LocalDate replyDate;

    @Column(name = "reminder_date")
    private LocalDate reminderDate;

    @Column(name = "sub_category1", length = 200)
    private String subCategory1;

    @Column(name = "sub_category2", length = 200)
    private String subCategory2;

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
