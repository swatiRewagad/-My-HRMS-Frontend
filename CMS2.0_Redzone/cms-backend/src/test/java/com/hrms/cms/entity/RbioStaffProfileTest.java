package com.hrms.cms.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the default-deny and leave-window semantics of the RBIO staff profile (UST629, UST447/448).
 *
 * <h2>Why these are unit-tested rather than left to the DDL</h2>
 * {@code CLOSURE_ELIGIBLE} has a {@code DEFAULT 'N'} in both migrations, but a column default only
 * applies when the column is absent from the INSERT — and Hibernate always lists every mapped column. So
 * a profile built without setting the flag would be written as NULL, and NULL would then have to be
 * interpreted somewhere. These tests pin that interpretation to "deny", which is the whole point of
 * UST629 defaulting a new Reviewer to No.
 */
class RbioStaffProfileTest {

    private RbioStaffProfile profile() {
        return RbioStaffProfile.builder().userId("rbio_reviewer_001").build();
    }

    @Nested
    @DisplayName("Closure eligibility denies unless explicitly granted (UST629)")
    class ClosureEligibility {

        @Test
        void aNewProfileIsNotClosureEligible() {
            RbioStaffProfile p = profile();
            p.onCreate();

            assertThat(p.getClosureEligible()).isEqualTo("N");
            assertThat(p.closureEligibleFlag()).isFalse();
        }

        @Test
        void anUnsetFlagIsTreatedAsDenied() {
            // Without @PrePersist having run — e.g. an object read back from a row written by an older
            // path that predates this column — a null must not read as permitted.
            assertThat(profile().closureEligibleFlag()).isFalse();
        }

        @Test
        void onlyAnExplicitYGrantsEligibility() {
            RbioStaffProfile p = profile();

            p.setClosureEligible("Y");
            assertThat(p.closureEligibleFlag()).isTrue();

            // Lower case is accepted: the DDL does not constrain case and a 'y' written by hand or by a
            // data fix is plainly intended as a grant.
            p.setClosureEligible("y");
            assertThat(p.closureEligibleFlag()).isTrue();
        }

        @Test
        void anythingOtherThanYDenies() {
            RbioStaffProfile p = profile();
            for (String value : new String[]{"N", "n", "", " ", "true", "1", "YES", "X"}) {
                p.setClosureEligible(value);
                assertThat(p.closureEligibleFlag())
                        .as("value '%s' must not grant closure eligibility", value)
                        .isFalse();
            }
        }

        @Test
        void onCreateDoesNotOverwriteAnExplicitGrant() {
            // An admin who ticked the box on the create form must not have it silently reset by the
            // default. This is the bug that would make UST629 look correct while ignoring the input.
            RbioStaffProfile p = profile();
            p.setClosureEligible("Y");

            p.onCreate();

            assertThat(p.closureEligibleFlag()).isTrue();
        }
    }

    @Nested
    @DisplayName("Leave windows (UST447, UST448, UST451)")
    class LeaveWindow {

        private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

        @Test
        void noRecordedLeaveMeansNotOnLeave() {
            assertThat(profile().onLeaveOn(DAY)).isFalse();
        }

        @Test
        void aDayInsideTheWindowIsOnLeave() {
            RbioStaffProfile p = profile();
            p.setLeaveFromDate(DAY.minusDays(2));
            p.setLeaveToDate(DAY.plusDays(2));

            assertThat(p.onLeaveOn(DAY)).isTrue();
        }

        @Test
        void bothBoundariesAreInclusive() {
            // An officer whose leave runs "from the 21st to the 25th" is away on both of those days.
            // Treating either boundary as exclusive would hand them work on a day they are absent, which
            // is what UST450's assignment fix is about.
            RbioStaffProfile p = profile();
            p.setLeaveFromDate(DAY);
            p.setLeaveToDate(DAY.plusDays(4));

            assertThat(p.onLeaveOn(DAY)).isTrue();
            assertThat(p.onLeaveOn(DAY.plusDays(4))).isTrue();
        }

        @Test
        void aDayBeforeOrAfterTheWindowIsNotOnLeave() {
            RbioStaffProfile p = profile();
            p.setLeaveFromDate(DAY);
            p.setLeaveToDate(DAY.plusDays(4));

            assertThat(p.onLeaveOn(DAY.minusDays(1))).isFalse();
            assertThat(p.onLeaveOn(DAY.plusDays(5))).isFalse();
        }

        @Test
        void openEndedLeaveKeepsTheOfficerOnLeave() {
            // A start date with no end date means the admin intended the officer to be away. Reading it
            // as "not on leave" would be the more dangerous interpretation: it would route complaints to
            // somebody nobody expects to be working.
            RbioStaffProfile p = profile();
            p.setLeaveFromDate(DAY.minusDays(30));

            assertThat(p.onLeaveOn(DAY)).isTrue();
            assertThat(p.onLeaveOn(DAY.plusYears(1))).isTrue();
        }

        @Test
        void anEndDateAloneIsNotLeave() {
            // Without a start there is no window. Inferring "on leave since the beginning of time" from a
            // stray end date would silently remove an active officer from the pool.
            RbioStaffProfile p = profile();
            p.setLeaveToDate(DAY.plusDays(5));

            assertThat(p.onLeaveOn(DAY)).isFalse();
        }
    }

    @Nested
    @DisplayName("Active flag")
    class ActiveFlag {

        @Test
        void aNewProfileIsActive() {
            RbioStaffProfile p = profile();
            p.onCreate();

            assertThat(p.activeFlag()).isTrue();
        }

        @Test
        void onlyYCountsAsActive() {
            RbioStaffProfile p = profile();
            p.setIsActive("N");
            assertThat(p.activeFlag()).isFalse();
        }

        @Test
        void onCreateStampsCreationAndMatchesUpdatedAt() {
            RbioStaffProfile p = profile();
            p.onCreate();

            assertThat(p.getCreatedAt()).isNotNull();
            assertThat(p.getUpdatedAt()).isEqualTo(p.getCreatedAt());
        }
    }
}
