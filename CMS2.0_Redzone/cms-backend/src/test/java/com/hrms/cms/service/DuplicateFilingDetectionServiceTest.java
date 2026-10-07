package com.hrms.cms.service;

import com.hrms.cms.dto.DuplicateFilingResponse;
import com.hrms.cms.dto.DuplicateFilingResponse.Match;
import com.hrms.cms.entity.AssistanceComplainantHistory;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.AssistanceComplainantHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DuplicateFilingDetectionService}: the scope boundary, the PII boundary, and the identity rule.
 *
 * <h2>What is actually at stake, and why the negative tests are the real tests</h2>
 * This service surfaces one identified person's OTHER complaints and produces a count that may support a
 * statutory {@code 16(2)(b)} "frivolous or vexatious" determination. Four failures here are worse than
 * the feature not existing:
 * <ol>
 *   <li>SHOWING AN OUT-OF-SCOPE COMPLAINT. An officer learning of a complaint in another department
 *       through this panel is a cross-department PII leak, and this repo has already had to fix leaks in
 *       two endpoints.</li>
 *   <li>LEAKING A FIELD. A name, email, phone, address, account number or narrative snippet in the
 *       response is the same leak by a different route.</li>
 *   <li>MATCHING STRANGERS TOGETHER. The placeholder phone {@code 9876543210} sits on 3,527 complaints
 *       across 3,427 distinct emails; a read that sought the suppression sentinel would return most of
 *       the register as one person's filing history AND feed it into a vexatious count.</li>
 *   <li>A FALSE DENOMINATOR. A complaint counted twice, or a truncated read reported as a total, makes
 *       "8 lifetime filings" a lie — and it is a lie an officer may cite in a closure note.</li>
 * </ol>
 * So the cases below are overwhelmingly about ABSENCE. A test proving results appear is worthless here:
 * MEASURED against {@code cms_db}, ZERO complainant emails span more than one department, so the scope
 * filter removes nothing on real data and a positive-only test would pass identically with
 * {@code inScope} deleted. Every scope case therefore constructs a cross-department fixture explicitly.
 *
 * <h2>Mocked, not sliced</h2>
 * H2 is not a dependency of this module, so a {@code @DataJpaTest} cannot run and a JPA slice needs the
 * live MySQL. Every rule under test here is Java — the scope filter, the role-to-department union, the
 * de-duplication, the window arithmetic, the entity alias match, the sentinel refusal — so mocks prove
 * them exactly. The one thing mocks cannot prove is the index the query uses; that is covered by the
 * {@code EXPLAIN} reasoning recorded alongside the migration.
 *
 * <h2>MUTATION-CHECKED</h2>
 * Deliberate breakages applied, with the observed result:
 * <ul>
 *   <li>{@code inScope} returning {@code true} unconditionally (the scope filter removed) → 10 failures
 *       across {@code ScopeIsEnforced}, including all seven per-role negatives plus
 *       {@code sentinelDepartmentIsNeverInScope} and the non-maintainable scope case. CONFIRMED, and
 *       this is the mutation that matters most: it is the shape a cross-department PII leak takes.</li>
 *   <li>{@code readHistory} keyed on an incrementing counter instead of the complaint number
 *       (de-duplication removed) → exactly 1 failure,
 *       {@code complaintMatchedByBothKeysIsCountedOnce}. CONFIRMED.</li>
 * </ul>
 * Mutations covered by construction rather than re-run individually: the {@code KEY_ABSENT} refusal in
 * {@code readHistory} is pinned by {@code sentinelKeysAreNeverSought}'s {@code never()} verifications; the
 * sentinel-to-sentinel entity guard by {@code entitylessComplaintsAreNotDuplicatesOfEachOther}; the window
 * bound by {@code complaintOutsideTheWindowIsNotADuplicate}; the window ANCHOR by
 * {@code windowIsAnchoredOnTheSubjectsOwnFilingDate}, whose subject is six months old so a
 * {@code now()}-anchored window returns nothing; and the response field set by
 * {@code matchExposesExactlyFiveNonIdentifyingFields}, which enumerates the record's components by
 * reflection and so fails on any ADDED field rather than only on ones somebody thought to forbid.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DuplicateFilingDetectionService")
class DuplicateFilingDetectionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 10, 0);
    private static final String SUBJECT = "N2026CEPC0001";
    private static final String EMAIL = "repeat.filer@example.com";
    private static final String PHONE = "9812345601";

    @Mock
    private AssistanceComplainantHistoryRepository historyRepository;

    private DuplicateFilingDetectionService service;

    @BeforeEach
    void setUp() {
        service = new DuplicateFilingDetectionService(historyRepository);
        enable(true);
        ReflectionTestUtils.setField(service, "configuredWindowDays", 30);
    }

    private void enable(boolean on) {
        ReflectionTestUtils.setField(service, "assistanceEnabled", on);
        ReflectionTestUtils.setField(service, "duplicateDetectionEnabled", on);
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /** The complaint on screen: CEPC, HDFC, filed at NOW, carrying both contacts. */
    private Complaint subject() {
        return Complaint.builder()
                .complaintNumber(SUBJECT)
                .complainantEmail(EMAIL)
                .complainantPhone(PHONE)
                .entityCode("HDFC Bank")
                .department("CEPC")
                .filedAt(NOW)
                .createdAt(NOW)
                .build();
    }

    private AssistanceComplainantHistory row(String number, String department, String entityKey,
                                             LocalDateTime filedAt) {
        return AssistanceComplainantHistory.builder()
                .complaintNumber(number)
                .emailKey(EMAIL)
                .phoneKey(PHONE)
                .entityKey(entityKey)
                .department(department)
                .filedAt(filedAt)
                .status("pending")
                .nonMaintainable(AssistanceComplainantHistory.FLAG_NO)
                .refreshedAt(NOW)
                .build();
    }

    /** Stubs the email seek. The phone seek returns nothing unless a test says otherwise. */
    private void onEmailSeek(AssistanceComplainantHistory... rows) {
        when(historyRepository.findByEmailKey(eq(EMAIL), any(Pageable.class)))
                .thenReturn(List.of(rows));
        when(historyRepository.findByPhoneKey(anyString(), any(Pageable.class)))
                .thenReturn(List.of());
    }

    // ═══ THE SCOPE BOUNDARY ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("the department scope is enforced, and it fails closed")
    class ScopeIsEnforced {

        /**
         * THE CENTRAL NEGATIVE TEST, run once per role family.
         *
         * <p>The complainant has two earlier complaints against the same entity inside the window: one in
         * the caller's own department and one in a department the caller's role has nothing to do with.
         * The out-of-scope one must be ABSENT from {@code matches}, ABSENT from {@code duplicateCount},
         * and ABSENT from {@code lifetimeFilings} — not merely unrendered. The in-scope one must still be
         * there, so the test cannot pass by the service simply returning nothing.
         *
         * <p>Parameterised over the five role prefixes the register actually contains plus the two
         * measured overrides, because a scope filter that is right for CEPC_DO and wrong for ORBIO is
         * still a leak. The roles are real names from the register, not invented ones.
         */
        @ParameterizedTest(name = "{0} sees only {1}, never the {2} complaint")
        @CsvSource({
                "CEPC_DO,       CEPC, RBIO",
                "CEPD_OFFICER,  CEPC, RBIO",
                "DO,            CEPC, RBIO",
                "RBIO_OFFICER,  RBIO, CEPC",
                "ORBIO_ADMIN,   RBIO, CRPC",
                "CRPC_REVIEWER, CRPC, CEPC",
                "DEO,           CRPC, RBIO"
        })
        void outOfScopeComplaintIsAbsentPerRole(String role, String ownDept, String foreignDept) {
            Complaint complaint = subject();
            complaint.setDepartment(ownDept);

            onEmailSeek(
                    row(SUBJECT, ownDept, "HDFC BANK", NOW),
                    row("N-OWN-EARLIER", ownDept, "HDFC BANK", NOW.minusDays(3)),
                    row("N-FOREIGN", foreignDept, "HDFC BANK", NOW.minusDays(4)));

            DuplicateFilingResponse response = service.detect(complaint, Set.of(role));

            assertThat(response.matches())
                    .extracting(Match::complaintNumber)
                    .containsExactly("N-OWN-EARLIER")
                    .doesNotContain("N-FOREIGN");
            assertThat(response.duplicateCount()).isEqualTo(1);
            // The foreign complaint is out of the DENOMINATOR too, not just the rendered list. Subject +
            // own earlier = 2.
            assertThat(response.repeat().lifetimeFilings()).isEqualTo(2);
            // And the officer is TOLD their view was narrowed, so a scoped count is not mistaken for a
            // lifetime total in a 16(2)(b) note.
            assertThat(response.repeat().scopeLimited()).isTrue();
            // The foreign department must not appear anywhere in the payload.
            assertThat(response.matches()).extracting(Match::department).doesNotContain(foreignDept);
        }

        @Test
        @DisplayName("the '*' sentinel department is in nobody's scope")
        void sentinelDepartmentIsNeverInScope() {
            // 23 of 4403 complaints carry no department. An unprovable scope on a PII-bearing read
            // excludes; showing it to everyone is how a leak is introduced by a NULL.
            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                    row("N-NO-DEPT", AssistanceComplainantHistory.KEY_ABSENT, "HDFC BANK",
                            NOW.minusDays(2)));

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            assertThat(response.matches()).isEmpty();
            assertThat(response.duplicateCount()).isZero();
            assertThat(response.repeat().lifetimeFilings()).isEqualTo(1);
            assertThat(response.repeat().scopeLimited()).isTrue();
        }

        @ParameterizedTest(name = "a caller holding only {0} gets no signal at all")
        @ValueSource(strings = {"CITIZEN", "RE_USER", "SOME_FUTURE_ROLE", "ADMIN", "", "   "})
        @DisplayName("a role that maps to no department gets no signal — fails CLOSED")
        void unknownRoleGetsNoScope(String role) {
            // ADMIN is in this list deliberately: it is a real role in the register and the prefix map
            // does NOT give it a department. Admitting it would be a decision to let a global role read
            // every department's complainants, which is not a decision this feature may make silently.
            DuplicateFilingResponse response = service.detect(subject(), Set.of(role));

            assertThat(response.available()).isFalse();
            assertThat(response.matches()).isEmpty();
            assertThat(response.repeat()).isNull();
            // Not merely filtered — the rows are never even READ, so a caller with no scope cannot cause
            // a PII-bearing table to be touched.
            verifyNoInteractions(historyRepository);
        }

        @Test
        @DisplayName("a citizen role gets no signal (the matcher is the floor, this is the second layer)")
        void citizenRoleGetsNoScope() {
            DuplicateFilingResponse response = service.detect(subject(), Set.of("CITIZEN"));

            assertThat(response.available()).isFalse();
            assertThat(response.matches()).isEmpty();
            verifyNoInteractions(historyRepository);
        }

        @Test
        @DisplayName("a null role set gets no signal")
        void nullRolesGetNoScope() {
            assertThat(service.detect(subject(), null).matches()).isEmpty();
            verifyNoInteractions(historyRepository);
        }

        @Test
        @DisplayName("an empty role set gets no signal")
        void emptyRolesGetNoScope() {
            assertThat(service.detect(subject(), Set.of()).matches()).isEmpty();
            verifyNoInteractions(historyRepository);
        }

        @Test
        @DisplayName("a multi-role officer sees the UNION of their departments, not an arbitrary one")
        void multiRoleOfficerSeesTheUnion() {
            // RequestIdentity.primaryRole is roles.iterator().next() over a HashSet, so keying on one
            // role would give this officer a different scope on a different pod. The union is the only
            // stable reading, and it is also the correct one: holding both roles IS responsibility for
            // both departments.
            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                    row("N-CEPC", "CEPC", "HDFC BANK", NOW.minusDays(2)),
                    row("N-RBIO", "RBIO", "HDFC BANK", NOW.minusDays(3)),
                    row("N-CRPC", "CRPC", "HDFC BANK", NOW.minusDays(4)));

            DuplicateFilingResponse response =
                    service.detect(subject(), Set.of("CEPC_DO", "RBIO_OFFICER"));

            assertThat(response.matches())
                    .extracting(Match::complaintNumber)
                    .containsExactly("N-CEPC", "N-RBIO");
            // CRPC is held by neither role, so it stays out — the union widens to what is held and no
            // further.
            assertThat(response.matches()).extracting(Match::complaintNumber).doesNotContain("N-CRPC");
            assertThat(response.repeat().scopeLimited()).isTrue();
        }

        @Test
        @DisplayName("scopeLimited is FALSE when nothing was withheld, so the flag means something")
        void scopeLimitedIsFalseWhenNothingWasWithheld() {
            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                    row("N-OWN", "CEPC", "HDFC BANK", NOW.minusDays(2)));

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            assertThat(response.repeat().scopeLimited()).isFalse();
            assertThat(response.repeat().lifetimeFilings()).isEqualTo(2);
        }

        @Test
        @DisplayName("role matching is case-insensitive and trimmed, so a token's casing is not a bypass")
        void roleTokensAreNormalised() {
            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                    row("N-OWN", "CEPC", "HDFC BANK", NOW.minusDays(2)));

            assertThat(service.detect(subject(), Set.of("  cepc_do  ")).matches()).hasSize(1);
        }

        @Test
        @DisplayName("a pathological role list cannot widen the scope beyond the cap")
        void roleListIsCapped() {
            Set<String> many = new java.util.HashSet<>();
            for (int i = 0; i < DuplicateFilingDetectionService.MAX_ROLES * 3; i++) {
                many.add("ZZZ_FILLER_" + i);
            }
            // Sorted-then-capped, so the surviving roles are deterministic. Every filler maps to no
            // department, so the answer is no scope — and crucially it is the SAME answer on every JVM.
            assertThat(service.departmentsFor(many)).isEmpty();
        }
    }

    // ═══ THE PII BOUNDARY ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("the response carries no identifying field")
    class PrivacyBoundary {

        /**
         * Pins the wire shape by REFLECTION over the record's components, not by reading a sample
         * payload. A field-by-field assertion would pass a new component nobody asserted on; this fails
         * the moment anyone adds one, which is the point — the privacy argument is about the SET of
         * fields, so the test has to be about the set.
         */
        @Test
        @DisplayName("Match exposes exactly five fields and none of them identifies a person")
        void matchExposesExactlyFiveNonIdentifyingFields() {
            List<String> components = new ArrayList<>();
            for (RecordComponent component : Match.class.getRecordComponents()) {
                components.add(component.getName());
            }

            assertThat(components).containsExactlyInAnyOrder(
                    "complaintNumber", "filedAt", "entityKey", "status", "department");

            // Named explicitly rather than inferred from the count, so a reader of a failure sees WHICH
            // forbidden field arrived.
            assertThat(components).doesNotContain(
                    "complainantName", "complainantEmail", "complainantPhone", "complainantAddress",
                    "email", "phone", "name", "address", "accountNumber", "cardNumber",
                    "subject", "description", "snippet", "narrative", "excerpt", "reliefSought");
        }

        @Test
        @DisplayName("RepeatComplainant carries counts only — no verdict, no score, no risk band")
        void repeatComplainantCarriesNoVerdict() {
            List<String> components = new ArrayList<>();
            for (RecordComponent component :
                    DuplicateFilingResponse.RepeatComplainant.class.getRecordComponents()) {
                components.add(component.getName());
            }

            assertThat(components).containsExactlyInAnyOrder(
                    "lifetimeFilings", "nonMaintainableClosures", "windowFilings", "scopeLimited");
            // The 16(2)(b) determination is a statutory discretion. A server field expressing it would
            // mean the determination was made by a constant and countersigned by the officer.
            assertThat(components).doesNotContain(
                    "vexatious", "frivolous", "verdict", "score", "risk", "riskLevel", "band",
                    "recommendation", "shouldClose", "decision");
        }

        @Test
        @DisplayName("the stored contact keys never reach the response, even though the rows hold them")
        void contactKeysStayOnTheServer() {
            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                    row("N-EARLIER", "CEPC", "HDFC BANK", NOW.minusDays(2)));

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            assertThat(response.matches()).hasSize(1);
            // The row that produced this match carries EMAIL and PHONE. Asserting on the whole rendered
            // payload rather than on absent getters, because the failure being guarded is a field being
            // ADDED that happens to carry them.
            assertThat(response.toString()).doesNotContain(EMAIL).doesNotContain(PHONE);
        }
    }

    // ═══ THE IDENTITY RULE ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("identity is email OR phone, with the suppression sentinel refused")
    class IdentityRule {

        @Test
        @DisplayName("the KEY_ABSENT sentinel is never sought on either key")
        void sentinelKeysAreNeverSought() {
            // '*' is shared by every contactless complaint and by all 3699 rows the fan-out guard
            // suppressed. Seeking it would return most of the register as one person's filing history and
            // feed it into a vexatious count. This is the single worst outcome the feature could produce.
            Complaint complaint = subject();
            complaint.setComplainantEmail("   ");
            complaint.setComplainantPhone(null);

            DuplicateFilingResponse response = service.detect(complaint, Set.of("CEPC_DO"));

            assertThat(response.available()).isFalse();
            verify(historyRepository, never())
                    .findByEmailKey(eq(AssistanceComplainantHistory.KEY_ABSENT), any());
            verify(historyRepository, never())
                    .findByPhoneKey(eq(AssistanceComplainantHistory.KEY_ABSENT), any());
            verify(historyRepository, never()).findByEmailKey(anyString(), any());
            verify(historyRepository, never()).findByPhoneKey(anyString(), any());
        }

        @Test
        @DisplayName("a complaint matched by BOTH keys is counted once, so the denominator is honest")
        void complaintMatchedByBothKeysIsCountedOnce() {
            AssistanceComplainantHistory subjectRow = row(SUBJECT, "CEPC", "HDFC BANK", NOW);
            AssistanceComplainantHistory shared = row("N-BOTH", "CEPC", "HDFC BANK", NOW.minusDays(2));

            // The same complaint comes back from both seeks, which is the NORMAL case for anyone who
            // filed with both contacts. Counting it twice would inflate both the duplicate count and the
            // lifetime total — a false denominator in a number that may reach a closure note.
            when(historyRepository.findByEmailKey(eq(EMAIL), any(Pageable.class)))
                    .thenReturn(List.of(subjectRow, shared));
            when(historyRepository.findByPhoneKey(eq(PHONE), any(Pageable.class)))
                    .thenReturn(List.of(subjectRow, shared));

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            assertThat(response.duplicateCount()).isEqualTo(1);
            assertThat(response.matches()).extracting(Match::complaintNumber).containsExactly("N-BOTH");
            assertThat(response.repeat().lifetimeFilings()).isEqualTo(2);
        }

        @Test
        @DisplayName("phone-only recall: a complaint sharing only the phone is still found")
        void phoneOnlyMatchIsFound() {
            // The measured reason the brief insists on OR: the existing complainant-history rail signal
            // matches email alone and says so. Phone matching adds 32 complaints of real recall
            // (259 against 227) once placeholders are suppressed.
            when(historyRepository.findByEmailKey(eq(EMAIL), any(Pageable.class)))
                    .thenReturn(List.of(row(SUBJECT, "CEPC", "HDFC BANK", NOW)));
            when(historyRepository.findByPhoneKey(eq(PHONE), any(Pageable.class)))
                    .thenReturn(List.of(AssistanceComplainantHistory.builder()
                            .complaintNumber("N-PHONE-ONLY")
                            .emailKey("a.different.address@example.com")
                            .phoneKey(PHONE)
                            .entityKey("HDFC BANK")
                            .department("CEPC")
                            .filedAt(NOW.minusDays(5))
                            .status("pending")
                            .nonMaintainable(AssistanceComplainantHistory.FLAG_NO)
                            .refreshedAt(NOW)
                            .build()));

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            assertThat(response.matches())
                    .extracting(Match::complaintNumber).containsExactly("N-PHONE-ONLY");
        }

        @ParameterizedTest(name = "phone \"{0}\" normalises to the same identity")
        @ValueSource(strings = {"9812345601", "+91 9812345601", "09812345601", "98123-45601",
                "  +91-98123 45601  "})
        @DisplayName("phone formatting variants are one identity")
        void phoneVariantsAreOneIdentity(String typed) {
            Complaint complaint = subject();
            complaint.setComplainantPhone(typed);
            complaint.setComplainantEmail(null);

            when(historyRepository.findByPhoneKey(eq(PHONE), any(Pageable.class)))
                    .thenReturn(List.of(
                            row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                            row("N-EARLIER", "CEPC", "HDFC BANK", NOW.minusDays(2))));

            assertThat(service.detect(complaint, Set.of("CEPC_DO")).duplicateCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("email matching is case-insensitive, because the key is lower-cased on write")
        void emailMatchingIsCaseInsensitive() {
            Complaint complaint = subject();
            complaint.setComplainantEmail("  Repeat.Filer@Example.COM  ");

            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                    row("N-EARLIER", "CEPC", "HDFC BANK", NOW.minusDays(2)));

            assertThat(service.detect(complaint, Set.of("CEPC_DO")).duplicateCount()).isEqualTo(1);
        }
    }

    // ═══ THE DUPLICATE RULE ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("the duplicate rule: same entity, inside the window, strictly earlier")
    class DuplicateRule {

        @Test
        @DisplayName("the subject complaint is never its own duplicate")
        void subjectIsNotItsOwnDuplicate() {
            onEmailSeek(row(SUBJECT, "CEPC", "HDFC BANK", NOW));

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            assertThat(response.duplicateCount()).isZero();
            assertThat(response.available()).isTrue();
            // Still a REAL answer: "we looked and there are none" is evidence, unlike "we did not look".
            assertThat(response.repeat().lifetimeFilings()).isEqualTo(1);
        }

        @Test
        @DisplayName("a complaint outside the window is not a duplicate but still counts for lifetime")
        void complaintOutsideTheWindowIsNotADuplicate() {
            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                    row("N-INSIDE", "CEPC", "HDFC BANK", NOW.minusDays(29)),
                    row("N-OUTSIDE", "CEPC", "HDFC BANK", NOW.minusDays(31)));

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            assertThat(response.matches())
                    .extracting(Match::complaintNumber).containsExactly("N-INSIDE");
            assertThat(response.duplicateCount()).isEqualTo(1);
            // The lifetime figure is the whole scoped history, window or not — that is what makes it a
            // LIFETIME count and the 16(2)(b) evidence rather than a restatement of the duplicate count.
            assertThat(response.repeat().lifetimeFilings()).isEqualTo(3);
            assertThat(response.repeat().windowFilings()).isEqualTo(1);
        }

        @Test
        @DisplayName("a LATER complaint is not an earlier filing")
        void laterComplaintIsNotADuplicate() {
            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                    row("N-LATER", "CEPC", "HDFC BANK", NOW.plusDays(2)));

            assertThat(service.detect(subject(), Set.of("CEPC_DO")).duplicateCount()).isZero();
        }

        @Test
        @DisplayName("a different entity is not a duplicate, but still a window filing")
        void differentEntityIsNotADuplicate() {
            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                    row("N-OTHER-BANK", "CEPC", "ICICI BANK", NOW.minusDays(2)));

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            assertThat(response.duplicateCount()).isZero();
            // Carried separately on purpose: a complainant filing against six banks in a week is a
            // different pattern from one filing six times about one bank, and collapsing them hides which.
            assertThat(response.repeat().windowFilings()).isEqualTo(1);
        }

        @Test
        @DisplayName("two entity-less complaints are NOT duplicates of each other")
        void entitylessComplaintsAreNotDuplicatesOfEachOther() {
            // 290 complaints carry no entity_code. Treating two unknowns as equal would group a
            // complainant's unrelated filings into a false duplicate cluster.
            Complaint complaint = subject();
            complaint.setEntityCode(null);

            onEmailSeek(
                    row(SUBJECT, "CEPC", AssistanceComplainantHistory.KEY_ABSENT, NOW),
                    row("N-ALSO-NONE", "CEPC", AssistanceComplainantHistory.KEY_ABSENT,
                            NOW.minusDays(2)));

            DuplicateFilingResponse response = service.detect(complaint, Set.of("CEPC_DO"));

            assertThat(response.duplicateCount()).isZero();
            assertThat(response.repeat().lifetimeFilings()).isEqualTo(2);
        }

        @Test
        @DisplayName("the entity ALIAS table is honoured: PNB matches Punjab National Bank")
        void entityAliasesMatch() {
            // AssistanceEntityAliasNormaliser is reused on both sides. The register spells one bank both
            // ways and UPPER() merges neither — they share no character position.
            Complaint complaint = subject();
            complaint.setEntityCode("PNB");

            onEmailSeek(
                    row(SUBJECT, "CEPC", "PUNJAB NATIONAL BANK", NOW),
                    row("N-FULL-NAME", "CEPC", "PUNJAB NATIONAL BANK", NOW.minusDays(2)));

            assertThat(service.detect(complaint, Set.of("CEPC_DO")).matches())
                    .extracting(Match::complaintNumber).containsExactly("N-FULL-NAME");
        }

        @Test
        @DisplayName("the window is anchored on the SUBJECT's filing date, not on today")
        void windowIsAnchoredOnTheSubjectsOwnFilingDate() {
            // An officer reviewing a complaint filed six months ago must see the duplicates that existed
            // AROUND IT, not the empty set "30 days from today" would produce.
            LocalDateTime longAgo = NOW.minusMonths(6);
            Complaint complaint = subject();
            complaint.setFiledAt(longAgo);
            complaint.setCreatedAt(longAgo);

            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", longAgo),
                    row("N-NEAR-IT", "CEPC", "HDFC BANK", longAgo.minusDays(5)));

            assertThat(service.detect(complaint, Set.of("CEPC_DO")).duplicateCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("matches are newest first, with a stable tiebreak")
        void matchesAreOrderedNewestFirst() {
            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                    row("N-OLD", "CEPC", "HDFC BANK", NOW.minusDays(20)),
                    row("N-NEW", "CEPC", "HDFC BANK", NOW.minusDays(1)),
                    row("N-MID", "CEPC", "HDFC BANK", NOW.minusDays(10)));

            assertThat(service.detect(subject(), Set.of("CEPC_DO")).matches())
                    .extracting(Match::complaintNumber)
                    .containsExactly("N-NEW", "N-MID", "N-OLD");
        }

        @Test
        @DisplayName("the response cap truncates the LIST but never the COUNT")
        void capTruncatesTheListNotTheCount() {
            List<AssistanceComplainantHistory> rows = new ArrayList<>();
            rows.add(row(SUBJECT, "CEPC", "HDFC BANK", NOW));
            int total = DuplicateFilingDetectionService.MAX_MATCHES_RETURNED + 5;
            for (int i = 0; i < total; i++) {
                rows.add(row("N-" + String.format("%03d", i), "CEPC", "HDFC BANK",
                        NOW.minusDays(1).minusMinutes(i)));
            }
            when(historyRepository.findByEmailKey(eq(EMAIL), any(Pageable.class))).thenReturn(rows);
            when(historyRepository.findByPhoneKey(anyString(), any(Pageable.class)))
                    .thenReturn(List.of());

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            assertThat(response.matches()).hasSize(DuplicateFilingDetectionService.MAX_MATCHES_RETURNED);
            // The denominator stays honest: the cap costs rows, never the count.
            assertThat(response.duplicateCount()).isEqualTo(total);
        }
    }

    // ═══ THE REPEAT-COMPLAINANT SIGNAL ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("the repeat-complainant signal: counts, with the 16(2)(b) evidence")
    class RepeatSignal {

        @Test
        @DisplayName("lifetime filings INCLUDE the complaint on screen")
        void lifetimeIncludesTheCurrentComplaint() {
            // "This is their 8th filing" is the sentence an officer needs, and a count excluding the case
            // in hand would be off by one against the list the officer can see.
            onEmailSeek(
                    row(SUBJECT, "CEPC", "HDFC BANK", NOW),
                    row("N-1", "CEPC", "HDFC BANK", NOW.minusDays(40)),
                    row("N-2", "CEPC", "ICICI BANK", NOW.minusDays(80)));

            assertThat(service.detect(subject(), Set.of("CEPC_DO")).repeat().lifetimeFilings())
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("non-maintainable closures are counted as a SUBSET of lifetime filings")
        void nonMaintainableIsASubset() {
            AssistanceComplainantHistory nm = row("N-NM", "CEPC", "HDFC BANK", NOW.minusDays(50));
            nm.setNonMaintainable(AssistanceComplainantHistory.FLAG_YES);

            onEmailSeek(row(SUBJECT, "CEPC", "HDFC BANK", NOW), nm);

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            assertThat(response.repeat().lifetimeFilings()).isEqualTo(2);
            assertThat(response.repeat().nonMaintainableClosures()).isEqualTo(1);
            // A SUBSET, never a second bucket: a client adding the two would double-count.
            assertThat(response.repeat().nonMaintainableClosures())
                    .isLessThanOrEqualTo(response.repeat().lifetimeFilings());
        }

        @Test
        @DisplayName("an out-of-scope non-maintainable closure is not counted either")
        void outOfScopeNonMaintainableIsNotCounted() {
            AssistanceComplainantHistory foreign = row("N-FOREIGN-NM", "RBIO", "HDFC BANK",
                    NOW.minusDays(50));
            foreign.setNonMaintainable(AssistanceComplainantHistory.FLAG_YES);

            onEmailSeek(row(SUBJECT, "CEPC", "HDFC BANK", NOW), foreign);

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            // The scope filter runs BEFORE any counting, so an out-of-scope row cannot reach the
            // 16(2)(b) evidence either. A leak into a COUNT is still a leak.
            assertThat(response.repeat().nonMaintainableClosures()).isZero();
            assertThat(response.repeat().lifetimeFilings()).isEqualTo(1);
        }
    }

    // ═══ DEGRADATION ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("degrades to silence, never to an error")
    class Degradation {

        @Test
        @DisplayName("either switch off yields the empty shape with no query")
        void switchesGateTheRead() {
            ReflectionTestUtils.setField(service, "duplicateDetectionEnabled", false);
            assertThat(service.detect(subject(), Set.of("CEPC_DO")).available()).isFalse();

            enable(true);
            ReflectionTestUtils.setField(service, "assistanceEnabled", false);
            assertThat(service.detect(subject(), Set.of("CEPC_DO")).available()).isFalse();

            verifyNoInteractions(historyRepository);
        }

        @Test
        @DisplayName("a null complaint yields the empty shape")
        void nullComplaintIsNotAnError() {
            assertThat(service.detect(null, Set.of("CEPC_DO")).available()).isFalse();
            verifyNoInteractions(historyRepository);
        }

        @Test
        @DisplayName("an absent table (the live shipped state) yields the empty shape, not a 500")
        void repositoryFailureDegradesQuietly() {
            when(historyRepository.findByEmailKey(anyString(), any(Pageable.class)))
                    .thenThrow(new RuntimeException(
                            "Table 'cms_db.ASSISTANCE_COMPLAINANT_HISTORY' doesn't exist"));

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            assertThat(response.available()).isFalse();
            assertThat(response.matches()).isEmpty();
            assertThat(response.repeat()).isNull();
        }

        @Test
        @DisplayName("an empty projection yields the empty shape")
        void emptyProjectionDegradesQuietly() {
            when(historyRepository.findByEmailKey(anyString(), any(Pageable.class)))
                    .thenReturn(List.of());
            when(historyRepository.findByPhoneKey(anyString(), any(Pageable.class)))
                    .thenReturn(List.of());

            assertThat(service.detect(subject(), Set.of("CEPC_DO")).available()).isFalse();
        }

        @Test
        @DisplayName("a row with no filing date is dropped rather than defaulted")
        void rowWithoutFiledAtIsDropped() {
            AssistanceComplainantHistory broken = row("N-BROKEN", "CEPC", "HDFC BANK", null);

            onEmailSeek(row(SUBJECT, "CEPC", "HDFC BANK", NOW), broken);

            DuplicateFilingResponse response = service.detect(subject(), Set.of("CEPC_DO"));

            // Guessing a date would decide a duplicate finding.
            assertThat(response.duplicateCount()).isZero();
            assertThat(response.repeat().lifetimeFilings()).isEqualTo(1);
        }

        @ParameterizedTest(name = "a configured window of {0} days is clamped to {1}")
        @CsvSource({"0, 1", "-5, 1", "400, 365", "30, 30", "1, 1", "365, 365"})
        @DisplayName("the window is clamped, and the clamped value is what travels on the wire")
        void windowIsClamped(int configured, int expected) {
            ReflectionTestUtils.setField(service, "configuredWindowDays", configured);
            onEmailSeek(row(SUBJECT, "CEPC", "HDFC BANK", NOW));

            assertThat(service.detect(subject(), Set.of("CEPC_DO")).windowDays()).isEqualTo(expected);
        }
    }
}
