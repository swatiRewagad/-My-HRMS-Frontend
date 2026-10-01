package com.hrms.cms.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@code @PrePersist} to DEFAULTING the audit timestamps rather than imposing them.
 *
 * <h2>Why this is worth a test</h2>
 * {@code onCreate()} used to assign {@code createdAt}, {@code updatedAt}, {@code filedAt} and
 * {@code lastStatusChangeDate} unconditionally, so a caller-supplied date was unwritable — the setter
 * ran, the callback overwrote it, and the row landed with the insert time. No exception, no log line.
 *
 * <p>The cost was real and measured: {@code DemoDataSeeder} spreads 60 complaints over 90 days, and all
 * 60 persisted within 122 ms. Since those rows keep the {@code closed_at} the seeder chose, closure
 * then preceded creation, so every filing-to-closure measurement discarded them as negative. That is
 * what starved the assistance rail's category-closure prior of a sample.
 *
 * <p>Both directions are asserted here, because fixing one and breaking the other is the easy mistake:
 * a supplied value must survive, AND an unsupplied one must still be defaulted — the production filing
 * path sets none of these and must keep working untouched.
 *
 * <p>The callback is invoked directly, matching {@code RbioStaffProfileTest}. That is deliberate rather
 * than lazy: it is the only way to observe the callback's own behaviour in isolation, and a persistence
 * test would prove the same thing while also depending on a database being up.
 */
class ComplaintTimestampTest {

    /** A date far enough from "now" that a clobbered value cannot be mistaken for a preserved one. */
    private static final LocalDateTime BACKDATED = LocalDateTime.of(2026, 3, 14, 9, 30, 15);

    private Complaint complaint() {
        return Complaint.builder()
                .complaintNumber("CMS-TEST-0001")
                .subject("timestamp defaulting")
                .build();
    }

    @Nested
    @DisplayName("A caller-supplied timestamp survives the callback")
    class Preserves {

        @Test
        void createdAtIsNotOverwritten() {
            Complaint c = complaint();
            c.setCreatedAt(BACKDATED);

            c.onCreate();

            assertThat(c.getCreatedAt()).isEqualTo(BACKDATED);
        }

        @Test
        void filedAtIsNotOverwritten() {
            Complaint c = complaint();
            c.setFiledAt(BACKDATED);

            c.onCreate();

            assertThat(c.getFiledAt()).isEqualTo(BACKDATED);
        }

        @Test
        void updatedAtAndLastStatusChangeAreNotOverwritten() {
            Complaint c = complaint();
            c.setUpdatedAt(BACKDATED);
            c.setLastStatusChangeDate(BACKDATED);

            c.onCreate();

            assertThat(c.getUpdatedAt()).isEqualTo(BACKDATED);
            assertThat(c.getLastStatusChangeDate()).isEqualTo(BACKDATED);
        }

        @Test
        @DisplayName("a backdated complaint closed later keeps a POSITIVE closure window")
        void backdatedComplaintYieldsNonNegativeClosureWindow() {
            // This is the defect in the form that actually hurt. The seeder's intent is a complaint
            // filed in March and closed in April; the old callback moved creation to now(), so closure
            // landed BEFORE creation and the row was dropped by the non-negative filter in
            // AssistanceRailService.categoryClosureTime.
            Complaint c = complaint();
            c.setCreatedAt(BACKDATED);
            c.setClosedAt(BACKDATED.plusDays(30));

            c.onCreate();

            assertThat(c.getClosedAt()).isAfter(c.getCreatedAt());
            assertThat(java.time.Duration.between(c.getCreatedAt(), c.getClosedAt()).toDays())
                    .isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("An unsupplied timestamp is still defaulted")
    class Defaults {

        @Test
        void allFourAreSetWhenTheCallerSetsNone() {
            // The production filing path sets none of these, so this is the case that must not regress.
            LocalDateTime before = LocalDateTime.now();
            Complaint c = complaint();

            c.onCreate();

            assertThat(c.getCreatedAt()).isNotNull().isBetween(before, LocalDateTime.now());
            assertThat(c.getUpdatedAt()).isNotNull().isBetween(before, LocalDateTime.now());
            assertThat(c.getFiledAt()).isNotNull().isBetween(before, LocalDateTime.now());
            assertThat(c.getLastStatusChangeDate()).isNotNull().isBetween(before, LocalDateTime.now());
        }

        @Test
        @DisplayName("defaulting is per-field: one supplied value does not suppress the others")
        void aSuppliedCreatedAtDoesNotSuppressTheOtherDefaults() {
            Complaint c = complaint();
            c.setCreatedAt(BACKDATED);

            c.onCreate();

            assertThat(c.getCreatedAt()).isEqualTo(BACKDATED);
            assertThat(c.getFiledAt()).isNotNull().isAfter(BACKDATED);
            assertThat(c.getUpdatedAt()).isNotNull().isAfter(BACKDATED);
            assertThat(c.getLastStatusChangeDate()).isNotNull().isAfter(BACKDATED);
        }

        @Test
        void statusAndPriorityStillDefault() {
            Complaint c = complaint();

            c.onCreate();

            assertThat(c.getStatus()).isEqualTo("pending");
            assertThat(c.getPriority()).isEqualTo("medium");
        }

        @Test
        void anExplicitStatusAndPriorityAreKept() {
            Complaint c = complaint();
            c.setStatus("under_review");
            c.setPriority("high");

            c.onCreate();

            assertThat(c.getStatus()).isEqualTo("under_review");
            assertThat(c.getPriority()).isEqualTo("high");
        }
    }
}
