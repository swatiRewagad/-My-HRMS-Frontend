package com.rbi.cms.search.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the two properties every constraint on these records has to hold simultaneously: they must
 * bound what a caller can send, and they must not reject an unused filter.
 *
 * <p>Every field on all four records is an <em>optional</em> filter. An omitted field arrives as null
 * and a cleared form control arrives as {@code ""}, so a constraint that rejects either turns an
 * ordinary search into a 400. That is why there is no {@code @NotNull} or {@code @NotBlank} anywhere in
 * these records, and why the first two tests in each block are the important ones.
 */
class RequestConstraintsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static <T> Set<String> paths(T target) {
        return validator.validate(target).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static String repeat(int length) {
        return "x".repeat(length);
    }

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Nested
    @DisplayName("AdvancedSearchRequest")
    class Advanced {

        private AdvancedSearchRequest allNull() {
            return new AdvancedSearchRequest(null, null, null, null, null, null, null, null, null,
                    null, null, null, null);
        }

        private AdvancedSearchRequest allBlank() {
            return new AdvancedSearchRequest("", null, "", "", "", "", "", "", "", null, null, "", "");
        }

        @Test
        @DisplayName("an entirely empty request is valid")
        void allNullIsValid() {
            assertThat(validator.validate(allNull())).isEmpty();
        }

        // The cleared-filter guarantee. @Pattern is the trap here: unlike @Size and @Email it rejects
        // "" unless the regex admits it, so complainantPhone needs its ^$ branch.
        @Test
        @DisplayName("every text field cleared to \"\" is valid")
        void allBlankIsValid() {
            assertThat(validator.validate(allBlank())).isEmpty();
        }

        @Test
        @DisplayName("over-length text is rejected on the field that carries it")
        void overLengthIsRejected() {
            AdvancedSearchRequest request = new AdvancedSearchRequest(
                    repeat(51), null, repeat(31), repeat(201), null, null, repeat(51),
                    repeat(301), repeat(501), null, null, repeat(201), null);

            assertThat(paths(request)).containsExactlyInAnyOrder(
                    "complaintNumber", "statusCode", "complainantName", "filingType",
                    "entityName", "subject", "nodalOfficerName");
        }

        @Test
        @DisplayName("values at the length bound are accepted")
        void boundaryLengthIsValid() {
            AdvancedSearchRequest request = new AdvancedSearchRequest(
                    repeat(50), null, null, repeat(200), null, null, null,
                    repeat(300), repeat(500), null, null, repeat(200), null);

            assertThat(validator.validate(request)).isEmpty();
        }

        // A complaint cannot be filed in the future, so this would otherwise be a well-formed request
        // that can only ever return an empty page.
        @Test
        @DisplayName("a future filedAt is rejected")
        void futureFiledAtIsRejected() {
            AdvancedSearchRequest request = new AdvancedSearchRequest(null, null, null, null, null,
                    null, null, null, null, null, TOMORROW, null, null);

            assertThat(paths(request)).containsExactly("filedAt");
        }

        @Test
        @DisplayName("today's filedAt is accepted")
        void todayFiledAtIsValid() {
            AdvancedSearchRequest request = new AdvancedSearchRequest(null, null, null, null, null,
                    null, null, null, null, null, LocalDate.now(), null, null);

            assertThat(validator.validate(request)).isEmpty();
        }

        // A non-positive id cannot match a document, so it is a client bug rather than a filter.
        @Test
        @DisplayName("non-positive ids are rejected")
        void nonPositiveIdsAreRejected() {
            assertThat(paths(new AdvancedSearchRequest(null, 0L, null, null, null, null, null, null,
                    null, 0L, null, null, null)))
                    .containsExactlyInAnyOrder("id", "categoryId");

            assertThat(paths(new AdvancedSearchRequest(null, -1L, null, null, null, null, null, null,
                    null, -1L, null, null, null)))
                    .containsExactlyInAnyOrder("id", "categoryId");
        }

        // Bounded by length only, for the same reason as SearchFieldsRequest: the stored vocabulary is
        // wider than ComplaintStatus and FilingType declare.
        @Test
        @DisplayName("statusCode and filingType accept values that are not enum constants")
        void statusAndFilingTypeAreNotEnumConstrained() {
            AdvancedSearchRequest request = new AdvancedSearchRequest(null, null, "pending", null, null,
                    null, "WEB_PORTAL", null, null, null, null, null, null);

            assertThat(validator.validate(request)).isEmpty();
        }

        @Test
        @DisplayName("malformed email is rejected on both address fields")
        void malformedEmailIsRejected() {
            AdvancedSearchRequest request = new AdvancedSearchRequest(null, null, null, null, null,
                    "not-an-email", null, null, null, null, null, null, "also@bad@example.com");

            assertThat(paths(request)).containsExactlyInAnyOrder("complainantEmail", "fromEmailId");
        }

        @Test
        @DisplayName("phone accepts digits and separators, rejects letters and short input")
        void phonePattern() {
            assertThat(validator.validate(phone("+91 98765 43210"))).isEmpty();
            assertThat(validator.validate(phone("9876543210"))).isEmpty();
            assertThat(paths(phone("98765"))).containsExactly("complainantPhone");
            assertThat(paths(phone("call-me-maybe"))).containsExactly("complainantPhone");
        }

        private AdvancedSearchRequest phone(String value) {
            return new AdvancedSearchRequest(null, null, null, null, value, null, null, null, null,
                    null, null, null, null);
        }
    }

    @Nested
    @DisplayName("SearchFieldsRequest")
    class SearchFields {

        private SearchFieldsRequest allNull() {
            return new SearchFieldsRequest(null, null, null, null, null, null, null, null, null,
                    null, null, null, null);
        }

        private SearchFieldsRequest allBlank() {
            return new SearchFieldsRequest("", "", "", "", "", "", "", "", "", null, null, "", "");
        }

        @Test
        @DisplayName("an entirely empty request is valid")
        void allNullIsValid() {
            assertThat(validator.validate(allNull())).isEmpty();
        }

        @Test
        @DisplayName("every text field cleared to \"\" is valid")
        void allBlankIsValid() {
            assertThat(validator.validate(allBlank())).isEmpty();
        }

        @Test
        @DisplayName("over-length text is rejected on the field that carries it")
        void overLengthIsRejected() {
            SearchFieldsRequest request = new SearchFieldsRequest(
                    repeat(21), repeat(51), repeat(201), repeat(21), null, repeat(201), null,
                    repeat(301), repeat(201), null, null, null, repeat(501));

            assertThat(paths(request)).containsExactlyInAnyOrder(
                    "complaintId", "complaintNumber", "assignedTo", "slaBreachIn",
                    "complainantName", "entityName", "complaintCategory", "subject");
        }

        @Test
        @DisplayName("an unknown priority is rejected")
        void unknownPriorityIsRejected() {
            SearchFieldsRequest request = new SearchFieldsRequest(null, null, null, null, null, null,
                    null, null, null, null, null, "URGENT", null);

            assertThat(paths(request)).containsExactly("priority");
        }

        @Test
        @DisplayName("a valid priority passes in any case")
        void knownPriorityIsAccepted() {
            SearchFieldsRequest request = new SearchFieldsRequest(null, null, null, null, null, null,
                    null, null, null, null, null, "HIGH", null);

            assertThat(validator.validate(request)).isEmpty();
        }

        // status and mode carry a wider vocabulary than ComplaintStatus and FilingType define —
        // cms-backend stores "pending", "conciliated", "WEB_PORTAL", "PHYSICAL_LETTER" and more, none of
        // which are enum constants. They are bounded by length only, so these must not be errors.
        @Test
        @DisplayName("status and mode accept values that are not enum constants")
        void statusAndModeAreNotEnumConstrained() {
            SearchFieldsRequest request = new SearchFieldsRequest(null, null, null, null,
                    "WEB_PORTAL", null, "pending", null, null, null, null, null, null);

            assertThat(validator.validate(request)).isEmpty();
            assertThat(validator.validate(new SearchFieldsRequest(null, null, null, null,
                    "PHYSICAL_LETTER", null, "conciliated", null, null, null, null, null, null)))
                    .isEmpty();
        }

        // Both dates describe something that has already happened, so a future value can only return an
        // empty page. They are LocalDate rather than String so that the query layer's toString() yields
        // the ISO-8601 form the index actually stores.
        @Test
        @DisplayName("future createdDate and lastUpdatedDate are rejected")
        void futureDatesAreRejected() {
            SearchFieldsRequest request = new SearchFieldsRequest(null, null, null, null, null, null,
                    null, null, null, TOMORROW, TOMORROW, null, null);

            assertThat(paths(request)).containsExactlyInAnyOrder("createdDate", "lastUpdatedDate");
        }

        @Test
        @DisplayName("past dates are accepted")
        void pastDatesAreValid() {
            SearchFieldsRequest request = new SearchFieldsRequest(null, null, null, null, null, null,
                    null, null, null, LocalDate.now().minusYears(2), LocalDate.now(), null, null);

            assertThat(validator.validate(request)).isEmpty();
        }
    }

    @Nested
    @DisplayName("FilterSearchRequest")
    class Filters {

        @Test
        @DisplayName("null lists normalize to empty and are valid")
        void nullListsAreValid() {
            FilterSearchRequest request =
                    new FilterSearchRequest(null, null, null, null, null, null);

            assertThat(validator.validate(request)).isEmpty();
            assertThat(request.states()).isEmpty();
        }

        @Test
        @DisplayName("a list at the 200-value bound is accepted")
        void boundaryListIsValid() {
            FilterSearchRequest request = new FilterSearchRequest(
                    Collections.nCopies(200, "MH"), null, null, null, null, null);

            assertThat(validator.validate(request)).isEmpty();
        }

        // Each list goes straight to a terms clause, so an unbounded list is the same class of exposure
        // as an unbounded wildcard.
        @Test
        @DisplayName("a list past the bound is rejected")
        void oversizedListIsRejected() {
            List<String> tooMany = Collections.nCopies(201, "MH");
            FilterSearchRequest request =
                    new FilterSearchRequest(tooMany, tooMany, null, null, null, null);

            assertThat(paths(request)).containsExactlyInAnyOrder("states", "districts");
        }
    }

    @Nested
    @DisplayName("ComplaintSearchRequest")
    class Root {

        @Test
        @DisplayName("an entirely empty request is valid")
        void allNullIsValid() {
            assertThat(validator.validate(
                    new ComplaintSearchRequest(null, null, null, null, null, null, null, null, null)))
                    .isEmpty();
        }

        // Without @Valid on the nested components Bean Validation does not traverse into them and every
        // constraint they declare is silently skipped. This is the guard against shipping the whole
        // validation layer as a no-op.
        @Test
        @DisplayName("violations inside nested components are reported with a nested path")
        void nestedComponentsAreTraversed() {
            ComplaintSearchRequest request = new ComplaintSearchRequest(
                    new AdvancedSearchRequest(repeat(51), null, null, null, null, null, null, null,
                            null, null, null, null, null),
                    new FilterSearchRequest(Collections.nCopies(201, "MH"), null, null, null, null, null),
                    null, null, null, null, null,
                    new SearchFieldsRequest(null, null, null, null, null, null, null, null,
                            null, null, null, "URGENT", null),
                    null);

            assertThat(paths(request)).containsExactlyInAnyOrder(
                    "advancedSearch.complaintNumber", "filters.states", "search.priority");
        }

        // statusCode here carries a UI phrase ("All Complaints", "New Complaints") resolved by
        // applyStatusCode, which is a different vocabulary from ComplaintStatus — hence a length bound
        // only, and no @EnumValue. Annotating it would reject every value the frontend sends.
        @Test
        @DisplayName("statusCode accepts UI phrases and is bounded by length only")
        void statusCodeAcceptsUiPhrases() {
            assertThat(validator.validate(root("All Complaints"))).isEmpty();
            assertThat(validator.validate(root("Complaint Assigned To Me"))).isEmpty();
            assertThat(paths(root(repeat(61)))).containsExactly("statusCode");
        }

        private ComplaintSearchRequest root(String statusCode) {
            return new ComplaintSearchRequest(null, null, statusCode, null, null, null, null, null, null);
        }

        @Test
        @DisplayName("over-length kpiCards and tabs are rejected")
        void overLengthPhrasesRejected() {
            ComplaintSearchRequest request = new ComplaintSearchRequest(
                    null, null, null, repeat(61), repeat(61), null, null, null, null);

            assertThat(paths(request)).containsExactlyInAnyOrder("kpiCards", "tabs");
        }

        @Test
        @DisplayName("over-length assignedOfficer is rejected")
        void overLengthAssignedOfficerRejected() {
            ComplaintSearchRequest request = new ComplaintSearchRequest(
                    null, null, null, null, null, null, null, null, repeat(101));

            assertThat(paths(request)).containsExactly("assignedOfficer");
        }
    }
}
