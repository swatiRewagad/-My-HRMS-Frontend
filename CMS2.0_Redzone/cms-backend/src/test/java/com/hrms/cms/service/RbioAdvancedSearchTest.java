package com.hrms.cms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the UST439-441 advance-search contract that decides whether a criterion RUNS or is REFUSED.
 *
 * <h2>Why this matters more than it looks</h2>
 * The failure mode in this module is not a 404 — it is a criterion the client sends and the server never
 * reads, so the response is 200 with a result set that answers a different question. A test that asserted
 * "searching returns rows" would pass against exactly that bug. So these tests pin the two decisions that
 * cannot be observed from a row count: which terms are too short to run, and whether the officer supplied
 * any criteria at all.
 */
class RbioAdvancedSearchTest {

    private RbioComplaintListService.AdvancedSearch search(String name, String entity, String subject,
                                                           String nodal) {
        return new RbioComplaintListService.AdvancedSearch(
                null, name, null, null, null, null, null, subject, null, entity, nodal, null, null, null);
    }

    private RbioComplaintListService.AdvancedSearch empty() {
        return search(null, null, null, null);
    }

    @Nested
    @DisplayName("Short partial terms are refused, exact fields are exempt")
    class MinimumTermLength {

        @Test
        void aSingleCharacterNameIsRefused() {
            // LIKE '%a%' over COMPLAINTS returns most of the table. Useless to the officer, and an easy
            // way to page through every complainant name in the system.
            assertThat(search("a", null, null, null).tooShortTerms()).containsExactly("complainantName");
        }

        @Test
        void twoCharactersIsAccepted() {
            assertThat(search("ab", null, null, null).tooShortTerms()).isEmpty();
        }

        @Test
        void whitespaceDoesNotPadATermToLength() {
            // " a " trims to one character. Counting the spaces would let the guard be bypassed with a
            // space bar.
            assertThat(search(" a ", null, null, null).tooShortTerms()).containsExactly("complainantName");
        }

        @Test
        void everyPartialFieldIsChecked() {
            assertThat(search("a", "b", "c", "d").tooShortTerms())
                    .containsExactlyInAnyOrder("complainantName", "entityName", "subject",
                            "nodalOfficerName");
        }

        @Test
        void exactMatchFieldsAreExemptFromTheLengthGuard() {
            // A one-digit mobile or a one-character complaint number is selective by construction — an
            // equality test, not a scan. Applying the guard here would refuse a legitimate narrow search.
            var s = new RbioComplaintListService.AdvancedSearch(
                    "N", null, "9", "a@b.c", "NEW", "1", "x@y.z", null, "EMAIL", null, null, "2",
                    null, null);

            assertThat(s.tooShortTerms()).isEmpty();
        }

        @Test
        void aBlankTermIsNotTreatedAsTooShort() {
            // Blank means "not supplied", which is not an error. Refusing it would block a search whose
            // other criteria are perfectly valid.
            assertThat(search("", "   ", null, null).tooShortTerms()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Whether any criterion was supplied (UST442)")
    class AnyCriterion {

        @Test
        void noCriteriaMeansNoSearch() {
            // Drives the difference between "your search matched nothing" and "you have no work". Showing
            // the wrong one of those two tells an officer their queue is empty when it is not.
            assertThat(empty().any()).isFalse();
        }

        @Test
        void blankStringsDoNotCountAsCriteria() {
            assertThat(search("", "", "", "").any()).isFalse();
        }

        @Test
        void oneCriterionIsEnough() {
            assertThat(search("Sharma", null, null, null).any()).isTrue();
        }

        @Test
        void everyFieldCountsTowardsHavingSearched() {
            // Checked field by field because a missing branch in any() would silently report a real search
            // as "not searched", and the empty grid would then say "no complaints in this queue".
            String[] values = new String[14];
            for (int i = 0; i < values.length; i++) {
                String[] one = new String[14];
                one[i] = "x";
                var s = new RbioComplaintListService.AdvancedSearch(
                        one[0], one[1], one[2], one[3], one[4], one[5], one[6],
                        one[7], one[8], one[9], one[10], one[11], one[12], one[13]);
                assertThat(s.any())
                        .as("field at index %d must count as a supplied criterion", i)
                        .isTrue();
            }
        }

        @Test
        void aDateRangeAloneCountsAsASearch() {
            var s = new RbioComplaintListService.AdvancedSearch(
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    "2026-01-01", "2026-09-30");

            assertThat(s.any()).isTrue();
        }
    }
}
