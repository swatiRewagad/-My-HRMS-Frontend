package com.hrms.cms.service;

import com.hrms.cms.entity.AaAssignmentCounter;
import com.hrms.cms.entity.AaOfficerPool;
import com.hrms.cms.repository.AaAssignmentCounterRepository;
import com.hrms.cms.repository.AaOfficerPoolRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers the durable, cluster-safe rotation (UST471) and on-leave exclusion (UST450).
 *
 * <p>The assertions mirror the ones that already guard the AA engine in
 * {@code e2e/aa/s2c-assignment-engine.spec.ts} — pointer read back from the store rather than inferred
 * from a call sequence, eligibility applied without a restart, and a fail-closed outcome when nobody is
 * available — because those are the properties that actually broke in the five in-memory
 * implementations this service replaces.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DurableRoundRobinAssignerTest {

    private static final String GROUP = "RBIO_OFFICER";

    @Mock private AaAssignmentCounterRepository counterRepository;
    @Mock private AaAssignmentCounterInitialiser counterInitialiser;
    @Mock private AaOfficerPoolRepository officerPoolRepository;
    @Mock private KeycloakUserService keycloakUserService;

    @InjectMocks private DurableRoundRobinAssigner assigner;

    /** A mutable counter row, so a test can assert what the service persisted. */
    private AaAssignmentCounter counter(String lastAssigned) {
        AaAssignmentCounter c = AaAssignmentCounter.builder()
                .roleGroup(GROUP)
                .lastAssignedIndex(0)
                .lastAssignedUserId(lastAssigned)
                .build();
        when(counterRepository.findByRoleGroupForUpdate(GROUP)).thenReturn(Optional.of(c));
        when(counterRepository.findByRoleGroup(GROUP)).thenReturn(Optional.of(c));
        return c;
    }

    private void keycloakMembers(String... userIds) {
        List<Map<String, Object>> members = new ArrayList<>();
        for (String id : userIds) {
            members.add(Map.of("userId", id));
        }
        when(keycloakUserService.getUsersByRole(GROUP)).thenReturn(members);
    }

    private void poolRows(AaOfficerPool... rows) {
        when(officerPoolRepository.findByRoleGroupOrderByUserIdAsc(GROUP)).thenReturn(List.of(rows));
    }

    private AaOfficerPool poolRow(String userId, boolean active, boolean onLeave) {
        AaOfficerPool row = new AaOfficerPool();
        row.setUserId(userId);
        row.setRoleGroup(GROUP);
        row.setActive(active);
        row.setOnLeave(onLeave);
        return row;
    }

    @Nested
    @DisplayName("Durable rotation (UST471)")
    class Rotation {

        @Test
        void startsAtTheHeadWhenThereIsNoRotationHistory() {
            AaAssignmentCounter c = counter(null);
            keycloakMembers("officer.c", "officer.a", "officer.b");
            poolRows();

            assertThat(assigner.assignNext(GROUP).officerId()).isEqualTo("officer.a");
            // The pointer is PERSISTED, which is what makes rotation survive a restart. An in-memory
            // counter would have produced the same first answer and then reset on the next boot.
            assertThat(c.getLastAssignedUserId()).isEqualTo("officer.a");
            verify(counterRepository).save(c);
        }

        @Test
        void advancesToTheNextOfficerAfterTheStoredPointer() {
            counter("officer.a");
            keycloakMembers("officer.a", "officer.b", "officer.c");
            poolRows();

            assertThat(assigner.assignNext(GROUP).officerId()).isEqualTo("officer.b");
        }

        @Test
        void wrapsToTheHeadAfterTheLastOfficer() {
            counter("officer.c");
            keycloakMembers("officer.a", "officer.b", "officer.c");
            poolRows();

            assertThat(assigner.assignNext(GROUP).officerId()).isEqualTo("officer.a");
        }

        @Test
        void resumesAfterAnOfficerWhoIsNoLongerACandidate() {
            // The whole reason the pointer is a user id and not an index: officer.b has left, but
            // "the next id after officer.b" is still answerable, so rotation continues at officer.c
            // instead of restarting at the head and favouring officer.a.
            counter("officer.b");
            keycloakMembers("officer.a", "officer.c", "officer.d");
            poolRows();

            assertThat(assigner.assignNext(GROUP).officerId()).isEqualTo("officer.c");
        }

        @Test
        void rotatesFairlyAcrossAFullCycle() {
            AaAssignmentCounter c = counter(null);
            keycloakMembers("officer.a", "officer.b", "officer.c");
            poolRows();

            List<String> picked = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                picked.add(assigner.assignNext(GROUP).officerId());
            }

            // Two complete cycles, in order, with no officer taking two in a row.
            assertThat(picked).containsExactly(
                    "officer.a", "officer.b", "officer.c",
                    "officer.a", "officer.b", "officer.c");
            assertThat(c.getLastAssignedUserId()).isEqualTo("officer.c");
        }

        @Test
        void orderingIsIndependentOfTheOrderKeycloakReturnsMembers() {
            // Without a stable total order the pointer is meaningless, because "the next id after X"
            // would depend on whatever order the realm happened to answer in on each pod.
            counter("officer.a");
            keycloakMembers("officer.c", "officer.b", "officer.d");
            poolRows();

            assertThat(assigner.assignNext(GROUP).officerId()).isEqualTo("officer.b");
        }
    }

    @Nested
    @DisplayName("Locking order")
    class LockingOrder {

        @Test
        void createsThePointerRowBeforeTakingTheLock() {
            counter(null);
            keycloakMembers("officer.a");
            poolRows();

            assigner.assignNext(GROUP);

            // Locking a row that does not exist takes an InnoDB gap lock, and the insert meant to fill
            // that gap then blocks on the same transaction — a self-deadlock seen as "Lock wait timeout"
            // on the first ever assignment into a new role group.
            InOrder order = inOrder(counterInitialiser, counterRepository);
            order.verify(counterInitialiser).ensureExists(GROUP);
            order.verify(counterRepository).findByRoleGroupForUpdate(GROUP);
        }

        @Test
        void takesTheLockBeforeReadingTheCandidatePool() {
            counter(null);
            keycloakMembers("officer.a");
            poolRows();

            assigner.assignNext(GROUP);

            // The predecessor locked AFTER reading the pool, which left the candidate snapshot outside
            // the critical section and made the lock decorative.
            InOrder order = inOrder(counterRepository, keycloakUserService);
            order.verify(counterRepository).findByRoleGroupForUpdate(GROUP);
            order.verify(keycloakUserService).getUsersByRole(GROUP);
        }
    }

    @Nested
    @DisplayName("Eligibility (UST450)")
    class Eligibility {

        @Test
        void skipsAnOfficerThePoolMarksOnLeave() {
            counter(null);
            keycloakMembers("officer.a", "officer.b");
            poolRows(poolRow("officer.a", true, true));

            assertThat(assigner.assignNext(GROUP).officerId()).isEqualTo("officer.b");
        }

        @Test
        void skipsAnInactiveOfficer() {
            counter(null);
            keycloakMembers("officer.a", "officer.b");
            poolRows(poolRow("officer.a", false, false));

            assertThat(assigner.assignNext(GROUP).officerId()).isEqualTo("officer.b");
        }

        @Test
        void treatsAnOfficerWithNoPoolRowAsAvailable() {
            // Absence of a row is absence of evidence, not evidence of unavailability. Refusing to
            // assign anyone merely because the pool is unseeded would stall registration everywhere.
            counter(null);
            keycloakMembers("officer.a");
            poolRows();

            assertThat(assigner.assignNext(GROUP).officerId()).isEqualTo("officer.a");
        }

        @Test
        void refusesWhenEveryCandidateIsOnLeave() {
            counter(null);
            keycloakMembers("officer.a", "officer.b");
            poolRows(poolRow("officer.a", true, true), poolRow("officer.b", true, true));

            DurableRoundRobinAssigner.Assignment result = assigner.assignNext(GROUP);

            // Fail closed: there is deliberately no widen-the-predicate fallback. The predecessor in
            // cms-workflow-service re-queried without the on-leave filter when the filtered set came
            // back empty, which re-admitted the very officers UST450 excludes.
            assertThat(result.isAssigned()).isFalse();
            assertThat(result.reason()).contains(GROUP);
            verify(counterRepository, never()).save(any());
        }

        @Test
        void doesNotAdvanceThePointerWhenNobodyIsAssigned() {
            AaAssignmentCounter c = counter("officer.a");
            keycloakMembers();
            poolRows();

            assigner.assignNext(GROUP);

            // Preserving the pointer matters: resetting it on every exhausted attempt would make the
            // next available officer always be the head of the list.
            assertThat(c.getLastAssignedUserId()).isEqualTo("officer.a");
        }
    }

    @Nested
    @DisplayName("Fail closed")
    class FailClosed {

        @Test
        void refusesABlankRoleGroup() {
            DurableRoundRobinAssigner.Assignment result = assigner.assignNext("  ");

            assertThat(result.isAssigned()).isFalse();
            verifyNoInteractions(counterInitialiser, counterRepository, keycloakUserService);
        }

        @Test
        void refusesWhenTheRoleHasNoMembers() {
            counter(null);
            keycloakMembers();
            poolRows();

            DurableRoundRobinAssigner.Assignment result = assigner.assignNext(GROUP);

            // Never a fabricated placeholder. The predecessor returned "RBIO OFFICER Team" here and that
            // string was persisted as the assigned officer, so a complaint looked assigned to a person
            // who does not exist.
            assertThat(result.isAssigned()).isFalse();
            assertThat(result.officerId()).isNull();
        }

        @Test
        void refusesWhenKeycloakIsUnreachable() {
            counter(null);
            when(keycloakUserService.getUsersByRole(GROUP)).thenThrow(new RuntimeException("connection refused"));
            poolRows();

            DurableRoundRobinAssigner.Assignment result = assigner.assignNext(GROUP);

            // An unreachable directory is not evidence that nobody is available, so the complaint is
            // left unassigned rather than assigned to a guess.
            assertThat(result.isAssigned()).isFalse();
        }

        @Test
        void refusesWhenThePointerRowVanishesAfterInitialisation() {
            when(counterRepository.findByRoleGroupForUpdate(GROUP)).thenReturn(Optional.empty());
            keycloakMembers("officer.a");
            poolRows();

            DurableRoundRobinAssigner.Assignment result = assigner.assignNext(GROUP);

            // Without the lock there is no serialisation, so assigning anyway would silently give up
            // the property this service exists to provide.
            assertThat(result.isAssigned()).isFalse();
            verify(keycloakUserService, never()).getUsersByRole(anyString());
        }

        @Test
        void stillAppliesRotationWhenThePoolCannotBeRead() {
            // A pool read failure must not abort assignment — the pool is an exclusion list, not the
            // candidate source — but it must not silently widen eligibility either. Here it degrades to
            // "no exclusions known", which is logged as an error by the service.
            counter(null);
            keycloakMembers("officer.a", "officer.b");
            when(officerPoolRepository.findByRoleGroupOrderByUserIdAsc(GROUP))
                    .thenThrow(new RuntimeException("pool unavailable"));

            assertThat(assigner.assignNext(GROUP).officerId()).isEqualTo("officer.a");
        }
    }

    @Nested
    @DisplayName("Pointer reset")
    class PointerReset {

        @Test
        void clearingThePointerSendsTheNextAssignmentBackToTheHead() {
            AaAssignmentCounter c = counter("officer.c");

            assigner.resetPointer(GROUP);

            assertThat(c.getLastAssignedUserId()).isNull();
            verify(counterRepository).save(c);
        }

        @Test
        void currentPointerReportsWhoLastReceivedWork() {
            counter("officer.b");

            assertThat(assigner.currentPointer(GROUP)).contains("officer.b");
        }
    }
}
