package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintComment;
import com.hrms.cms.repository.ComplaintCommentRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.security.RequestIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers the three-tier readership and the never-the-complainant rule.
 *
 * <p>The behaviour being guarded is that the REFUSAL happens, not that a picker is filtered. A test
 * asserting only "a PUBLIC comment is returned" would pass against a service that returned every
 * comment to everybody, so each tier is asserted from BOTH sides: who sees it and who does not.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComplaintCommentServiceTest {

    private static final String COMPLAINT_NUMBER = "N202627013000001";
    private static final Long COMPLAINT_ID = 4001L;

    @Mock private ComplaintCommentRepository commentRepository;
    @Mock private ComplaintRepository complaintRepository;

    @InjectMocks private ComplaintCommentService service;

    // ── Identities ───────────────────────────────────────────────────────────

    private RequestIdentity staff(String userId, String... roles) {
        return RequestIdentity.builder()
                .userId(userId)
                .displayName(userId)
                .primaryRole(roles.length == 0 ? null : roles[0])
                .roles(Set.of(roles))
                .side("RBI")
                .build();
    }

    /**
     * A citizen session carries a bearer token but NO staff role. The resolver produces exactly this
     * shape for one, so it is the realistic adversary — not a hypothetical "CITIZEN" role that the
     * realm does not define.
     */
    private RequestIdentity citizen() {
        return RequestIdentity.builder()
                .userId("9876500011")
                .displayName("9876500011")
                .roles(Set.of())
                .side("RBI")
                .build();
    }

    private RequestIdentity entityUser() {
        return RequestIdentity.builder()
                .userId("re_nodal_001")
                .displayName("RE Nodal")
                .primaryRole("RE_NODAL_OFFICER")
                .roles(Set.of("RE_NODAL_OFFICER"))
                .side("RE")
                .entityCode("HDFC")
                .build();
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private ComplaintComment comment(Long id, String visibility, String authorUserId,
                                     String roles, String userIds) {
        return ComplaintComment.builder()
                .id(id)
                .complaintId(COMPLAINT_ID)
                .body("body " + id)
                .visibility(visibility)
                .restrictedToRoles(roles)
                .restrictedToUserIds(userIds)
                .authorUserId(authorUserId)
                .authorName(authorUserId)
                .createdAt(LocalDateTime.now())
                .editCount(0)
                .build();
    }

    private void complaintExists() {
        Complaint c = new Complaint();
        c.setId(COMPLAINT_ID);
        c.setComplaintNumber(COMPLAINT_NUMBER);
        when(complaintRepository.findByComplaintNumber(COMPLAINT_NUMBER)).thenReturn(Optional.of(c));
    }

    private void threadContains(ComplaintComment... comments) {
        complaintExists();
        when(commentRepository.findByComplaintIdOrderByCreatedAtAsc(COMPLAINT_ID))
                .thenReturn(List.of(comments));
    }

    private void savesWhateverItIsGiven() {
        when(commentRepository.save(any(ComplaintComment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("PRIVATE")
    class Private_ {

        @Test
        void isVisibleToItsAuthor() {
            ComplaintComment note = comment(1L, ComplaintComment.VISIBILITY_PRIVATE,
                    "rbio_officer_001", null, null);
            threadContains(note);

            assertThat(service.getThread(COMPLAINT_NUMBER, staff("rbio_officer_001", "RBIO_OFFICER")))
                    .containsExactly(note);
        }

        @Test
        void isInvisibleToADifferentStaffUser() {
            threadContains(comment(1L, ComplaintComment.VISIBILITY_PRIVATE,
                    "rbio_officer_001", null, null));

            assertThat(service.getThread(COMPLAINT_NUMBER, staff("rbio_officer_002", "RBIO_OFFICER")))
                    .isEmpty();
        }

        @Test
        void isInvisibleEvenToAnAdmin() {
            // No "read everything" override. An admin who needs the content has the audit trail; a
            // silent admin bypass would make PRIVATE a lie to the author who chose it.
            threadContains(comment(1L, ComplaintComment.VISIBILITY_PRIVATE,
                    "rbio_officer_001", null, null));

            assertThat(service.getThread(COMPLAINT_NUMBER, staff("admin_001", "ADMIN"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("RESTRICTED")
    class Restricted {

        @Test
        void isVisibleToANamedRole() {
            ComplaintComment note = comment(1L, ComplaintComment.VISIBILITY_RESTRICTED,
                    "rbio_officer_001", "RBIO_SUPERVISOR,CEPC_REVIEWER", null);
            threadContains(note);

            assertThat(service.getThread(COMPLAINT_NUMBER, staff("sup_001", "RBIO_SUPERVISOR")))
                    .containsExactly(note);
        }

        @Test
        void isInvisibleToAnUnnamedRole() {
            threadContains(comment(1L, ComplaintComment.VISIBILITY_RESTRICTED,
                    "rbio_officer_001", "RBIO_SUPERVISOR", null));

            assertThat(service.getThread(COMPLAINT_NUMBER, staff("other_001", "RBIO_OFFICER")))
                    .isEmpty();
        }

        @Test
        void isVisibleToANamedUserId() {
            ComplaintComment note = comment(1L, ComplaintComment.VISIBILITY_RESTRICTED,
                    "rbio_officer_001", null, "cepc_do_007,cepc_do_008");
            threadContains(note);

            assertThat(service.getThread(COMPLAINT_NUMBER, staff("cepc_do_007", "CEPC_DO")))
                    .containsExactly(note);
        }

        @Test
        void matchesAPrefixedRoleAgainstABareStoredOne() {
            // Keycloak hands the same role out both ways depending on the path it arrived by.
            ComplaintComment note = comment(1L, ComplaintComment.VISIBILITY_RESTRICTED,
                    "rbio_officer_001", "RBIO_SUPERVISOR", null);
            threadContains(note);

            assertThat(service.getThread(COMPLAINT_NUMBER, staff("sup_001", "ROLE_RBIO_SUPERVISOR")))
                    .containsExactly(note);
        }

        @Test
        void doesNotMatchARoleThatMerelyContainsANamedOne() {
            // A substring test would match ADMIN inside RBIO_ADMIN and widen every restricted comment.
            threadContains(comment(1L, ComplaintComment.VISIBILITY_RESTRICTED,
                    "rbio_officer_001", "ADMIN", null));

            assertThat(service.getThread(COMPLAINT_NUMBER, staff("rbio_admin_001", "RBIO_ADMIN")))
                    .isEmpty();
        }

        @Test
        void withBothListsBlankStaysVisibleToItsAuthorAlone() {
            // Deliberately the OPPOSITE of the ClosureClauseMaster precedent, where blank means
            // unrestricted. The author explicitly chose RESTRICTED; failing open would publish it.
            ComplaintComment note = comment(1L, ComplaintComment.VISIBILITY_RESTRICTED,
                    "rbio_officer_001", "  ", null);
            threadContains(note);

            assertThat(service.getThread(COMPLAINT_NUMBER, staff("rbio_officer_001", "RBIO_OFFICER")))
                    .containsExactly(note);
            assertThat(service.getThread(COMPLAINT_NUMBER, staff("sup_001", "RBIO_SUPERVISOR")))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("PUBLIC means public-to-staff")
    class Public_ {

        @Test
        void isVisibleToEveryStaffUser() {
            ComplaintComment note = comment(1L, ComplaintComment.VISIBILITY_PUBLIC,
                    "rbio_officer_001", null, null);
            threadContains(note);

            for (String role : List.of("RBIO_OFFICER", "RBIO_SUPERVISOR", "CEPC_DO", "AA_REVIEWER",
                    "DEO", "ADMIN")) {
                assertThat(service.getThread(COMPLAINT_NUMBER, staff("u_" + role, role)))
                        .as("role %s should read a PUBLIC comment", role)
                        .containsExactly(note);
            }
        }
    }

    @Nested
    @DisplayName("The complainant reads NOTHING, at every tier")
    class NeverTheCitizen {

        @Test
        void aCitizenSessionSeesZeroCommentsAtEveryTier() {
            threadContains(
                    comment(1L, ComplaintComment.VISIBILITY_PRIVATE, "rbio_officer_001", null, null),
                    comment(2L, ComplaintComment.VISIBILITY_RESTRICTED, "rbio_officer_001",
                            "RBIO_SUPERVISOR", "9876500011"),
                    comment(3L, ComplaintComment.VISIBILITY_PUBLIC, "rbio_officer_001", null, null));

            // Note comment 2 NAMES the citizen's own id in restrictedToUserIds. The staff gate runs
            // FIRST and unconditionally, so a mistaken or malicious entry in that list still cannot
            // hand the complainant a comment.
            assertThatThrownBy(() -> service.getThread(COMPLAINT_NUMBER, citizen()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("RBI staff");
        }

        @Test
        void aCitizenCannotPostOrReplyOrEdit() {
            complaintExists();

            assertThatThrownBy(() -> service.addComment(COMPLAINT_NUMBER, citizen(), "hello",
                    ComplaintComment.VISIBILITY_PUBLIC, null, null))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> service.addReply(1L, citizen(), "hello"))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> service.editComment(1L, citizen(), "hello"))
                    .isInstanceOf(SecurityException.class);

            verify(commentRepository, never()).save(any());
        }

        @Test
        void aPublicCommentIsStillRefusedToAnIdentityWithNoRoles() {
            // canRead is reached directly here so the gate is proven to live in the rule itself, not
            // only in getThread's preamble.
            ComplaintComment note = comment(1L, ComplaintComment.VISIBILITY_PUBLIC,
                    "rbio_officer_001", null, null);

            assertThat(service.canRead(note, citizen())).isFalse();
            assertThat(service.canRead(note, null)).isFalse();
        }

        @Test
        void anEntitySideUserIsNotStaffForThisSurface() {
            // The RE is the counterparty to the complaint. Its own notes surface is
            // COMPLAINT_INTERNAL_NOTE; admitting it here would show RBI deliberation to the entity
            // being complained about.
            ComplaintComment note = comment(1L, ComplaintComment.VISIBILITY_PUBLIC,
                    "rbio_officer_001", null, null);

            assertThat(service.canRead(note, entityUser())).isFalse();
        }
    }

    @Nested
    @DisplayName("Threading, one level only")
    class Threading {

        @Test
        void aReplyNestsUnderItsParent() {
            savesWhateverItIsGiven();
            ComplaintComment parent = comment(1L, ComplaintComment.VISIBILITY_PUBLIC,
                    "rbio_officer_001", null, null);
            when(commentRepository.findById(1L)).thenReturn(Optional.of(parent));

            ComplaintComment reply = service.addReply(1L, staff("sup_001", "RBIO_SUPERVISOR"), "noted");

            assertThat(reply.getParentId()).isEqualTo(1L);
            assertThat(reply.getComplaintId()).isEqualTo(COMPLAINT_ID);
            assertThat(reply.isReply()).isTrue();
        }

        @Test
        void aReplyInheritsTheParentsTierAndLists() {
            savesWhateverItIsGiven();
            ComplaintComment parent = comment(1L, ComplaintComment.VISIBILITY_RESTRICTED,
                    "rbio_officer_001", "RBIO_SUPERVISOR", "cepc_do_007");
            when(commentRepository.findById(1L)).thenReturn(Optional.of(parent));

            ComplaintComment reply = service.addReply(1L, staff("sup_001", "RBIO_SUPERVISOR"), "noted");

            // A PUBLIC reply under a RESTRICTED parent would leak the parent's substance to an
            // audience its author excluded, so the tier is not the replier's to choose.
            assertThat(reply.getVisibility()).isEqualTo(ComplaintComment.VISIBILITY_RESTRICTED);
            assertThat(reply.getRestrictedToRoles()).isEqualTo("RBIO_SUPERVISOR");
            assertThat(reply.getRestrictedToUserIds()).isEqualTo("cepc_do_007");
        }

        @Test
        void aReplyToAReplyIsRefused() {
            ComplaintComment reply = comment(2L, ComplaintComment.VISIBILITY_PUBLIC,
                    "sup_001", null, null);
            reply.setParentId(1L);
            when(commentRepository.findById(2L)).thenReturn(Optional.of(reply));

            assertThatThrownBy(() -> service.addReply(2L, staff("other_001", "RBIO_OFFICER"), "again"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be replied to");

            verify(commentRepository, never()).save(any());
        }

        @Test
        void cannotReplyToACommentTheCallerCannotRead() {
            ComplaintComment parent = comment(1L, ComplaintComment.VISIBILITY_PRIVATE,
                    "rbio_officer_001", null, null);
            when(commentRepository.findById(1L)).thenReturn(Optional.of(parent));

            assertThatThrownBy(() -> service.addReply(1L, staff("sup_001", "RBIO_SUPERVISOR"), "peek"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("cannot read");

            verify(commentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Posting and editing")
    class Writing {

        @Test
        void attributionComesFromTheResolvedIdentityNotTheRequest() {
            savesWhateverItIsGiven();
            complaintExists();

            service.addComment(COMPLAINT_NUMBER, staff("rbio_officer_001", "RBIO_OFFICER"),
                    "  spaced  ", "public", null, null);

            ArgumentCaptor<ComplaintComment> saved = ArgumentCaptor.forClass(ComplaintComment.class);
            verify(commentRepository).save(saved.capture());
            assertThat(saved.getValue().getAuthorUserId()).isEqualTo("rbio_officer_001");
            assertThat(saved.getValue().getAuthorRole()).isEqualTo("RBIO_OFFICER");
            assertThat(saved.getValue().getBody()).isEqualTo("spaced");
            assertThat(saved.getValue().getVisibility()).isEqualTo(ComplaintComment.VISIBILITY_PUBLIC);
        }

        @Test
        void refusesAnUnknownTier() {
            complaintExists();

            assertThatThrownBy(() -> service.addComment(COMPLAINT_NUMBER,
                    staff("rbio_officer_001", "RBIO_OFFICER"), "hi", "WORLD_READABLE", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PRIVATE, RESTRICTED, PUBLIC");
        }

        @Test
        void refusesAMissingComplaint() {
            when(complaintRepository.findByComplaintNumber(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getThread("N000000000000000",
                    staff("rbio_officer_001", "RBIO_OFFICER")))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void onlyTheAuthorMayEdit() {
            ComplaintComment note = comment(1L, ComplaintComment.VISIBILITY_PUBLIC,
                    "rbio_officer_001", null, null);
            when(commentRepository.findById(1L)).thenReturn(Optional.of(note));

            assertThatThrownBy(() -> service.editComment(1L, staff("sup_001", "RBIO_SUPERVISOR"), "mine"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Only the author");
        }

        @Test
        void theAuthorsEditBumpsTheCounterAndLeavesTheTierAlone() {
            savesWhateverItIsGiven();
            ComplaintComment note = comment(1L, ComplaintComment.VISIBILITY_RESTRICTED,
                    "rbio_officer_001", "RBIO_SUPERVISOR", null);
            when(commentRepository.findById(1L)).thenReturn(Optional.of(note));

            ComplaintComment edited = service.editComment(
                    1L, staff("rbio_officer_001", "RBIO_OFFICER"), "corrected");

            assertThat(edited.getBody()).isEqualTo("corrected");
            assertThat(edited.getEditCount()).isEqualTo(1);
            assertThat(edited.getUpdatedAt()).isNotNull();
            // Widening after the fact would retroactively publish something readers believed narrow.
            assertThat(edited.getVisibility()).isEqualTo(ComplaintComment.VISIBILITY_RESTRICTED);
            assertThat(edited.getRestrictedToRoles()).isEqualTo("RBIO_SUPERVISOR");
        }

        @Test
        void thereIsNoDeletePath() {
            // COMPLAINT_QUERY_MESSAGE has no delete and COMPLAINT_INTERNAL_NOTE has no delete; this
            // follows suit. Asserted on the type so adding one is a deliberate, visible change.
            assertThat(ComplaintCommentService.class.getDeclaredMethods())
                    .noneMatch(m -> m.getName().toLowerCase().contains("delete")
                            || m.getName().toLowerCase().contains("remove"));
            verify(commentRepository, never()).delete(any());
            verify(commentRepository, never()).deleteById(anyLong());
        }
    }
}
