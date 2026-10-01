package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintComment;
import com.hrms.cms.repository.ComplaintCommentRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.security.RequestIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Threaded staff comments on a complaint, and the visibility rule that governs them.
 *
 * <p>THE RULE IS ONE METHOD. {@link #canRead(ComplaintComment, RequestIdentity)} is the only place
 * readership is decided; {@link #getThread}, {@link #editComment} and every future caller go through
 * it. Duplicating the tier test per call site is how a privacy control silently diverges.
 *
 * <p>NEVER THE COMPLAINANT, AT ANY TIER. {@code PUBLIC} means public-to-staff. The gate is
 * {@link #requireStaff}, an ALLOWLIST of staff roles — not a denylist of citizen ones. A caller
 * holding no recognised staff role reads nothing and writes nothing, so a citizen session token, an
 * anonymous caller, and a token carrying some future unrelated role are all refused by default
 * rather than by having been enumerated. A denylist would admit anything nobody thought to name.
 *
 * <p>RE USERS ARE NOT STAFF HERE. Regulated-entity users are the counterparty to the complaint, not
 * the handling office; their own notes surface is {@code COMPLAINT_INTERNAL_NOTE}. Admitting them
 * would put RBI-internal deliberation in front of the entity being complained about.
 *
 * <p>EDITABLE, NOT DELETABLE. {@code COMPLAINT_INTERNAL_NOTE} allows an author-only edit and has no
 * delete path anywhere; {@code COMPLAINT_QUERY_MESSAGE} allows neither. Following the more permissive
 * of the two precedents, an author may edit their own comment (with no time lock — these are working
 * notes, not the tamper-evident correspondence record UST857 governs) and nobody may delete. A
 * retraction is made by editing the body, and {@code editCount} records that it happened.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintCommentService {

    /**
     * The staff roles that may participate. Mirrors {@code SecurityConfig.STAFF_ROLES} plus the RBIO
     * and AA roles that route through their own guards, deliberately EXCLUDING every RE_* role.
     */
    private static final Set<String> STAFF_ROLES = Set.of(
            "OFFICER", "DEO", "REVIEWER", "TOLL_FREE_HELPDESK",
            "CRPC_HEAD", "CRPC_ADMIN", "CRPC_INCHARGE",
            "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR",
            "RBIO_ADMIN", "RBIO_OMBUDSMAN",
            "CEPC_DO", "CEPC_OFFICER", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_SUPERVISOR",
            "CEPC_CLOSING_AUTHORITY", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_CONTACT_PERSON", "CEPC_ADMIN",
            "AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN",
            "ADMIN");

    private static final Set<String> VISIBILITIES = Set.of(
            ComplaintComment.VISIBILITY_PRIVATE,
            ComplaintComment.VISIBILITY_RESTRICTED,
            ComplaintComment.VISIBILITY_PUBLIC);

    private final ComplaintCommentRepository commentRepository;
    private final ComplaintRepository complaintRepository;

    /**
     * Whether this caller may read this comment. THE rule — see the class javadoc.
     *
     * <p>A RESTRICTED comment whose lists are both empty stays visible to its author ALONE. That is
     * the opposite of the {@link com.hrms.cms.entity.ClosureClauseMaster} precedent, where a blank
     * list means unrestricted, and it is deliberate: there a blank column means "this clause was
     * never restricted", whereas here the author explicitly chose RESTRICTED, so failing open to
     * every staff user would publish a comment that was asked to be narrow.
     */
    public boolean canRead(ComplaintComment comment, RequestIdentity identity) {
        if (!isStaff(identity)) {
            return false;
        }
        if (identity.getUserId().equals(comment.getAuthorUserId())) {
            return true;
        }
        return switch (comment.getVisibility()) {
            case ComplaintComment.VISIBILITY_PUBLIC -> true;
            case ComplaintComment.VISIBILITY_RESTRICTED -> namedRole(comment, identity)
                    || namedUser(comment, identity);
            default -> false;
        };
    }

    @Transactional(readOnly = true)
    public List<ComplaintComment> getThread(String complaintNumber, RequestIdentity identity) {
        requireStaff(identity);
        Complaint complaint = requireComplaint(complaintNumber);
        return commentRepository.findByComplaintIdOrderByCreatedAtAsc(complaint.getId()).stream()
                .filter(comment -> canRead(comment, identity))
                .toList();
    }

    @Transactional
    public ComplaintComment addComment(String complaintNumber, RequestIdentity identity, String body,
                                       String visibility, String restrictedToRoles,
                                       String restrictedToUserIds) {
        requireStaff(identity);
        Complaint complaint = requireComplaint(complaintNumber);

        return commentRepository.save(ComplaintComment.builder()
                .complaintId(complaint.getId())
                .body(body.trim())
                .visibility(requireVisibility(visibility))
                .restrictedToRoles(blankToNull(restrictedToRoles))
                .restrictedToUserIds(blankToNull(restrictedToUserIds))
                .authorUserId(identity.getUserId())
                .authorName(identity.getDisplayName())
                .authorRole(identity.getPrimaryRole())
                .createdAt(LocalDateTime.now())
                .editCount(0)
                .build());
    }

    /**
     * A reply inherits the parent's tier and restriction lists verbatim, and the caller cannot
     * override them: a PUBLIC reply under a RESTRICTED parent would leak the parent's substance to an
     * audience the parent's author excluded. Replying therefore requires being able to READ the
     * parent, which is the same rule, not a second one.
     */
    @Transactional
    public ComplaintComment addReply(Long parentId, RequestIdentity identity, String body) {
        requireStaff(identity);

        ComplaintComment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found: " + parentId));

        if (!canRead(parent, identity)) {
            throw new SecurityException("You cannot reply to a comment you cannot read");
        }
        if (parent.isReply()) {
            throw new IllegalArgumentException(
                    "A reply cannot be replied to — reply to the parent comment " + parent.getParentId()
                            + " instead");
        }

        return commentRepository.save(ComplaintComment.builder()
                .complaintId(parent.getComplaintId())
                .parentId(parent.getId())
                .body(body.trim())
                .visibility(parent.getVisibility())
                .restrictedToRoles(parent.getRestrictedToRoles())
                .restrictedToUserIds(parent.getRestrictedToUserIds())
                .authorUserId(identity.getUserId())
                .authorName(identity.getDisplayName())
                .authorRole(identity.getPrimaryRole())
                .createdAt(LocalDateTime.now())
                .editCount(0)
                .build());
    }

    /**
     * Only the author may edit, and only the body. The tier is immutable after posting: widening a
     * RESTRICTED comment to PUBLIC would retroactively publish something the readers at the time
     * believed was narrow, and narrowing it cannot un-read it.
     */
    @Transactional
    public ComplaintComment editComment(Long commentId, RequestIdentity identity, String body) {
        requireStaff(identity);

        ComplaintComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found: " + commentId));

        if (!identity.getUserId().equals(comment.getAuthorUserId())) {
            throw new SecurityException("Only the author may edit a comment");
        }

        comment.setBody(body.trim());
        comment.setUpdatedAt(LocalDateTime.now());
        comment.setEditCount(comment.getEditCount() + 1);
        return commentRepository.save(comment);
    }

    private boolean namedRole(ComplaintComment comment, RequestIdentity identity) {
        Set<String> permitted = tokens(comment.getRestrictedToRoles());
        if (permitted.isEmpty()) {
            return false;
        }
        for (String role : identity.getRoles()) {
            if (permitted.contains(normalise(role))) {
                return true;
            }
        }
        return false;
    }

    private boolean namedUser(ComplaintComment comment, RequestIdentity identity) {
        return tokens(comment.getRestrictedToUserIds()).contains(normalise(identity.getUserId()));
    }

    /**
     * Splits the comma-separated column into WHOLE tokens. A substring test would match {@code ADMIN}
     * inside {@code RBIO_ADMIN} and widen every comment restricted to administrators.
     */
    private Set<String> tokens(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.stream(csv.split(","))
                .map(this::normalise)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    /** Keycloak hands roles out both bare and {@code ROLE_}-prefixed; the stored list is bare. */
    private String normalise(String value) {
        String normalised = value.trim().toUpperCase();
        return normalised.startsWith("ROLE_") ? normalised.substring("ROLE_".length()) : normalised;
    }

    private boolean isStaff(RequestIdentity identity) {
        if (identity == null || identity.getUserId() == null || identity.isRe()) {
            return false;
        }
        for (String role : identity.getRoles()) {
            if (STAFF_ROLES.contains(normalise(role))) {
                return true;
            }
        }
        return false;
    }

    private void requireStaff(RequestIdentity identity) {
        if (!isStaff(identity)) {
            throw new SecurityException("Complaint comments are visible only to RBI staff users");
        }
    }

    private String requireVisibility(String visibility) {
        String candidate = visibility == null ? null : visibility.trim().toUpperCase();
        if (candidate == null || !VISIBILITIES.contains(candidate)) {
            throw new IllegalArgumentException(
                    "visibility must be one of PRIVATE, RESTRICTED, PUBLIC");
        }
        return candidate;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Complaint requireComplaint(String complaintNumber) {
        return complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new NoSuchElementException("Complaint not found: " + complaintNumber));
    }
}
