package com.hrms.cms.service;

import com.hrms.cms.entity.AssistanceComplainantHistory;
import com.hrms.cms.entity.AssistanceJobLock;
import com.hrms.cms.repository.AssistanceComplainantHistoryRepository;
import com.hrms.cms.repository.AssistanceJobLockRepository;
import com.hrms.cms.repository.ComplainantHistorySourceRepository;
import com.hrms.cms.repository.projection.ComplainantHistoryProjections.ComplainantRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AssistanceComplainantHistoryRefreshService}: the fan-out guard, the normalisation, the lease.
 *
 * <h2>The fan-out guard is the whole feature, and these are the tests that prove it</h2>
 * MEASURED against {@code cms_db}: the phone {@code 9876543210} appears on 3,527 complaints across 3,427
 * DISTINCT EMAILS. Matching on "email OR phone" without a guard flags 3,849 of 4,403 complaints (87%) as
 * duplicate filings; with the guard, 259 (5.9%). An 87% hit rate is not a signal, it is a banner an
 * officer would learn to ignore within a day — and the {@code 16(2)(b)} vexatious count it feeds would be
 * resting on a seeder's default value.
 *
 * <p>So {@link FanoutGuard} is the centre of this file. Note particularly
 * {@code placeholderPhoneIsSuppressedWhileRealPhonesSurvive}: a guard that suppressed EVERYTHING would
 * also produce a quiet feature, and would pass any test that only checked the placeholder was gone. Every
 * suppression case therefore asserts that a legitimate contact in the same pass SURVIVED.
 *
 * <h2>Mocked, not sliced</h2>
 * H2 is not a dependency of this module, so a {@code @DataJpaTest} cannot run. Every rule here is Java —
 * the two-pass fan-out measurement, the key normalisation, the three-marker non-maintainability test, the
 * sweep ordering, the lease contract — so mocks prove them exactly.
 *
 * <h2>MUTATION-CHECKED, AND IT FOUND A REAL BUG</h2>
 * These tests were written before the implementation was trusted, and on their first run they FAILED
 * against a genuine defect: {@code measureContactFanout} passed its two suppression sets to
 * {@code ContactFanoutIndex} in map order, which put the PHONE suppressions into the email field and vice
 * versa. The consequence was not cosmetic — on the real register the email direction is nearly inert, so
 * the swap effectively DISABLED the phone guard and would have silently restored the 87% false-positive
 * rate. It is called out in a comment at the call site now.
 *
 * <p>Deliberate breakages applied afterwards, with the observed result:
 * <ul>
 *   <li>{@code MAX_CONTACT_FANOUT} raised to {@code Integer.MAX_VALUE} (guard disabled) → 3 failures,
 *       including {@code placeholderPhoneIsSuppressedWhileRealPhonesSurvive} and
 *       {@code suppressionIsSymmetricForEmails}. CONFIRMED.</li>
 *   <li>the fan-out comparison changed from {@code >} to {@code >=} → exactly 1 failure,
 *       {@code contactWithExactlyThresholdPeersSurvives}. CONFIRMED.</li>
 *   <li>the sweep MOVED before the write loop → 1 failure,
 *       {@code sweepRunsOnlyAfterACompletePass}. CONFIRMED.</li>
 *   <li>the sweep DUPLICATED before the write loop while the original stayed → initially SURVIVED,
 *       because an {@code inOrder} check is satisfied by the later of two calls. A
 *       {@code times(1)} assertion was added to {@code sweepRunsOnlyAfterACompletePass} and the mutation
 *       is now caught. Recorded because it is the one mutation that got through first.</li>
 * </ul>
 * Mutations covered by construction rather than re-run individually: the lease name (pinned by name in
 * {@code leaseUsesItsOwnDistinctName}), the {@code 16(2)} prefix (pinned by
 * {@code clause162CountsAsNonMaintainable}), and the phone tail direction (pinned by
 * {@code phoneKeyIsTheLastTenDigits}, whose fixtures differ in their leading digits).
 *
 * <p>NOT covered, and said plainly rather than claimed: dropping {@code Locale.ROOT} from
 * {@code normaliseEmail} is invisible on an ASCII fixture. The Turkish-locale hazard it guards against
 * would need a test that sets the default locale, which would leak into every other test in the JVM.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssistanceComplainantHistoryRefreshService")
class AssistanceComplainantHistoryRefreshServiceTest {

    private static final LocalDateTime STAMP = LocalDateTime.of(2026, 6, 15, 10, 0);

    @Mock
    private ComplainantHistorySourceRepository sourceRepository;
    @Mock
    private AssistanceComplainantHistoryRepository projectionRepository;
    @Mock
    private AssistanceJobLockRepository lockRepository;

    private AssistanceComplainantHistoryRefreshService service;

    @BeforeEach
    void setUp() {
        service = new AssistanceComplainantHistoryRefreshService(
                sourceRepository, projectionRepository, lockRepository);
        ReflectionTestUtils.setField(service, "assistanceEnabled", true);
        ReflectionTestUtils.setField(service, "duplicateDetectionEnabled", true);
        ReflectionTestUtils.setField(service, "podIdentity", "test-pod");

        // Default: nothing already stored, so every upsert is an insert.
        when(projectionRepository.findByComplaintNumber(anyString())).thenReturn(Optional.empty());
        when(lockRepository.acquire(anyString(), any(), any(), anyString())).thenReturn(1);
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private ComplainantRow src(long id, String number, String email, String phone, String entity,
                               String department) {
        return new ComplainantRow(id, number, email, phone, entity, department, "pending",
                STAMP.minusDays(id), STAMP.minusDays(id), null, null, null);
    }

    /**
     * Serves the SAME page list to both of the service's two passes.
     *
     * <p>The service scans twice — once to measure the fan-out, once to write — so the stub must answer
     * {@code afterId = 0} repeatedly. Keyed on the cursor rather than counting invocations, which is what
     * lets one stub serve both passes without the test knowing how many there are.
     */
    private void onScan(List<ComplainantRow> rows) {
        when(sourceRepository.findComplainantRows(eq(0L), any(Pageable.class))).thenReturn(rows);
        long lastId = rows.isEmpty() ? 0L : rows.get(rows.size() - 1).complaintId();
        when(sourceRepository.findComplainantRows(eq(lastId), any(Pageable.class)))
                .thenReturn(List.of());
    }

    private List<AssistanceComplainantHistory> captureSaved() {
        ArgumentCaptor<AssistanceComplainantHistory> captor =
                ArgumentCaptor.forClass(AssistanceComplainantHistory.class);
        verify(projectionRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private AssistanceComplainantHistory savedFor(String complaintNumber) {
        for (AssistanceComplainantHistory row : captureSaved()) {
            if (complaintNumber.equals(row.getComplaintNumber())) {
                return row;
            }
        }
        return null;
    }

    // ═══ THE FAN-OUT GUARD ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("the contact fan-out guard")
    class FanoutGuard {

        /**
         * THE CENTRAL TEST. A placeholder phone shared by five different emails is suppressed, and a real
         * phone in the SAME pass is not.
         *
         * <p>The second half is what makes this a test rather than a tautology: a guard that suppressed
         * everything would also make the placeholder disappear, and would pass any assertion that only
         * looked for its absence.
         */
        @Test
        @DisplayName("a placeholder phone is suppressed while real phones in the same pass survive")
        void placeholderPhoneIsSuppressedWhileRealPhonesSurvive() {
            String placeholder = "9876543210";
            List<ComplainantRow> rows = new ArrayList<>();
            // Five distinct people sharing one number: above MAX_CONTACT_FANOUT of 3.
            for (int i = 1; i <= 5; i++) {
                rows.add(src(i, "N-PLACE-" + i, "person" + i + "@example.com", placeholder,
                        "HDFC Bank", "CEPC"));
            }
            // One genuine complainant with their own number, filing twice.
            rows.add(src(6, "N-REAL-1", "real@example.com", "9812345601", "HDFC Bank", "CEPC"));
            rows.add(src(7, "N-REAL-2", "real@example.com", "9812345601", "HDFC Bank", "CEPC"));
            onScan(rows);

            service.recompute(STAMP);

            // Suppressed: the placeholder contributes NO phone identity, so these five strangers can
            // never be matched to each other.
            for (int i = 1; i <= 5; i++) {
                assertThat(savedFor("N-PLACE-" + i).getPhoneKey())
                        .isEqualTo(AssistanceComplainantHistory.KEY_ABSENT);
                // Their EMAILS are untouched — each is still their own identity.
                assertThat(savedFor("N-PLACE-" + i).getEmailKey())
                        .isEqualTo("person" + i + "@example.com");
            }
            // NOT suppressed: the real complainant keeps their phone identity.
            assertThat(savedFor("N-REAL-1").getPhoneKey()).isEqualTo("9812345601");
            assertThat(savedFor("N-REAL-2").getPhoneKey()).isEqualTo("9812345601");
        }

        @Test
        @DisplayName("a phone shared by two addresses of the SAME person survives — that is the point")
        void phoneSharedByTwoPeopleSurvivesTheGuard() {
            // A person legitimately has a work address and a personal one. Measured: 16 phones in the
            // register carry more than one email, and suppressing those would be email-only matching,
            // which the brief rejects and which loses 32 complaints of real recall.
            onScan(List.of(
                    src(1, "N-WORK", "me@work.example.com", "9812345601", "HDFC Bank", "CEPC"),
                    src(2, "N-HOME", "me@home.example.com", "9812345601", "HDFC Bank", "CEPC")));

            service.recompute(STAMP);

            assertThat(savedFor("N-WORK").getPhoneKey()).isEqualTo("9812345601");
            assertThat(savedFor("N-HOME").getPhoneKey()).isEqualTo("9812345601");
        }

        @Test
        @DisplayName("a contact with EXACTLY the threshold number of peers survives (exclusive bound)")
        void contactWithExactlyThresholdPeersSurvives() {
            List<ComplainantRow> rows = new ArrayList<>();
            for (int i = 1; i <= AssistanceComplainantHistoryRefreshService.MAX_CONTACT_FANOUT; i++) {
                rows.add(src(i, "N-" + i, "addr" + i + "@example.com", "9812345601", "HDFC Bank",
                        "CEPC"));
            }
            onScan(rows);

            service.recompute(STAMP);

            assertThat(savedFor("N-1").getPhoneKey()).isEqualTo("9812345601");
        }

        @Test
        @DisplayName("the guard is SYMMETRIC: a shared email address is suppressed too")
        void suppressionIsSymmetricForEmails() {
            // A branch mailbox or a noreply@ address is the same failure wearing the other hat. Measured
            // today this direction is nearly inert — the largest fan-out behind one email is 4 distinct
            // phones — but that is a property of this dataset and not of email addresses.
            String shared = "branch.desk@example.com";
            List<ComplainantRow> rows = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                rows.add(src(i, "N-SHARED-" + i, shared, "981234560" + i, "HDFC Bank", "CEPC"));
            }
            rows.add(src(6, "N-OWN", "private@example.com", "9700000001", "HDFC Bank", "CEPC"));
            onScan(rows);

            service.recompute(STAMP);

            for (int i = 1; i <= 5; i++) {
                assertThat(savedFor("N-SHARED-" + i).getEmailKey())
                        .isEqualTo(AssistanceComplainantHistory.KEY_ABSENT);
                // Each still keeps their own PHONE identity, so they are not merged with each other.
                assertThat(savedFor("N-SHARED-" + i).getPhoneKey()).isEqualTo("981234560" + i);
            }
            assertThat(savedFor("N-OWN").getEmailKey()).isEqualTo("private@example.com");
        }

        @Test
        @DisplayName("a complaint whose ONLY contact was suppressed is dropped entirely")
        void complaintWithOnlySuppressedContactIsDropped() {
            // Post-guard state decides. Such a row has no identity this feature can speak about, and
            // storing it under two sentinels would leave it waiting to be pooled with every other
            // contactless stranger.
            String placeholder = "9876543210";
            List<ComplainantRow> rows = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                // No email at all — the phone is the only contact, and it is a placeholder.
                rows.add(src(i, "N-ONLY-PLACE-" + i, null, placeholder, "HDFC Bank", "CEPC"));
            }
            // Give the placeholder its email fan-out from other rows that DO carry an email.
            for (int i = 6; i <= 10; i++) {
                rows.add(src(i, "N-WITH-EMAIL-" + i, "p" + i + "@example.com", placeholder,
                        "HDFC Bank", "CEPC"));
            }
            onScan(rows);

            service.recompute(STAMP);

            for (int i = 1; i <= 5; i++) {
                assertThat(savedFor("N-ONLY-PLACE-" + i)).isNull();
            }
            // The ones carrying an email are still stored, keyed on it.
            assertThat(savedFor("N-WITH-EMAIL-6")).isNotNull();
            assertThat(savedFor("N-WITH-EMAIL-6").getPhoneKey())
                    .isEqualTo(AssistanceComplainantHistory.KEY_ABSENT);
        }

        @Test
        @DisplayName("a row carrying only ONE contact does not contribute to any fan-out count")
        void singleContactRowsDoNotInflateFanout() {
            // "How many distinct emails does this phone appear beside" cannot be answered by a complaint
            // that recorded no email. Counting such a row as a peer of nothing would make a placeholder
            // used on phone-only complaints look clean.
            List<ComplainantRow> rows = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                rows.add(src(i, "N-" + i, null, "9812345601", "HDFC Bank", "CEPC"));
            }
            onScan(rows);

            service.recompute(STAMP);

            // Ten phone-only rows produce a fan-out of ZERO distinct emails, so the phone survives.
            assertThat(savedFor("N-1").getPhoneKey()).isEqualTo("9812345601");
        }

        @Test
        @DisplayName("a complaint with NEITHER contact is dropped")
        void contactlessComplaintIsDropped() {
            onScan(List.of(src(1, "N-NOTHING", null, "   ", "HDFC Bank", "CEPC")));

            int written = service.recompute(STAMP);

            assertThat(written).isZero();
            verify(projectionRepository, never()).save(any());
        }
    }

    // ═══ NORMALISATION ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("key normalisation, applied on WRITE")
    class Normalisation {

        @ParameterizedTest(name = "\"{0}\" normalises to \"{1}\"")
        @CsvSource({
                "'  Repeat.Filer@Example.COM  ', repeat.filer@example.com",
                "'USER@EXAMPLE.COM',             user@example.com",
                "'already@lower.com',            already@lower.com"
        })
        @DisplayName("email is lower-cased and trimmed")
        void emailIsNormalised(String raw, String expected) {
            assertThat(AssistanceComplainantHistoryRefreshService.normaliseEmail(raw))
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "a blank email \"{0}\" is not an identity")
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("blank is not an identity, so 21 contactless complainants are not one person")
        void blankEmailIsNotAnIdentity(String raw) {
            // 21 rows store '' rather than NULL. Keying on the empty string would group every one of
            // those complainants together as one person.
            assertThat(AssistanceComplainantHistoryRefreshService.normaliseEmail(raw)).isNull();
        }

        @Test
        @DisplayName("a null email is not an identity")
        void nullEmailIsNotAnIdentity() {
            assertThat(AssistanceComplainantHistoryRefreshService.normaliseEmail(null)).isNull();
        }

        @ParameterizedTest(name = "phone \"{0}\" keys as \"{1}\"")
        @CsvSource({
                "'9812345601',          9812345601",
                "'+91 9812345601',      9812345601",
                "'09812345601',         9812345601",
                "'98123-45601',         9812345601",
                "'  +91-98123 45601  ', 9812345601",
                "'0091 98123 45601',    9812345601"
        })
        @DisplayName("the phone key is the LAST ten digits, non-digits stripped")
        void phoneKeyIsTheLastTenDigits(String raw, String expected) {
            assertThat(AssistanceComplainantHistoryRefreshService.normalisePhone(raw))
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "a short phone \"{0}\" is rejected, never padded")
        @ValueSource(strings = {"", "   ", "12345", "123456789", "+91 12345"})
        @DisplayName("a phone with fewer than ten digits is REJECTED, not padded or used short")
        void shortPhoneIsRejected(String raw) {
            // A 6-digit fragment would match a great many people, and matching strangers together is this
            // feature's only serious failure mode.
            assertThat(AssistanceComplainantHistoryRefreshService.normalisePhone(raw)).isNull();
        }

        @Test
        @DisplayName("the entity key goes through the EXISTING alias normaliser, not a second one")
        void entityKeyUsesTheSharedAliasNormaliser() {
            onScan(List.of(
                    src(1, "N-ABBREV", "a@example.com", "9812345601", "PNB", "CEPC"),
                    src(2, "N-FULL", "a@example.com", "9812345601", "Punjab National Bank", "CEPC")));

            service.recompute(STAMP);

            // Both spellings land on ONE key, which is what lets the read find them as one entity's
            // history. UPPER() alone would merge neither — they share no character position.
            assertThat(savedFor("N-ABBREV").getEntityKey()).isEqualTo("PUNJAB NATIONAL BANK");
            assertThat(savedFor("N-FULL").getEntityKey()).isEqualTo("PUNJAB NATIONAL BANK");
            assertThat(savedFor("N-ABBREV").getEntityKey())
                    .isEqualTo(AssistanceEntityAliasNormaliser.normalise("PNB"));
        }

        @Test
        @DisplayName("a blank entity becomes the sentinel, never the empty string")
        void blankEntityBecomesTheSentinel() {
            onScan(List.of(src(1, "N-NO-ENTITY", "a@example.com", "9812345601", null, "CEPC")));

            service.recompute(STAMP);

            assertThat(savedFor("N-NO-ENTITY").getEntityKey())
                    .isEqualTo(AssistanceComplainantHistory.KEY_ABSENT);
        }

        @Test
        @DisplayName("department is upper-cased (it is COMPARED) and status is not (it is DISPLAYED)")
        void comparedKeysAreFoldedAndDisplayedOnesAreNot() {
            ComplainantRow row = new ComplainantRow(1L, "N-CASE", "a@example.com", "9812345601",
                    "HDFC Bank", "cepc", "pending", STAMP, STAMP, null, null, null);
            onScan(List.of(row));

            service.recompute(STAMP);

            // Folded: the read compares it against role-derived departments, and a verbatim comparison
            // would fold in MySQL and not in Oracle.
            assertThat(savedFor("N-CASE").getDepartment()).isEqualTo("CEPC");
            // NOT folded: the register mixes 'pending' and 'NOT_OPENED', and folding would show an
            // officer a status spelled differently from the complaint grid beside it.
            assertThat(savedFor("N-CASE").getStatus()).isEqualTo("pending");
        }

        @Test
        @DisplayName("a blank department becomes the sentinel, which the read treats as out of scope")
        void blankDepartmentBecomesTheSentinel() {
            onScan(List.of(src(1, "N-NO-DEPT", "a@example.com", "9812345601", "HDFC Bank", null)));

            service.recompute(STAMP);

            assertThat(savedFor("N-NO-DEPT").getDepartment())
                    .isEqualTo(AssistanceComplainantHistory.KEY_ABSENT);
        }

        @Test
        @DisplayName("filedAt falls back to createdAt, and a row with neither is dropped")
        void filedAtFallsBackToCreatedAt() {
            ComplainantRow withCreatedOnly = new ComplainantRow(1L, "N-CREATED", "a@example.com",
                    "9812345601", "HDFC Bank", "CEPC", "pending", null, STAMP.minusDays(3),
                    null, null, null);
            ComplainantRow withNeither = new ComplainantRow(2L, "N-NEITHER", "b@example.com",
                    "9812345602", "HDFC Bank", "CEPC", "pending", null, null, null, null, null);
            onScan(List.of(withCreatedOnly, withNeither));

            service.recompute(STAMP);

            assertThat(savedFor("N-CREATED").getFiledAt()).isEqualTo(STAMP.minusDays(3));
            // A fabricated date would put the complaint inside or outside a 30-day window arbitrarily.
            assertThat(savedFor("N-NEITHER")).isNull();
        }
    }

    // ═══ THE 16(2)(b) EVIDENCE ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("the non-maintainable flag: three markers, OR'd")
    class NonMaintainableFlag {

        private void assertFlag(String determination, String cause, String clause, String expected) {
            ComplainantRow row = new ComplainantRow(1L, "N-FLAG", "a@example.com", "9812345601",
                    "HDFC Bank", "CEPC", "closed", STAMP, STAMP, determination, cause, clause);
            onScan(List.of(row));

            service.recompute(STAMP);

            assertThat(savedFor("N-FLAG").getNonMaintainable()).isEqualTo(expected);
        }

        @Test
        @DisplayName("maintainability_determination = NON_MAINTAINABLE sets the flag")
        void determinationSetsTheFlag() {
            assertFlag("NON_MAINTAINABLE", null, null, AssistanceComplainantHistory.FLAG_YES);
        }

        @Test
        @DisplayName("closure_cause = NON_MAINTAINABLE sets the flag")
        void closureCauseSetsTheFlag() {
            assertFlag(null, "NON_MAINTAINABLE", null, AssistanceComplainantHistory.FLAG_YES);
        }

        @ParameterizedTest(name = "closure_clause {0} sets the flag")
        @ValueSource(strings = {"16(2)(a)", "16(2)(b)", "16(2)", "  16(2)(c)  "})
        @DisplayName("a closure clause under 16(2) sets the flag")
        void clause162CountsAsNonMaintainable(String clause) {
            assertFlag(null, null, clause, AssistanceComplainantHistory.FLAG_YES);
        }

        @ParameterizedTest(name = "closure_clause {0} does NOT set the flag")
        @ValueSource(strings = {"15(1)(a)", "15(1)(b)", "16(3)", "16(1)"})
        @DisplayName("a clause outside 16(2) does not set the flag")
        void otherClausesDoNotSetTheFlag(String clause) {
            assertFlag(null, null, clause, AssistanceComplainantHistory.FLAG_NO);
        }

        @Test
        @DisplayName("the markers are compared case-insensitively and trimmed")
        void markersAreComparedLoosely() {
            assertFlag("  non_maintainable  ", null, null, AssistanceComplainantHistory.FLAG_YES);
        }

        @Test
        @DisplayName("an OPEN complaint is not non-maintainable")
        void openComplaintIsNotNonMaintainable() {
            assertFlag(null, null, null, AssistanceComplainantHistory.FLAG_NO);
        }

        @Test
        @DisplayName("MAINTAINABLE is not non-maintainable")
        void maintainableIsNotNonMaintainable() {
            assertFlag("MAINTAINABLE", "RESOLVED", "15(1)(a)",
                    AssistanceComplainantHistory.FLAG_NO);
        }
    }

    // ═══ THE LEASE, AND THE SWEEP ORDERING ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("the lease and the stale sweep")
    class LeaseAndSweep {

        @Test
        @DisplayName("the lease uses its OWN distinct name, not a sibling job's")
        void leaseUsesItsOwnDistinctName() {
            // Two jobs on one lease row means whichever fires second logs "another pod holds the lease"
            // and never runs — false, and the hardest kind of bug to see.
            onScan(List.of());
            service.refresh();

            verify(lockRepository).acquire(
                    eq(AssistanceJobLock.LOCK_NAME_COMPLAINANT_HISTORY_REFRESH),
                    any(), any(), anyString());
            verify(lockRepository, never()).acquire(
                    eq(AssistanceJobLock.LOCK_NAME_CLAUSE_AFFINITY_REFRESH), any(), any(), anyString());
            verify(lockRepository, never()).acquire(
                    eq(AssistanceJobLock.LOCK_NAME_NEXT_ACTION_REFRESH), any(), any(), anyString());
            verify(lockRepository, never()).acquire(
                    eq(AssistanceJobLock.LOCK_NAME_ENTITY_PATTERN_REFRESH), any(), any(), anyString());
        }

        @Test
        @DisplayName("losing the lease means doing nothing at all")
        void losingTheLeaseMeansDoNothing() {
            when(lockRepository.acquire(anyString(), any(), any(), anyString())).thenReturn(0);

            assertThat(service.refresh()).isZero();
            verifyNoInteractions(sourceRepository);
            verify(projectionRepository, never()).save(any());
            verify(projectionRepository, never()).deleteStale(any());
        }

        @Test
        @DisplayName("a lease FAILURE (missing row / unapplied migration) means doing nothing")
        void leaseFailureMeansDoNothing() {
            // Treating an error as permission to proceed would mean the one environment where the lock is
            // unavailable is the one where every pod rebuilds at once.
            when(lockRepository.acquire(anyString(), any(), any(), anyString()))
                    .thenThrow(new RuntimeException("Table 'ASSISTANCE_JOB_LOCK' doesn't exist"));

            assertThat(service.refresh()).isZero();
            verifyNoInteractions(sourceRepository);
            verify(projectionRepository, never()).save(any());
        }

        @Test
        @DisplayName("the lease is released even when the pass fails")
        void leaseIsAlwaysReleased() {
            when(sourceRepository.findComplainantRows(anyLong(), any(Pageable.class)))
                    .thenThrow(new RuntimeException("boom"));

            assertThat(service.refresh()).isZero();
            verify(lockRepository).release(
                    eq(AssistanceJobLock.LOCK_NAME_COMPLAINANT_HISTORY_REFRESH), any());
        }

        @Test
        @DisplayName("either switch off means no lease is taken and no query is issued")
        void switchesGateTheJob() {
            ReflectionTestUtils.setField(service, "duplicateDetectionEnabled", false);
            assertThat(service.refresh()).isZero();

            ReflectionTestUtils.setField(service, "duplicateDetectionEnabled", true);
            ReflectionTestUtils.setField(service, "assistanceEnabled", false);
            assertThat(service.refresh()).isZero();

            verifyNoInteractions(lockRepository);
            verifyNoInteractions(sourceRepository);
        }

        @Test
        @DisplayName("the sweep runs only AFTER a complete pass, stamped with the pass's own instant")
        void sweepRunsOnlyAfterACompletePass() {
            onScan(List.of(src(1, "N-1", "a@example.com", "9812345601", "HDFC Bank", "CEPC")));

            service.recompute(STAMP);

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(projectionRepository);
            order.verify(projectionRepository).save(any());
            order.verify(projectionRepository).deleteStale(STAMP);
            // EXACTLY ONCE, and this assertion was ADDED after a mutation survived: moving the sweep
            // before the write loop while LEAVING the one after it in place still satisfies the inOrder
            // check above, because the second call is genuinely after the save. An extra sweep on the
            // pass's own stamp would delete rows the pass had not yet rewritten, so "once" is part of the
            // contract and not a detail.
            verify(projectionRepository, org.mockito.Mockito.times(1)).deleteStale(any());
        }

        @Test
        @DisplayName("the sweep is SKIPPED when the pass fails, so a partial pass cannot delete history")
        void sweepIsSkippedOnAFailedPass() {
            // A pass that aborted halfway has stamped only the complaints it reached, so sweeping on its
            // stamp would delete the entire remainder of the register's history and silently drop every
            // lifetime count. There is no transaction to roll the upserts back, so this ordering is the
            // only guarantee.
            when(sourceRepository.findComplainantRows(anyLong(), any(Pageable.class)))
                    .thenThrow(new RuntimeException("connection reset mid-pass"));

            assertThat(service.refresh()).isZero();
            verify(projectionRepository, never()).deleteStale(any());
        }

        @Test
        @DisplayName("an existing row is OVERWRITTEN, not duplicated")
        void existingRowIsOverwritten() {
            AssistanceComplainantHistory existing = AssistanceComplainantHistory.builder()
                    .id(42L)
                    .complaintNumber("N-1")
                    .emailKey("stale@example.com")
                    .phoneKey("9700000000")
                    .entityKey("STALE BANK")
                    .department("RBIO")
                    .filedAt(STAMP.minusYears(1))
                    .status("old")
                    .nonMaintainable(AssistanceComplainantHistory.FLAG_YES)
                    .refreshedAt(STAMP.minusDays(30))
                    .build();
            when(projectionRepository.findByComplaintNumber("N-1")).thenReturn(Optional.of(existing));
            onScan(List.of(src(1, "N-1", "fresh@example.com", "9812345601", "HDFC Bank", "CEPC")));

            service.recompute(STAMP);

            AssistanceComplainantHistory saved = savedFor("N-1");
            // Same row (UK_ACH_COMPLAINT is the idempotency), re-keyed in place.
            assertThat(saved.getId()).isEqualTo(42L);
            assertThat(saved.getEmailKey()).isEqualTo("fresh@example.com");
            assertThat(saved.getPhoneKey()).isEqualTo("9812345601");
            assertThat(saved.getEntityKey()).isEqualTo("HDFC BANK");
            assertThat(saved.getDepartment()).isEqualTo("CEPC");
            assertThat(saved.getNonMaintainable()).isEqualTo(AssistanceComplainantHistory.FLAG_NO);
            assertThat(saved.getRefreshedAt()).isEqualTo(STAMP);
        }

        @Test
        @DisplayName("every row of a pass carries ONE stamp, so the sweep can tell them apart")
        void everyRowCarriesTheSameStamp() {
            onScan(List.of(
                    src(1, "N-1", "a@example.com", "9812345601", "HDFC Bank", "CEPC"),
                    src(2, "N-2", "b@example.com", "9812345602", "HDFC Bank", "CEPC")));

            service.recompute(STAMP);

            assertThat(captureSaved())
                    .extracting(AssistanceComplainantHistory::getRefreshedAt)
                    .containsOnly(STAMP);
        }
    }

    // ═══ NO PII IN THE PROJECTION ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the projection holds no name, address, subject or narrative — structurally")
    void projectionHoldsNoNarrativeOrName() {
        // The source projection has no field for them, so the job cannot copy what it never reads. This
        // asserts on the SOURCE record's shape, which is the upstream half of the privacy argument.
        List<String> components = new ArrayList<>();
        for (java.lang.reflect.RecordComponent component : ComplainantRow.class.getRecordComponents()) {
            components.add(component.getName());
        }

        assertThat(components).doesNotContain(
                "complainantName", "complainantAddress", "complainantPincode", "subject",
                "description", "reliefSought", "accountNumber", "cardNumber");
        // The two contact fields ARE read, because they are the identity keys. They are normalised into
        // join keys and never serialised — see DuplicateFilingDetectionServiceTest.PrivacyBoundary.
        assertThat(components).contains("complainantEmail", "complainantPhone");
    }
}
