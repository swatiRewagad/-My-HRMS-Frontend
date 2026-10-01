package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A threaded staff comment on a complaint, with a three-tier readership.
 *
 * <p>NEVER THE COMPLAINANT, AT ANY TIER. {@link #VISIBILITY_PUBLIC} means public-to-staff, not
 * public-to-world. The complainant has no readership of this table whatsoever and no endpoint
 * serves it to a citizen session. The tier only ever NARROWS the staff audience; it can never widen
 * it to include the person the complaint is about. That is enforced in
 * {@code ComplaintCommentService} by an allowlist of staff roles rather than a denylist of citizen
 * ones, so an identity nobody recognised — a citizen-session bearer token, or a dev-header caller
 * with no roles — reads nothing.
 *
 * <p>WHY STRING CONSTANTS AND NOT A JAVA ENUM. The dominant local convention: 24 entities in this
 * package publish {@code public static final String} vocabularies (see
 * {@link ComplaintQueryMessage#KIND_MESSAGE}, {@link ComplaintQuery#STATUS_OPEN}) and only 3 use
 * {@code @Enumerated}. Following the majority also keeps the column readable in the DB and lets a
 * migration seed or repair a row without a matching Java release, which is how every other
 * vocabulary column in this schema is already operated.
 *
 * <p>RESTRICTED LISTS FOLLOW THE {@link ClosureClauseMaster#getRestrictedToRoles()} PRECEDENT:
 * comma-separated, and null-or-blank means "no restriction from this list". Whole-token comparison
 * is mandatory — a {@code contains} test would match {@code ADMIN} inside {@code RBIO_ADMIN} and
 * quietly widen the audience.
 *
 * <p>ONE LEVEL OF NESTING. A reply carries {@link #parentId}; a reply to a reply is refused by the
 * service. A reply also INHERITS its parent's tier and restriction lists, because a PUBLIC reply
 * quoting a RESTRICTED parent would leak the parent's substance to an audience the author excluded.
 */
@Entity
@Table(name = "COMPLAINT_COMMENT", indexes = {
    @Index(name = "idx_ccm_complaint", columnList = "complaintId,createdAt"),
    @Index(name = "idx_ccm_parent", columnList = "parentId"),
    @Index(name = "idx_ccm_author", columnList = "authorUserId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintComment {

    /** Readable only by its author. A note to self. */
    public static final String VISIBILITY_PRIVATE = "PRIVATE";
    /** Readable by its author plus the named roles and named user ids. */
    public static final String VISIBILITY_RESTRICTED = "RESTRICTED";
    /** Readable by every staff user. Never by the complainant. */
    public static final String VISIBILITY_PUBLIC = "PUBLIC";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long complaintId;

    /** Null for a top-level comment; the id of the comment being replied to otherwise. */
    private Long parentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 20)
    private String visibility;

    /** Comma-separated roles, RESTRICTED only. Null or blank imposes no role restriction. */
    @Column(length = 1000)
    private String restrictedToRoles;

    /** Comma-separated user ids, RESTRICTED only. Null or blank imposes no user restriction. */
    @Column(length = 2000)
    private String restrictedToUserIds;

    @Column(nullable = false, length = 100)
    private String authorUserId;

    @Column(nullable = false, length = 200)
    private String authorName;

    @Column(length = 50)
    private String authorRole;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer editCount = 0;

    public boolean isReply() {
        return parentId != null;
    }
}
