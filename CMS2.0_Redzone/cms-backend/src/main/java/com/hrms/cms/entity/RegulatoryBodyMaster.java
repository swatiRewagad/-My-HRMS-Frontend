package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An external regulator a complaint outside RBI's remit may be referred to (UST766).
 *
 * <p><b>This master did not exist in any form.</b> The forwarding screen called
 * {@code GET /api/v1/master-data/regulatory-bodies}, which no controller implements — and the real master
 * controller is mounted at {@code /api/v1/masters}, so even the prefix was wrong. The call was wrapped in
 * {@code catchError(() => of([]))}, so the 404 rendered as a permanently empty dropdown while the screen told
 * the officer "Only bodies from the validated master list can be selected".
 *
 * <p><b>{@link #emailVerified} is the point of the story.</b> UST766 restricts forwarding to bodies WITH
 * VERIFIED EMAIL IDS. The flag is nullable and must equal 'Y' to permit a forward, so it fails closed: a body
 * whose address nobody has confirmed cannot receive a citizen's complaint, and an unseeded master forwards to
 * nobody rather than to a guessed address. Every seeded body starts 'N' — seeding 'Y' would assert a
 * verification that never happened.
 */
@Entity
@Table(name = "REGULATORY_BODY_MASTER", indexes = {
    @Index(name = "idx_reg_body_code", columnList = "bodyCode"),
    @Index(name = "idx_reg_body_active", columnList = "isActive")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegulatoryBodyMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "BODY_CODE", nullable = false, length = 30)
    private String bodyCode;

    @Column(name = "BODY_NAME", nullable = false, length = 250)
    private String bodyName;

    @Column(name = "CONTACT_EMAIL", length = 320)
    private String contactEmail;

    /** 'Y' only once an administrator has confirmed the address. See the class comment. */
    @Column(name = "EMAIL_VERIFIED", length = 1)
    private String emailVerified;

    @Column(name = "VERIFIED_BY", length = 200)
    private String verifiedBy;

    @Column(name = "VERIFIED_AT")
    private LocalDateTime verifiedAt;

    @Column(name = "CONTACT_PHONE", length = 30)
    private String contactPhone;

    @Column(name = "ADDRESS", length = 500)
    private String address;

    @Column(name = "JURISDICTION", length = 250)
    private String jurisdiction;

    @Column(name = "IS_ACTIVE", length = 1)
    private String isActive;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    public boolean active() {
        return "Y".equalsIgnoreCase(isActive);
    }

    /**
     * Whether this body may lawfully receive a referral.
     *
     * <p>Requires BOTH a verified flag and an actual address: a body flagged verified with a null email is a
     * data error, and treating it as forwardable would queue an awareness email to nobody while telling the
     * citizen their complaint had been referred.
     */
    public boolean forwardable() {
        return active() && "Y".equalsIgnoreCase(emailVerified)
                && contactEmail != null && !contactEmail.isBlank();
    }
}
