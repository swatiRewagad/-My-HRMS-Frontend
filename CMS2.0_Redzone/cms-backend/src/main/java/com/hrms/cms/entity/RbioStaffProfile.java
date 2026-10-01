package com.hrms.cms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The RBIO-side profile of a staff member whose identity lives in Keycloak (UST443-448, UST629-631).
 *
 * <h2>Why this table exists at all</h2>
 * Before this there was no RBIO staff master anywhere in the system. The only officer-shaped table is
 * cms-workflow-service's {@code WF_OFFICER_POOL}, which holds nine columns and none of the four things
 * the stories need: a designation, an office posting that is a real key rather than free text, leave
 * FROM/TO dates, and the reviewer closure-eligibility flag.
 *
 * <p>Keycloak user attributes were the obvious alternative and are deliberately rejected. Writing an
 * attribute is a read-modify-write of the entire user representation, so two concurrent admin sessions
 * silently lose each other's edits, and Keycloak 26 drops unknown attributes unless the realm's
 * unmanagedAttributePolicy permits them. Closure eligibility decides whether a reviewer may finally
 * close a citizen's complaint; it must not hinge on either of those behaviours.
 *
 * <h2>What this deliberately does NOT own</h2>
 * {@code IS_ON_LEAVE} stays authoritative in {@code WF_OFFICER_POOL}, and workload and the round-robin
 * pointer stay there too. Mirroring the leave boolean here would create two answers to "is this officer
 * on leave", which is precisely the divergence that had two services disagreeing about the RE response
 * window. This table records the leave DATES and the attribution that the bare boolean cannot express.
 */
@Entity
@Table(name = "RBIO_STAFF_PROFILE",
        uniqueConstraints = @UniqueConstraint(name = "uk_rsp_user_unique", columnNames = "USER_ID"),
        indexes = {
                @Index(name = "idx_rsp_office", columnList = "OFFICE_CODE"),
                @Index(name = "idx_rsp_role", columnList = "PRIMARY_ROLE"),
                @Index(name = "idx_rsp_closure", columnList = "CLOSURE_ELIGIBLE")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RbioStaffProfile {

    private static final String YES = "Y";
    private static final String NO = "N";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The Keycloak {@code preferred_username}. The join key to the identity provider — no credential is
     * ever stored here.
     */
    @Column(name = "USER_ID", nullable = false, length = 200)
    private String userId;

    @Column(name = "DISPLAY_NAME", length = 250)
    private String displayName;

    @Column(name = "EMAIL", length = 250)
    private String email;

    /**
     * UST443/444. Free text against no master: RBI designations are not enumerated anywhere in this
     * system, and inventing a DESIGNATION_MASTER here would present a guess as reference data.
     */
    @Column(name = "DESIGNATION", length = 150)
    private String designation;

    /**
     * The office this officer is posted to, holding {@code OFFICE_CODE_MASTER.OFFICE_CODE}.
     *
     * <p>The same key {@code COMPLAINTS.rbio_office_code} uses, so a caller's office can be compared
     * with a complaint's office directly. Deliberately NOT {@code WF_OFFICER_POOL.REGIONAL_OFFICE},
     * which is free-text with no FK and is spelled inconsistently.
     */
    @Column(name = "OFFICE_CODE", length = 10)
    private String officeCode;

    /** The RBIO rank, e.g. {@code RBIO_DEALING_OFFICIAL}. Roles themselves remain in Keycloak. */
    @Column(name = "PRIMARY_ROLE", length = 50)
    private String primaryRole;

    @Column(name = "LEAVE_FROM_DATE")
    private LocalDate leaveFromDate;

    @Column(name = "LEAVE_TO_DATE")
    private LocalDate leaveToDate;

    /** Who set the leave. Resolved from the JWT by the service, never accepted from the request body. */
    @Column(name = "LEAVE_SET_BY", length = 200)
    private String leaveSetBy;

    @Column(name = "LEAVE_SET_AT")
    private LocalDateTime leaveSetAt;

    /**
     * NOT AN AUTHORIZATION SOURCE. Retained for reporting only.
     *
     * <h2>Why this must not be checked</h2>
     * UST629-631 describes closure eligibility as a per-user flag an Ombudsman Admin toggles. Under the
     * delegated UAM model that is the wrong home for it: user administration lives in the SSO, and the
     * permission to take a final closure decision is granted there as the
     * {@link com.hrms.cms.security.CmsAuthority#RBIO_COMPLAINT_CLOSE_FINAL} authority.
     *
     * <p>If this column were ALSO consulted, there would be two answers to "may this reviewer close a
     * complaint" and they would eventually disagree — and the disagreement is invisible until a reviewer is
     * either wrongly blocked or, far worse, wrongly permitted to close a citizen's complaint. That is the
     * same split-brain that had two services disagreeing about the RE response window.
     *
     * <p>So: the SSO authority is the control. This column is left in place because the table is shared and
     * ddl-auto never reverts, and because an operational report of "who was marked eligible" is still
     * useful — but {@link #closureEligibleFlag()} is deliberately NOT used by any guard. UST629's "defaults
     * to No" is satisfied by the SSO simply not granting the authority.
     */
    @Column(name = "CLOSURE_ELIGIBLE", nullable = false, length = 1)
    private String closureEligible;

    @Column(name = "IS_ACTIVE", nullable = false, length = 1)
    private String isActive;

    @Column(name = "CREATED_BY", length = 200)
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_BY", length = 200)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /**
     * Defaults applied in Java as well as in the DDL.
     *
     * <p>The column default only fires when the column is omitted from the INSERT, and Hibernate always
     * includes every mapped column — so a null {@code closureEligible} would be written as NULL and read
     * back as "not eligible" only by accident. Setting it here makes the deny explicit.
     */
    @PrePersist
    void onCreate() {
        if (closureEligible == null) {
            closureEligible = NO;
        }
        if (isActive == null) {
            isActive = YES;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** True only on an explicit "Y". Anything else — null, blank, junk — denies. */
    public boolean closureEligibleFlag() {
        return YES.equalsIgnoreCase(closureEligible);
    }

    public boolean activeFlag() {
        return YES.equalsIgnoreCase(isActive);
    }

    /**
     * Whether this officer is on leave on the given day, per the recorded date range.
     *
     * <p>A null {@code from} means no leave is recorded. A null {@code to} means open-ended leave, which
     * is treated as still on leave rather than as "not on leave" — an admin who set a start date and no
     * end date meant the officer to be away, and assuming the opposite would hand them work.
     */
    public boolean onLeaveOn(LocalDate day) {
        if (leaveFromDate == null || day == null) {
            return false;
        }
        if (day.isBefore(leaveFromDate)) {
            return false;
        }
        return leaveToDate == null || !day.isAfter(leaveToDate);
    }
}
