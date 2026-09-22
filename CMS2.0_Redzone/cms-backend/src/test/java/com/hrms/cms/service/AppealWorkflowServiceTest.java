package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealTimeline;
import com.hrms.cms.entity.ClosureClauseMaster.AppealParty;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.repository.AppealTimelineRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.security.AaAccessDeniedException;
import com.hrms.cms.security.AaIdentityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AppealWorkflowService.
 */
@ExtendWith(MockitoExtension.class)
class AppealWorkflowServiceTest {

    @Mock private AppealRepository appealRepository;
    @Mock private AppealTimelineRepository appealTimelineRepository;
    @Mock private AppealEligibilityService eligibilityService;
    @Mock private ComplaintRepository complaintRepository;
    @Mock private KeycloakUserService keycloakUserService;
    @Mock private FileStorageService fileStorageService;
    @Mock private AaIdentityResolver aaIdentityResolver;
    @Mock private AppealClassificationService classificationService;

    // Collaborators added when the workflow moved onto the real assignment engine and gained
    // notification, hearing and outbox seams. Without these mocks @InjectMocks leaves them null and
    // every performAction test NPEs on the first announce.
    @Mock private AaAssignmentEngine assignmentEngine;
    @Mock private AaWorkflowNotifier workflowNotifier;
    @Mock private AaHearingPort hearingPort;
    @Mock private ComplaintTimelineRepository complaintTimelineRepository;
    @Mock private AaOutboxPublisher outboxPublisher;

    @InjectMocks
    private AppealWorkflowService appealWorkflowService;

    private Complaint closedComplaint;

    /**
     * The acting AA identity is now resolved server-side, so every scenario that used to declare
     * actorRole/actor in the params map declares it here instead.
     */
    private void actingAs(String role, String actor) {
        lenient().when(aaIdentityResolver.resolveAaRole()).thenReturn(role);
        lenient().when(aaIdentityResolver.resolveActor()).thenReturn(actor);
    }

    /** Parent complaint lookup + derived classification, both now mandatory for fileAppeal(). */
    private void givenParentClassifiedAs(String classification) {
        lenient().when(complaintRepository.findByComplaintNumber("CMP-20260601-100001"))
                .thenReturn(Optional.of(closedComplaint));
        lenient().when(classificationService.classify(any(Complaint.class), any(AppealParty.class)))
                .thenReturn(classification);
    }

    @BeforeEach
    void setUp() {
        closedComplaint = Complaint.builder()
                .id(1L)
                .complaintNumber("CMP-20260601-100001")
                .complainantName("Test Appellant")
                .complainantEmail("appellant@example.com")
                .subject("Credit Card Overcharge")
                .description("Bank charged excessive fees")
                .status("closed")
                .priority("HIGH")
                .department("RBIO")
                .entityCode("HDFC001")
                .closedAt(LocalDateTime.now().minusDays(10))
                .createdAt(LocalDateTime.now().minusDays(60))
                .updatedAt(LocalDateTime.now().minusDays(10))
                .build();

        // The assignment engine's contract is that assign() NEVER returns null — an exhausted pool comes
        // back as an UNASSIGNED_* outcome instead. A bare mock returns null, which is a state the real
        // engine cannot produce, so stub it here rather than making production code defend against an
        // impossible value. Tests that care about the placement override this.
        lenient().when(assignmentEngine.assign(any())).thenAnswer(inv -> {
            com.hrms.cms.dto.AaAssignmentRequest req = inv.getArgument(0);
            return com.hrms.cms.dto.AaAssignmentResult.builder()
                    .outcome(com.hrms.cms.dto.AaAssignmentResult.Outcome.ASSIGNED)
                    .assignedUserId("registrar-1")
                    .roleGroup(req == null ? null : req.getRoleGroup())
                    .messageKey("aa.assignment.assigned")
                    .build();
        });
        lenient().when(assignmentEngine.assignManually(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> com.hrms.cms.dto.AaAssignmentResult.builder()
                        .outcome(com.hrms.cms.dto.AaAssignmentResult.Outcome.MANUAL_OVERRIDE)
                        .assignedUserId(inv.getArgument(1))
                        .messageKey("aa.assignment.manual_override")
                        .build());
    }

    // ═══════════════════════════════════════════════════════════════════
    // fileAppeal()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("fileAppeal()")
    class FileAppeal {

        @Test
        @DisplayName("should create appeal with correct fields and return result map")
        void shouldCreateAppealWithCorrectFields() {
            givenParentClassifiedAs("APPEAL");
            Map<String, Object> eligibility = Map.of("eligible", true, "reason", "OK");
            when(eligibilityService.checkEligibility("CMP-20260601-100001")).thenReturn(eligibility);
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test Appellant");
            request.put("appellantEmail", "appellant@example.com");
            request.put("appealGround", "Dissatisfied with closure");
            request.put("reliefSought", "Full refund");

            Map<String, Object> result = appealWorkflowService.fileAppeal(request);

            assertThat(result).containsKey("appealNumber");
            assertThat(result.get("classificationType")).isEqualTo("APPEAL");
            assertThat(result.get("status")).isEqualTo("filed");
            assertThat(result.get("assignedRole")).isEqualTo("AA_DO");
            // TWO saves, not one: the assignment engine records a placement against the appeal NUMBER,
            // so the appeal must exist before it can be placed. The first save persists the filing, the
            // second writes back the officer the engine chose. The old single-save expectation belonged
            // to the in-memory round robin, which could pick an assignee before anything was persisted.
            verify(appealRepository, times(2)).save(any(Appeal.class));
            verify(appealTimelineRepository).save(any(AppealTimeline.class));
        }

        @Test
        @DisplayName("should generate appeal number with correct prefix")
        void shouldGenerateAppealNumberWithPrefix() {
            givenParentClassifiedAs("APPEAL");
            Map<String, Object> eligibility = Map.of("eligible", true, "reason", "OK");
            when(eligibilityService.checkEligibility("CMP-20260601-100001")).thenReturn(eligibility);
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            Map<String, Object> result = appealWorkflowService.fileAppeal(request);

            assertThat((String) result.get("appealNumber")).startsWith("APL-");
        }

        /**
         * The officer now comes from AaAssignmentEngine, not from a round robin over the Keycloak
         * AA_DO list, so the expected id is the engine's placement ("registrar-1") and the request it
         * received is asserted instead of a Keycloak lookup.
         */
        @Test
        @DisplayName("should assign to AA_DO role by default, placing the officer through the engine")
        void shouldAssignToRegistrarByDefault() {
            givenParentClassifiedAs("APPEAL");
            Map<String, Object> eligibility = Map.of("eligible", true, "reason", "OK");
            when(eligibilityService.checkEligibility("CMP-20260601-100001")).thenReturn(eligibility);
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            Map<String, Object> result = appealWorkflowService.fileAppeal(request);

            assertThat(result.get("assignedRole")).isEqualTo("AA_DO");
            assertThat(result.get("assignedOfficer")).isEqualTo("registrar-1");
            verify(assignmentEngine).assign(argThat(req -> "AA_DO".equals(req.getRoleGroup())));
            verify(keycloakUserService, never()).getUsersByRole(anyString());
        }

        /**
         * New: an exhausted or empty officer pool is a legitimate outcome, not an error. The old round
         * robin silently picked someone regardless of load; the engine reports UNASSIGNED_* and the
         * appeal stays visible in the AA_DO role queue with no fabricated assignee.
         */
        @Test
        @DisplayName("should leave the appeal unassigned when the engine cannot place it")
        void shouldLeaveUnassignedWhenPoolExhausted() {
            givenParentClassifiedAs("APPEAL");
            when(eligibilityService.checkEligibility("CMP-20260601-100001"))
                    .thenReturn(Map.of("eligible", true, "reason", "OK"));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            when(assignmentEngine.assign(any())).thenReturn(com.hrms.cms.dto.AaAssignmentResult.builder()
                    .outcome(com.hrms.cms.dto.AaAssignmentResult.Outcome.UNASSIGNED_POOL_EMPTY)
                    .messageKey("aa.assignment.pool_empty")
                    .build());

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            Map<String, Object> result = appealWorkflowService.fileAppeal(request);

            assertThat(result.get("assignedRole")).isEqualTo("AA_DO");
            assertThat(result.get("assignedOfficer")).isNull();
            // Only the filing save: with nobody to write back, the second save never happens.
            verify(appealRepository, times(1)).save(any(Appeal.class));
        }

        @Test
        @DisplayName("should throw when originalComplaintNumber is missing")
        void shouldThrowWhenComplaintNumberMissing() {
            Map<String, String> request = new LinkedHashMap<>();
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("originalComplaintNumber is required");
        }

        /**
         * Replaces two tests that asserted the OLD behaviour — that classificationType was a required,
         * validated REQUEST field. It is now derived from the parent's closure clause, so a client
         * value is not merely optional, it must be ignored outright.
         */
        @Test
        @DisplayName("should ignore a client-supplied classificationType and use the derived value")
        void shouldIgnoreClientSuppliedClassification() {
            givenParentClassifiedAs("REPRESENTATION");
            when(eligibilityService.checkEligibility("CMP-20260601-100001"))
                    .thenReturn(Map.of("eligible", true, "reason", "OK"));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            // The caller asks for the more favourable classification; the clause says otherwise.
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            Map<String, Object> result = appealWorkflowService.fileAppeal(request);

            assertThat(result.get("classificationType")).isEqualTo("REPRESENTATION");
            // atLeastOnce(): filing now saves twice (create, then write back the engine's placement).
            verify(appealRepository, atLeastOnce())
                    .save(argThat(a -> "REPRESENTATION".equals(a.getClassificationType())));
        }

        @Test
        @DisplayName("should throw when the parent complaint does not exist")
        void shouldThrowWhenParentComplaintMissing() {
            when(complaintRepository.findByComplaintNumber("CMP-NONEXIST")).thenReturn(Optional.empty());

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-NONEXIST");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Parent complaint not found: CMP-NONEXIST");
            verify(appealRepository, never()).save(any(Appeal.class));
        }

        @Test
        @DisplayName("should derive appellant details from the parent complaint when omitted")
        void shouldDeriveAppellantFromParent() {
            givenParentClassifiedAs("APPEAL");
            when(eligibilityService.checkEligibility("CMP-20260601-100001"))
                    .thenReturn(Map.of("eligible", true, "reason", "OK"));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("appealGround", "Grounds");

            appealWorkflowService.fileAppeal(request);

            // atLeastOnce(): filing now saves twice (create, then write back the engine's placement).
            verify(appealRepository, atLeastOnce()).save(argThat(a ->
                    "Test Appellant".equals(a.getAppellantName())
                            && "appellant@example.com".equals(a.getAppellantEmail())));
        }

        @Test
        @DisplayName("should throw when appellantName is missing and the parent carries none")
        void shouldThrowWhenAppellantNameMissing() {
            closedComplaint.setComplainantName(null);
            when(complaintRepository.findByComplaintNumber("CMP-20260601-100001"))
                    .thenReturn(Optional.of(closedComplaint));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("appealGround", "Grounds");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("appellantName is required");
        }

        @Test
        @DisplayName("should throw when appealGround is missing")
        void shouldThrowWhenAppealGroundMissing() {
            givenParentClassifiedAs("APPEAL");

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("appellantName", "Test");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("appealGround is required");
        }

        @Test
        @DisplayName("should classify as ENTITY when the caller holds an RE role")
        void shouldClassifyForEntityPartyWhenCallerIsRe() {
            givenParentClassifiedAs("APPEAL");
            when(aaIdentityResolver.resolveRoles()).thenReturn(Set.of("RE_PNO"));
            when(eligibilityService.checkEligibility("CMP-20260601-100001"))
                    .thenReturn(Map.of("eligible", true, "reason", "OK"));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("appellantName", "Bank Nodal Officer");
            request.put("appealGround", "Grounds");
            // A self-declared complainant standing must not override the caller's RE role.
            request.put("appealFiledBy", "COMPLAINANT");

            appealWorkflowService.fileAppeal(request);

            verify(classificationService).classify(any(Complaint.class), eq(AppealParty.ENTITY));
            // atLeastOnce(): filing now saves twice (create, then write back the engine's placement).
            verify(appealRepository, atLeastOnce()).save(argThat(a -> "ENTITY".equals(a.getAppealFiledBy())));
        }

        @Test
        @DisplayName("should record provenance and parent clause on the new appeal")
        void shouldRecordProvenance() {
            givenParentClassifiedAs("APPEAL");
            actingAs("AA_DO", "aa_do_001");
            when(eligibilityService.checkEligibility("CMP-20260601-100001"))
                    .thenReturn(Map.of("eligible", true, "reason", "OK"));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            closedComplaint.setClosureClause("15(1)(b)");

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            appealWorkflowService.fileAppeal(request);

            // atLeastOnce(): filing now saves twice (create, then write back the engine's placement).
            verify(appealRepository, atLeastOnce()).save(argThat(a ->
                    "aa_do_001".equals(a.getCreatedBy())
                            && "AA_DO".equals(a.getCreatedByRole())
                            && "HDFC001".equals(a.getEntityCode())
                            && "15(1)(b)".equals(a.getClosureClause())
                            && "COMPLAINANT".equals(a.getAppealFiledBy())));
        }

        @Test
        @DisplayName("should propagate UnmappedClauseException rather than guessing a classification")
        void shouldPropagateUnmappedClause() {
            when(complaintRepository.findByComplaintNumber("CMP-20260601-100001"))
                    .thenReturn(Optional.of(closedComplaint));
            when(classificationService.classify(any(Complaint.class), any(AppealParty.class)))
                    .thenThrow(new AppealClassificationService.UnmappedClauseException(
                            "Closure clause '99(9)' is not configured", "99(9)", "RBIOS_2021"));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(AppealClassificationService.UnmappedClauseException.class);
            verify(appealRepository, never()).save(any(Appeal.class));
        }

        @Test
        @DisplayName("should throw when eligibility check fails")
        void shouldThrowWhenNotEligible() {
            givenParentClassifiedAs("APPEAL");
            Map<String, Object> eligibility = Map.of("eligible", false, "reason", "Complaint is still active");
            when(eligibilityService.checkEligibility("CMP-20260601-100001")).thenReturn(eligibility);

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Appeal not eligible");
        }

        @Test
        @DisplayName("should set REPRESENTATION type when the clause is not appealable")
        void shouldSetRepresentationType() {
            givenParentClassifiedAs("REPRESENTATION");
            Map<String, Object> eligibility = Map.of("eligible", true, "reason", "OK");
            when(eligibilityService.checkEligibility("CMP-20260601-100001")).thenReturn(eligibility);
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("appellantName", "Test");
            request.put("appealGround", "Advisory grounds");

            Map<String, Object> result = appealWorkflowService.fileAppeal(request);

            assertThat(result.get("classificationType")).isEqualTo("REPRESENTATION");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // performAction() — State Transitions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("performAction() - State Transitions")
    class PerformAction {

        @Test
        @DisplayName("ACCEPT should transition to under_review")
        void acceptShouldTransitionToUnderReview() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_DO", "registrar-1");
            params.put("remarks", "OK");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "ACCEPT", params);

            assertThat(result.get("action")).isEqualTo("ACCEPT");
            assertThat(result.get("newStatus")).isEqualTo("under_review");
            assertThat(result.get("workflowStage")).isEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("REJECT should set status to rejected")
        void rejectShouldSetRejected() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_DO", "registrar-1");
            params.put("remarks", "Time-barred");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "REJECT", params);

            assertThat(result.get("newStatus")).isEqualTo("rejected");
            assertThat(result.get("workflowStage")).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("REJECT should throw when remarks are blank")
        void rejectShouldThrowWhenRemarksBlank() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_DO", "registrar-1");
            params.put("remarks", "");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "REJECT", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rejection reason");
        }

        /**
         * The reviewer is now picked by AaAssignmentEngine, so the Keycloak AA_REVIEWER lookup is gone
         * and the placement asserted instead is the engine's. ASSIGN_TO_BENCH is only legal from
         * under_review — the file must be accepted before it can be handed to a bench.
         */
        @Test
        @DisplayName("ASSIGN_TO_BENCH should assign to bench officer role via the engine")
        void assignToBenchShouldAssign() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_DO", "registrar-1");
            params.put("remarks", "Assigning");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "ASSIGN_TO_BENCH", params);

            assertThat(result.get("assignedRole")).isEqualTo("AA_REVIEWER");
            assertThat(result.get("workflowStage")).isEqualTo("ASSIGNED_TO_BENCH");
            verify(assignmentEngine).assign(argThat(req -> "AA_REVIEWER".equals(req.getRoleGroup())));
            verify(keycloakUserService, never()).getUsersByRole(anyString());
        }

        /**
         * New: the from-status guard. ASSIGN_TO_BENCH is permitted for AA_DO but not from `filed`, and
         * the refusal is a 409-shaped AaIllegalTransitionException carrying what IS available — not a
         * silent success, which is how a freshly filed appeal used to skip acceptance entirely.
         */
        @Test
        @DisplayName("ASSIGN_TO_BENCH from filed is refused as an illegal transition")
        void assignToBenchFromFiledIsIllegal() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_DO", "registrar-1");
            params.put("remarks", "Assigning too early");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "ASSIGN_TO_BENCH", params))
                    .isInstanceOf(AaIllegalTransitionException.class)
                    .extracting(e -> ((AaIllegalTransitionException) e).getAvailableActions())
                    .isEqualTo(List.of("ACCEPT", "REJECT", "REQUEST_DOCUMENTS"));
            assertThat(appeal.getStatus()).isEqualTo("filed");
            verify(appealRepository, never()).save(any(Appeal.class));
        }

        /**
         * New: the same guard closes the disposal hole. A passed order cannot be overwritten by a second
         * PASS_ORDER, nor pulled back to under_review by anything but AA_ADMIN's audited REOPEN.
         */
        @Test
        @DisplayName("PASS_ORDER on an already-passed order is refused as an illegal transition")
        void passOrderOnTerminalIsIllegal() {
            Appeal appeal = createAppeal("order_passed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_SECRETARIAT", "authority-1");
            params.put("orderOutcome", "MODIFIED");
            params.put("remarks", "Second bite");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "PASS_ORDER", params))
                    .isInstanceOf(AaIllegalTransitionException.class)
                    .hasMessageContaining("not allowed from status 'order_passed'");
            verify(appealRepository, never()).save(any(Appeal.class));
        }

        @Test
        @DisplayName("SCHEDULE_HEARING should set hearing date and status")
        void scheduleHearingShouldSetDateAndStatus() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_REVIEWER", "bench-1");
            params.put("hearingDate", "2026-07-20T10:00:00");
            params.put("hearingVenue", "Room A");
            params.put("remarks", "Scheduling");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "SCHEDULE_HEARING", params);

            assertThat(result.get("newStatus")).isEqualTo("hearing_scheduled");
            assertThat(result.get("workflowStage")).isEqualTo("HEARING_SCHEDULED");
        }

        @Test
        @DisplayName("SCHEDULE_HEARING should throw when hearingDate missing")
        void scheduleHearingShouldThrowWhenNoDate() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_REVIEWER", "bench-1");
            params.put("remarks", "Scheduling");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "SCHEDULE_HEARING", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("hearingDate is required");
        }

        /** The AA_SECRETARIAT holder is placed by the engine now, so the Keycloak stub is gone. */
        @Test
        @DisplayName("FORWARD_TO_AUTHORITY should change assigned role")
        void forwardToAuthorityShouldChangeRole() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_REVIEWER", "bench-1");
            params.put("remarks", "Forwarding");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "FORWARD_TO_AUTHORITY", params);

            assertThat(result.get("assignedRole")).isEqualTo("AA_SECRETARIAT");
            assertThat(result.get("workflowStage")).isEqualTo("FORWARDED_TO_AUTHORITY");
            verify(assignmentEngine).assign(argThat(req -> "AA_SECRETARIAT".equals(req.getRoleGroup())));
        }

        @Test
        @DisplayName("PASS_ORDER should set order_passed status with outcome")
        void passOrderShouldSetStatus() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_SECRETARIAT", "authority-1");
            params.put("orderOutcome", "UPHELD");
            params.put("orderSummary", "Original order upheld");
            params.put("remarks", "Order upheld");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "PASS_ORDER", params);

            assertThat(result.get("newStatus")).isEqualTo("order_passed");
            assertThat(result.get("workflowStage")).isEqualTo("ORDER_PASSED");
        }

        @Test
        @DisplayName("PASS_ORDER should throw when orderOutcome is missing")
        void passOrderShouldThrowWhenNoOutcome() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_SECRETARIAT", "authority-1");
            params.put("remarks", "Order");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "PASS_ORDER", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("orderOutcome is required");
        }

        @Test
        @DisplayName("PASS_ORDER with MODIFIED outcome and awardModifiedAmount")
        void passOrderModifiedShouldSetAmount() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_SECRETARIAT", "authority-1");
            params.put("orderOutcome", "MODIFIED");
            params.put("awardModifiedAmount", "100000");
            params.put("remarks", "Modified amount");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "PASS_ORDER", params);

            assertThat(result.get("newStatus")).isEqualTo("order_passed");
            verify(appealRepository).save(argThat(a -> a.getAwardModifiedAmount() != null));
        }

        @Test
        @DisplayName("REMAND_TO_OMBUDSMAN should close appeal and reopen complaint")
        void remandToOmbudsmanShouldReopenComplaint() {
            Appeal appeal = createAppeal("hearing_scheduled");
            appeal.setOriginalComplaintNumber("CMP-20260601-100001");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            when(complaintRepository.findByComplaintNumber("CMP-20260601-100001"))
                    .thenReturn(Optional.of(closedComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_SECRETARIAT", "authority-1");
            params.put("remarks", "Remanding");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "REMAND_TO_OMBUDSMAN", params);

            assertThat(result.get("newStatus")).isEqualTo("closed");
            assertThat(result.get("workflowStage")).isEqualTo("REMANDED");
            verify(complaintRepository).save(argThat(c -> "in_progress".equals(c.getStatus())));
        }

        @Test
        @DisplayName("DISMISS should close appeal")
        void dismissShouldCloseAppeal() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_SECRETARIAT", "authority-1");
            params.put("remarks", "Not maintainable");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "DISMISS", params);

            assertThat(result.get("newStatus")).isEqualTo("closed");
            assertThat(result.get("workflowStage")).isEqualTo("DISMISSED");
        }

        @Test
        @DisplayName("CLOSE by admin should close with closureCause")
        void closeShouldCloseWithCause() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_ADMIN", "admin-1");
            params.put("closureCause", "ADMIN_CLOSED");
            params.put("remarks", "Admin closure");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "CLOSE", params);

            assertThat(result.get("newStatus")).isEqualTo("closed");
            assertThat(result.get("workflowStage")).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("REOPEN should set status back to under_review")
        void reopenShouldSetUnderReview() {
            Appeal appeal = createAppeal("closed");
            appeal.setClosedAt(LocalDateTime.now());
            appeal.setClosureCause("ADMIN_CLOSED");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_ADMIN", "admin-1");
            params.put("remarks", "Reopening");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "REOPEN", params);

            assertThat(result.get("newStatus")).isEqualTo("under_review");
            assertThat(result.get("workflowStage")).isEqualTo("REOPENED");
        }

        /**
         * Changed expectation: an unknown action is now an IllegalArgumentException ("Unknown action"),
         * not AaAccessDeniedException. The action is resolved against AaWorkflowTransition BEFORE the
         * role check, and that ordering is the correct one — an action that does not exist is a
         * malformed request (400), not a permissions failure (403). Reporting it as a denial told the
         * caller to go and ask for a role, which could never make "INVALID" work.
         */
        @Test
        @DisplayName("unknown action is rejected as malformed input, not as a permissions failure")
        void shouldThrowForUnknownAction() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_ADMIN", "admin-1");

            Map<String, String> params = new HashMap<>();
            params.put("remarks", "");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "INVALID", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .isNotInstanceOf(AaAccessDeniedException.class)
                    .hasMessageContaining("Unknown action: INVALID");
            verify(appealRepository, never()).save(any(Appeal.class));
        }

        @Test
        @DisplayName("should throw when appeal not found")
        void shouldThrowWhenAppealNotFound() {
            when(appealRepository.findByAppealNumber("APL-NONEXIST")).thenReturn(Optional.empty());

            Map<String, String> params = Map.of("remarks", "");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-NONEXIST", "ACCEPT", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        /**
         * Was an IllegalArgumentException; a permission failure is now its own exception type so the
         * controller can answer 403 instead of a 200 that looks like bad input.
         */
        @Test
        @DisplayName("should throw AaAccessDeniedException when action not permitted for role")
        void shouldThrowWhenActionNotPermittedForRole() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_DO", "registrar-1");
            params.put("remarks", "");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "PASS_ORDER", params))
                    .isInstanceOf(AaAccessDeniedException.class)
                    .hasMessageContaining("not permitted for role");
            verify(appealRepository, never()).save(any(Appeal.class));
        }

        /**
         * THE BYPASS. performAction used to read actorRole out of the request body and skip the matrix
         * check entirely when it was blank — and the Angular client never sent it, so in practice the
         * AA role check never ran on a real request. A caller with no AA role must now be refused.
         */
        @Test
        @DisplayName("should throw AaAccessDeniedException when no AA role can be resolved")
        void shouldThrowWhenNoAaRoleResolvable() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(aaIdentityResolver.resolveAaRole()).thenReturn(null);

            Map<String, String> params = new HashMap<>();
            // Exactly the old payload: a self-declared role in the body must buy nothing.
            params.put("actor", "attacker");
            params.put("actorRole", "AA_SECRETARIAT");
            params.put("remarks", "OK");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "ACCEPT", params))
                    .isInstanceOf(AaAccessDeniedException.class)
                    .hasMessageContaining("No AA role");

            assertThat(appeal.getStatus()).isEqualTo("filed");
            verify(appealRepository, never()).save(any(Appeal.class));
            verify(appealTimelineRepository, never()).save(any(AppealTimeline.class));
        }

        /**
         * Attribution came from the request body too, so the recorded actor was whatever the caller
         * typed. The timeline must record the resolved identity, not the claimed one.
         */
        @Test
        @DisplayName("should record the resolver's actor on the timeline, not the params actor")
        void shouldRecordResolvedActorNotParamsActor() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            actingAs("AA_DO", "aa_do_001");

            Map<String, String> params = new HashMap<>();
            params.put("actor", "attacker");
            params.put("actorRole", "AA_ADMIN");
            params.put("remarks", "Accepted");

            appealWorkflowService.performAction("APL-001", "ACCEPT", params);

            ArgumentCaptor<AppealTimeline> captor = ArgumentCaptor.forClass(AppealTimeline.class);
            verify(appealTimelineRepository).save(captor.capture());
            assertThat(captor.getValue().getPerformedBy()).isEqualTo("aa_do_001");
            assertThat(captor.getValue().getPerformedBy()).isNotEqualTo("attacker");
            assertThat(captor.getValue().getPerformedByRole()).isEqualTo("AA_DO");
        }

        /** A resolvable role but no resolvable id is attributed as UNKNOWN, never as the claimed id. */
        @Test
        @DisplayName("should record UNKNOWN when the actor id cannot be resolved")
        void shouldRecordUnknownWhenActorUnresolvable() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            when(aaIdentityResolver.resolveAaRole()).thenReturn("AA_DO");
            when(aaIdentityResolver.resolveActor()).thenReturn(null);

            Map<String, String> params = new HashMap<>();
            params.put("actor", "attacker");
            params.put("remarks", "Accepted");

            appealWorkflowService.performAction("APL-001", "ACCEPT", params);

            ArgumentCaptor<AppealTimeline> captor = ArgumentCaptor.forClass(AppealTimeline.class);
            verify(appealTimelineRepository).save(captor.capture());
            assertThat(captor.getValue().getPerformedBy()).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("should reject classificationType change attempt (immutability)")
        void shouldRejectClassificationTypeChange() {
            Appeal appeal = createAppeal("filed");
            appeal.setClassificationType("APPEAL");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_DO", "registrar-1");
            params.put("remarks", "Change");
            params.put("classificationType", "REPRESENTATION");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "ACCEPT", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("immutable");
        }

        /**
         * With no routedByUserId on the record there is no router to return to, so the engine's pool
         * pick stands. The Keycloak AA_DO lookup that used to drive this is gone.
         */
        @Test
        @DisplayName("SEND_BACK_REGISTRAR should reassign to registrar")
        void sendBackRegistrarShouldReassign() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_REVIEWER", "bench-1");
            params.put("remarks", "Need more info");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "SEND_BACK_REGISTRAR", params);

            assertThat(result.get("assignedRole")).isEqualTo("AA_DO");
            assertThat(result.get("workflowStage")).isEqualTo("SENT_BACK_TO_REGISTRAR");
            verify(assignmentEngine).assign(argThat(req -> "AA_DO".equals(req.getRoleGroup())));
        }

        /**
         * New: a sent-back file goes to the DO who ROUTED it, through the engine's audited manual path,
         * rather than to an arbitrary pool pick. The officer who raised the query holds the context.
         */
        @Test
        @DisplayName("SEND_BACK_REGISTRAR returns the appeal to the DO who routed it")
        void sendBackRegistrarReturnsToRouter() {
            Appeal appeal = createAppeal("under_review");
            appeal.setRoutedByUserId("aa_do_007");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_REVIEWER", "bench-1");
            params.put("remarks", "Need more info");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "SEND_BACK_REGISTRAR", params);

            assertThat(result.get("assignedOfficer")).isEqualTo("aa_do_007");
            verify(assignmentEngine).assignManually(eq("APL-001"), eq("aa_do_007"), anyString());
            verify(assignmentEngine, never()).assign(any());
        }

        /**
         * New: REOPEN is the single escape from a terminal state, so it demands a stated reason —
         * an unexplained revival of a passed order is indistinguishable from an operator error.
         */
        @Test
        @DisplayName("REOPEN should throw when remarks are blank")
        void reopenShouldThrowWhenRemarksBlank() {
            Appeal appeal = createAppeal("closed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_ADMIN", "admin-1");
            params.put("remarks", "  ");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "REOPEN", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("aa.workflow.error_reopen_reason_required");
            verify(appealRepository, never()).save(any(Appeal.class));
        }

        /**
         * New: a REASSIGN target role is validated against the AA vocabulary. It used to be written
         * straight through, so an appeal could be parked on assignedRole="RE_PNO" or a typo and vanish
         * from every task query with no error and no way back short of a SQL fix.
         */
        @Test
        @DisplayName("REASSIGN should reject a target role outside the AA vocabulary")
        void reassignShouldRejectNonAaRole() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_ADMIN", "admin-1");
            params.put("role", "RE_PNO");
            params.put("remarks", "Parking it");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "REASSIGN", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("aa.workflow.error_invalid_target_role");
            assertThat(appeal.getAssignedRole()).isEqualTo("AA_DO");
            verify(appealRepository, never()).save(any(Appeal.class));
        }

        /** A named REASSIGN target goes through the engine's audited manual-override path. */
        @Test
        @DisplayName("REASSIGN to a named officer uses the audited manual override")
        void reassignToNamedOfficerUsesManualOverride() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_ADMIN", "admin-1");
            params.put("role", "AA_REVIEWER");
            params.put("officer", "reviewer-9");
            params.put("remarks", "Domain expertise");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "REASSIGN", params);

            assertThat(result.get("assignedRole")).isEqualTo("AA_REVIEWER");
            assertThat(result.get("assignedOfficer")).isEqualTo("reviewer-9");
            verify(assignmentEngine).assignManually("APL-001", "reviewer-9", "Domain expertise");
        }

        /**
         * New: performAction advertises what the caller may do next, from the same table that just
         * enforced the transition, so a client can never offer an action the server would refuse.
         */
        @Test
        @DisplayName("result map carries the availableActions for the resulting status")
        void resultShouldCarryAvailableActions() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            actingAs("AA_DO", "registrar-1");
            params.put("remarks", "OK");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "ACCEPT", params);

            // Now under_review, so the DO may hand it to a bench or ask for documents — but not ACCEPT again.
            assertThat(result.get("availableActions"))
                    .isEqualTo(List.of("ASSIGN_TO_BENCH", "REQUEST_DOCUMENTS"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Role-Action Authorization (via performAction)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Role-Action Authorization")
    class RoleActionAuthorization {

        /**
         * The fixture status is now per-action: role permission alone is no longer enough, the action
         * must also be legal FROM the appeal's status. ASSIGN_TO_BENCH is an AA_DO action but only once
         * the appeal has been accepted, so it is driven from under_review while the intake actions run
         * from filed. The Keycloak stub is gone — assignment goes through AaAssignmentEngine.
         */
        @ParameterizedTest
        @CsvSource({
                "AA_DO, ACCEPT, filed",
                "AA_DO, REJECT, filed",
                "AA_DO, ASSIGN_TO_BENCH, under_review",
                "AA_DO, REQUEST_DOCUMENTS, filed"
        })
        @DisplayName("AA_DO should be allowed these actions from a legal status")
        void aaRegistrarAllowedActions(String role, String action, String fromStatus) {
            Appeal appeal = createAppeal(fromStatus);
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            actingAs(role, "registrar-1");
            Map<String, String> params = new HashMap<>();
            params.put("remarks", "test");

            // Should NOT throw about "not permitted" nor about an illegal transition
            assertThatCode(() -> appealWorkflowService.performAction("APL-001", action, params))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @CsvSource({
                "AA_DO, PASS_ORDER",
                "AA_DO, DISMISS",
                "AA_DO, SCHEDULE_HEARING"
        })
        @DisplayName("AA_DO should be denied these actions")
        void aaRegistrarDeniedActions(String role, String action) {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            actingAs(role, "registrar-1");
            Map<String, String> params = new HashMap<>();
            params.put("remarks", "test");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", action, params))
                    .isInstanceOf(AaAccessDeniedException.class)
                    .hasMessageContaining("not permitted for role");
            verify(appealRepository, never()).save(any(Appeal.class));
        }

        /** Every AA role is refused every action outside its own list — the matrix, exhaustively. */
        @ParameterizedTest
        @CsvSource({
                "AA_REVIEWER, ACCEPT",
                "AA_REVIEWER, REJECT",
                "AA_REVIEWER, PASS_ORDER",
                "AA_REVIEWER, CLOSE",
                "AA_SECRETARIAT, ACCEPT",
                "AA_SECRETARIAT, ASSIGN_TO_BENCH",
                "AA_SECRETARIAT, REOPEN",
                "AA_ADMIN, ACCEPT",
                "AA_ADMIN, PASS_ORDER",
                "AA_ADMIN, SCHEDULE_HEARING"
        })
        @DisplayName("each role is denied actions outside its own list")
        void rolesDeniedForeignActions(String role, String action) {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            actingAs(role, "user-1");
            Map<String, String> params = new HashMap<>();
            params.put("remarks", "test");
            params.put("orderOutcome", "UPHELD");
            params.put("hearingDate", "2026-07-20T10:00:00");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", action, params))
                    .isInstanceOf(AaAccessDeniedException.class)
                    .hasMessageContaining("not permitted for role " + role);
            verify(appealRepository, never()).save(any(Appeal.class));
        }

        @ParameterizedTest
        @CsvSource({
                "AA_SECRETARIAT, PASS_ORDER",
                "AA_SECRETARIAT, REMAND_TO_OMBUDSMAN",
                "AA_SECRETARIAT, DISMISS",
                "AA_SECRETARIAT, SCHEDULE_HEARING"
        })
        @DisplayName("AA_SECRETARIAT should be allowed these actions")
        void aaAuthorityAllowedActions(String role, String action) {
            Appeal appeal = createAppeal("hearing_scheduled");
            appeal.setOriginalComplaintNumber("CMP-20260601-100001");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(complaintRepository.findByComplaintNumber(anyString()))
                    .thenReturn(Optional.of(closedComplaint));
            lenient().when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            actingAs(role, "authority-1");
            Map<String, String> params = new HashMap<>();
            params.put("remarks", "test");
            params.put("orderOutcome", "UPHELD");
            params.put("hearingDate", "2026-07-20T10:00:00");

            assertThatCode(() -> appealWorkflowService.performAction("APL-001", action, params))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getAvailableActions()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAvailableActions()")
    class GetAvailableActions {

        /**
         * Changed expectation: the list is STATUS-AWARE now, not a fixed per-role list. From `filed` an
         * AA_DO may accept, reject or ask for documents — ASSIGN_TO_BENCH is deliberately absent because
         * performAction would refuse it before acceptance. The old fixed list made the UI offer a button
         * the server rejected.
         */
        @Test
        @DisplayName("should return only the AA_DO actions legal from filed")
        void shouldReturnRegistrarActions() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_DO", "registrar-1");

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001");

            assertThat(actions).containsExactly("ACCEPT", "REJECT", "REQUEST_DOCUMENTS");
            assertThat(actions).doesNotContain("ASSIGN_TO_BENCH", "PASS_ORDER", "DISMISS");
        }

        /** The counterpart: once accepted, ASSIGN_TO_BENCH appears and ACCEPT/REJECT drop away. */
        @Test
        @DisplayName("should return ASSIGN_TO_BENCH for AA_DO once the appeal is under_review")
        void shouldReturnAssignToBenchOnceUnderReview() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_DO", "registrar-1");

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001");

            assertThat(actions).containsExactly("ASSIGN_TO_BENCH", "REQUEST_DOCUMENTS");
        }

        @Test
        @DisplayName("should return AA_REVIEWER actions for AA_REVIEWER")
        void shouldReturnBenchOfficerActions() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_REVIEWER", "bench-1");

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001");

            assertThat(actions).contains("SCHEDULE_HEARING", "PREPARE_BRIEF", "FORWARD_TO_AUTHORITY", "SEND_BACK_REGISTRAR");
        }

        @Test
        @DisplayName("should return AA_SECRETARIAT actions for AA_SECRETARIAT")
        void shouldReturnAuthorityActions() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_SECRETARIAT", "authority-1");

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001");

            assertThat(actions).contains("PASS_ORDER", "SCHEDULE_HEARING", "REMAND_TO_OMBUDSMAN", "DISMISS");
        }

        @Test
        @DisplayName("should return empty list for closed appeal (non-admin)")
        void shouldReturnEmptyForClosedNonAdmin() {
            Appeal appeal = createAppeal("closed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_REVIEWER", "bench-1");

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001");

            assertThat(actions).isEmpty();
        }

        /**
         * Changed expectation: on a terminal appeal the ONLY admin action is REOPEN. REASSIGN and CLOSE
         * are no longer offered — reassigning a disposed appeal is meaningless and closing an
         * already-closed one would rewrite its closure record. REOPEN is deliberately the single
         * audited escape from a terminal state.
         */
        @Test
        @DisplayName("should return only REOPEN for AA_ADMIN on a closed appeal")
        void shouldReturnAdminActionsOnClosed() {
            Appeal appeal = createAppeal("closed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_ADMIN", "admin-1");

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001");

            assertThat(actions).containsExactly("REOPEN");
        }

        /** On a live appeal the admin's override set is REASSIGN + CLOSE, and REOPEN is not offered. */
        @Test
        @DisplayName("should return REASSIGN and CLOSE for AA_ADMIN on an active appeal")
        void shouldReturnAdminActionsOnActive() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_ADMIN", "admin-1");

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001");

            assertThat(actions).containsExactly("REASSIGN", "CLOSE");
        }

        @Test
        @DisplayName("should return empty list when appeal not found")
        void shouldReturnEmptyWhenNotFound() {
            when(appealRepository.findByAppealNumber("APL-NONEXIST")).thenReturn(Optional.empty());

            List<String> actions = appealWorkflowService.getAvailableActions("APL-NONEXIST");

            assertThat(actions).isEmpty();
        }

        /**
         * The role is no longer a client-supplied query param, so a caller holding no AA role cannot
         * ask for — and be shown — another role's action set.
         */
        @Test
        @DisplayName("should return empty list when no AA role can be resolved")
        void shouldReturnEmptyWhenNoRoleResolvable() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(aaIdentityResolver.resolveAaRole()).thenReturn(null);

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001");

            assertThat(actions).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // overrideClassification()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("overrideClassification()")
    class OverrideClassification {

        private static final String REASON = "Clause mapping was wrong for this closure category";

        @Test
        @DisplayName("should re-classify and write a field-level audit entry")
        void shouldOverrideAndAudit() {
            Appeal appeal = createAppeal("under_review");
            appeal.setClassificationType("APPEAL");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            actingAs("AA_DO", "aa_do_001");

            Map<String, Object> result =
                    appealWorkflowService.overrideClassification("APL-001", "REPRESENTATION", REASON);

            assertThat(result.get("previousClassification")).isEqualTo("APPEAL");
            assertThat(result.get("classificationType")).isEqualTo("REPRESENTATION");
            assertThat(result.get("overriddenBy")).isEqualTo("aa_do_001");

            assertThat(appeal.getClassificationType()).isEqualTo("REPRESENTATION");
            assertThat(appeal.isClassificationOverridden()).isTrue();
            assertThat(appeal.getClassificationOverrideReason()).isEqualTo(REASON);
            assertThat(appeal.getClassificationOverriddenBy()).isEqualTo("aa_do_001");
            assertThat(appeal.getClassificationOverriddenAt()).isNotNull();

            ArgumentCaptor<AppealTimeline> captor = ArgumentCaptor.forClass(AppealTimeline.class);
            verify(appealTimelineRepository).save(captor.capture());
            AppealTimeline entry = captor.getValue();
            assertThat(entry.getAction()).isEqualTo("CLASSIFICATION_OVERRIDE");
            assertThat(entry.getFieldName()).isEqualTo("classificationType");
            assertThat(entry.getOldValue()).isEqualTo("APPEAL");
            assertThat(entry.getNewValue()).isEqualTo("REPRESENTATION");
            assertThat(entry.getPerformedBy()).isEqualTo("aa_do_001");
            assertThat(entry.getPerformedByRole()).isEqualTo("AA_DO");
            assertThat(entry.getRemarks()).isEqualTo(REASON);
        }

        @ParameterizedTest
        @ValueSource(strings = {"AA_DO", "AA_REVIEWER", "AA_ADMIN"})
        @DisplayName("AA_DO, AA_REVIEWER and AA_ADMIN may override")
        void permittedRolesMayOverride(String role) {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            actingAs(role, "user-1");

            assertThatCode(() -> appealWorkflowService
                    .overrideClassification("APL-001", "REPRESENTATION", REASON))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AA_SECRETARIAT may not override")
        void secretariatMayNotOverride() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_SECRETARIAT", "authority-1");

            assertThatThrownBy(() -> appealWorkflowService
                    .overrideClassification("APL-001", "REPRESENTATION", REASON))
                    .isInstanceOf(AaAccessDeniedException.class)
                    .hasMessageContaining("Classification override requires");

            assertThat(appeal.getClassificationType()).isEqualTo("APPEAL");
            verify(appealRepository, never()).save(any(Appeal.class));
            verify(appealTimelineRepository, never()).save(any(AppealTimeline.class));
        }

        @Test
        @DisplayName("a caller with no AA role may not override")
        void noRoleMayNotOverride() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(aaIdentityResolver.resolveAaRole()).thenReturn(null);

            assertThatThrownBy(() -> appealWorkflowService
                    .overrideClassification("APL-001", "REPRESENTATION", REASON))
                    .isInstanceOf(AaAccessDeniedException.class);
            verify(appealRepository, never()).save(any(Appeal.class));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "too short", "short one"})
        @DisplayName("should reject a blank or under-10-character reason")
        void shouldRejectShortReason(String reason) {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_DO", "aa_do_001");

            assertThatThrownBy(() -> appealWorkflowService
                    .overrideClassification("APL-001", "REPRESENTATION", reason))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 10 characters");

            assertThat(appeal.getClassificationType()).isEqualTo("APPEAL");
            verify(appealTimelineRepository, never()).save(any(AppealTimeline.class));
        }

        @Test
        @DisplayName("should reject a null reason")
        void shouldRejectNullReason() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_DO", "aa_do_001");

            assertThatThrownBy(() -> appealWorkflowService
                    .overrideClassification("APL-001", "REPRESENTATION", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 10 characters");
        }

        @Test
        @DisplayName("should reject a no-op change")
        void shouldRejectNoOpChange() {
            Appeal appeal = createAppeal("under_review");
            appeal.setClassificationType("APPEAL");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_DO", "aa_do_001");

            assertThatThrownBy(() -> appealWorkflowService
                    .overrideClassification("APL-001", "APPEAL", REASON))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already classified as APPEAL");

            verify(appealRepository, never()).save(any(Appeal.class));
            verify(appealTimelineRepository, never()).save(any(AppealTimeline.class));
        }

        @Test
        @DisplayName("should reject a classification outside APPEAL/REPRESENTATION")
        void shouldRejectUnknownClassification() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_DO", "aa_do_001");

            assertThatThrownBy(() -> appealWorkflowService
                    .overrideClassification("APL-001", "SOMETHING_ELSE", REASON))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be APPEAL or REPRESENTATION");
        }

        @Test
        @DisplayName("should reject a blank classification")
        void shouldRejectBlankClassification() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            actingAs("AA_DO", "aa_do_001");

            assertThatThrownBy(() -> appealWorkflowService
                    .overrideClassification("APL-001", "  ", REASON))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("newClassification is required");
        }

        @Test
        @DisplayName("should throw when the appeal does not exist")
        void shouldThrowWhenAppealNotFound() {
            when(appealRepository.findByAppealNumber("APL-NONEXIST")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> appealWorkflowService
                    .overrideClassification("APL-NONEXIST", "REPRESENTATION", REASON))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Appeal not found");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getStats()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getStats()")
    class GetStats {

        @Test
        @DisplayName("should return correct statistics counts")
        void shouldReturnCorrectStats() {
            when(appealRepository.count()).thenReturn(50L);
            when(appealRepository.findByStatus("filed")).thenReturn(List.of(createAppeal("filed")));
            when(appealRepository.findByStatus("under_review")).thenReturn(Collections.emptyList());
            when(appealRepository.findByStatus("hearing_scheduled")).thenReturn(Collections.emptyList());
            when(appealRepository.findByStatus("order_passed")).thenReturn(Collections.emptyList());
            when(appealRepository.findByStatus("closed")).thenReturn(Collections.emptyList());
            when(appealRepository.findByStatus("rejected")).thenReturn(Collections.emptyList());

            Map<String, Object> stats = appealWorkflowService.getStats();

            assertThat(stats.get("total")).isEqualTo(50L);
            assertThat(stats.get("filed")).isEqualTo(1);
            assertThat(stats).containsKeys("total", "filed", "underReview", "hearingScheduled", "orderPassed", "closed", "rejected");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getTimeline()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getTimeline()")
    class GetTimeline {

        @Test
        @DisplayName("should return timeline entries for appeal")
        void shouldReturnTimelineEntries() {
            AppealTimeline entry = AppealTimeline.builder()
                    .appealNumber("APL-001")
                    .action("FILED")
                    .performedBy("SYSTEM")
                    .toStatus("filed")
                    .performedAt(LocalDateTime.now())
                    .build();
            when(appealTimelineRepository.findByAppealNumberOrderByPerformedAtDesc("APL-001"))
                    .thenReturn(List.of(entry));

            List<AppealTimeline> timeline = appealWorkflowService.getTimeline("APL-001");

            assertThat(timeline).hasSize(1);
            assertThat(timeline.get(0).getAction()).isEqualTo("FILED");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════════

    private Appeal createAppeal(String status) {
        return Appeal.builder()
                .id(1L)
                .appealNumber("APL-001")
                .originalComplaintNumber("CMP-20260601-100001")
                .classificationType("APPEAL")
                .appealGround("Test grounds")
                .reliefSought("Test relief")
                .appellantName("Test Appellant")
                .appellantEmail("test@example.com")
                .status(status)
                .priority("high")
                .assignedRole("AA_DO")
                .assignedOfficer("registrar-1")
                .workflowStage("FILED")
                .filedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
