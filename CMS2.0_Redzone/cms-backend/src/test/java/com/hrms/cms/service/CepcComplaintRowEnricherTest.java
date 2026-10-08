package com.hrms.cms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the CEPC dashboard row tint.
 *
 * <p>The tint is deliberately sparse: five conditions colour a row and every other status leaves it plain.
 * Both halves matter, so the "no tint" cases are asserted as thoroughly as the coloured ones — a fallback
 * colour creeping back in would tint most of the grid and cost the colour its meaning.
 */
class CepcComplaintRowEnricherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 10, 0);

    @Nested
    @DisplayName("complaintColor — status to tint")
    class ComplaintColor {

        /**
         * Both spellings of every status, because the write path persists the legacy token while the seed
         * writes the canonical name. A status matched under only one spelling leaves live rows untinted.
         */
        @ParameterizedTest
        @CsvSource({
                "INFORMATION_REQUIRED,   red",
                "information_required,   red",
                "info_requested,         red",
                "SENT_TO_RBI,            green",
                "sent_to_rbi,            green",
                "SENT_BACK,              yellow",
                "sent_back,              yellow",
                "COMPLAINT_WITHDRAWN,    pink",
                "complaint_withdrawn,    pink",
                "withdrawn,              pink"})
        @DisplayName("colours the five flagged statuses under either spelling")
        void coloursFlaggedStatuses(String status, String expected) {
            assertThat(CepcComplaintRowEnricher.complaintColor(status, NOW.minusDays(1), NOW))
                    .isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ASSIGNED", "IN_PROGRESS", "UNDER_REVIEW", "reviewer_review",
                "COMPLAINT_SETTLED", "awaiting_closure", "COMPLAINT_CLOSED", "closed", "ESCALATED",
                "MEETING_SCHEDULED", "DRAFT", "COMPLAINT_REJECTED", "SENT_TO_OTHER_OFFICE",
                "PENDING_OFFICE_HEAD_APPROVAL", "not_a_real_status"})
        @DisplayName("leaves every other status untinted")
        void leavesOtherStatusesUntinted(String status) {
            assertThat(CepcComplaintRowEnricher.complaintColor(status, NOW.minusDays(30), NOW)).isNull();
        }

        @Test
        @DisplayName("a null status carries no tint rather than a fallback colour")
        void nullStatusIsUntinted() {
            assertThat(CepcComplaintRowEnricher.complaintColor(null, NOW.minusDays(30), NOW)).isNull();
        }

        @Test
        @DisplayName("ignores surrounding whitespace and case")
        void trimsAndCaseFolds() {
            assertThat(CepcComplaintRowEnricher.complaintColor("  Information_Required  ", NOW, NOW))
                    .isEqualTo("red");
        }
    }

    /**
     * Blue is the only condition that is not a status alone — it needs the complaint to still be new AND to
     * have been filed more than three days ago. The boundary is the part worth pinning: "more than 3 days"
     * means a complaint filed exactly three days ago is not yet blue.
     */
    @Nested
    @DisplayName("complaintColor — the new-complaint ageing rule")
    class NewComplaintAgeing {

        @ParameterizedTest
        @ValueSource(strings = {"NEW_COMPLAINT", "new_complaint", "pending"})
        @DisplayName("turns blue once a new complaint is more than three days old")
        void bluePastThreeDays(String status) {
            assertThat(CepcComplaintRowEnricher.complaintColor(status, NOW.minusDays(5), NOW))
                    .isEqualTo("blue");
        }

        @Test
        @DisplayName("stays untinted at exactly three days, and past it by hours")
        void notBlueAtTheBoundary() {
            assertThat(CepcComplaintRowEnricher.complaintColor("NEW_COMPLAINT", NOW.minusDays(3), NOW))
                    .isNull();
            assertThat(CepcComplaintRowEnricher.complaintColor(
                    "NEW_COMPLAINT", NOW.minusDays(3).minusHours(6), NOW)).isNull();
        }

        @Test
        @DisplayName("turns blue the moment a full fourth day has elapsed")
        void blueOnceFourDaysElapsed() {
            assertThat(CepcComplaintRowEnricher.complaintColor(
                    "NEW_COMPLAINT", NOW.minusDays(4), NOW)).isEqualTo("blue");
        }

        @Test
        @DisplayName("a recently filed new complaint carries no tint")
        void freshComplaintIsUntinted() {
            assertThat(CepcComplaintRowEnricher.complaintColor("NEW_COMPLAINT", NOW.minusDays(2), NOW))
                    .isNull();
        }

        @Test
        @DisplayName("an unknown filed date cannot be aged, so it carries no tint")
        void nullFiledDateIsUntinted() {
            assertThat(CepcComplaintRowEnricher.complaintColor("NEW_COMPLAINT", null, NOW)).isNull();
        }
    }
}
