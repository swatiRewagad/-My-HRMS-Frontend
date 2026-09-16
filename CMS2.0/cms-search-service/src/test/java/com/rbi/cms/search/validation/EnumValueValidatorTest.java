package com.rbi.cms.search.validation;

import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.enums.FilingType;
import com.rbi.cms.common.enums.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the validator through a real {@link Validator} rather than calling {@code isValid} directly,
 * so the annotation-to-validator wiring is covered too — that wiring is the part most likely to be
 * silently absent.
 */
class EnumValueValidatorTest {

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

    record StatusHolder(@EnumValue(ComplaintStatus.class) String status) { }

    record FilingTypeHolder(@EnumValue(FilingType.class) String mode) { }

    record PriorityHolder(@EnumValue(Priority.class) String priority) { }

    // A blank filter means "do not filter on this column", so it must never be an error. This is the
    // guarantee that keeps a cleared grid control from turning into a 400.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    @DisplayName("null and blank are accepted for every enum")
    void blankIsAlwaysValid(String value) {
        assertThat(validator.validate(new StatusHolder(value))).isEmpty();
        assertThat(validator.validate(new FilingTypeHolder(value))).isEmpty();
        assertThat(validator.validate(new PriorityHolder(value))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "IN_PROGRESS",       // constant name
            "in_progress",       // lower snake, as cms-backend persists it
            "In Progress",       // display label
            "in progress",       // label, lower case
            "  IN_PROGRESS  ",   // surrounding whitespace
            "NEW",
            "SENT_TO_OTHER_REGULATED_BODIES",
            "Complaint Settled"
    })
    @DisplayName("constant names and display labels are both accepted, in any case")
    void acceptsNamesAndLabels(String value) {
        assertThat(validator.validate(new StatusHolder(value))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PORTAL", "portal", "Portal", "EMAIL", "Letter"})
    @DisplayName("FilingType accepts names and labels")
    void acceptsFilingType(String value) {
        assertThat(validator.validate(new FilingTypeHolder(value))).isEmpty();
    }

    // Priority does not implement LabeledEnum, so there are no labels to match — name only. Proves the
    // validator degrades gracefully for a plain enum instead of failing to initialize.
    @ParameterizedTest
    @ValueSource(strings = {"LOW", "low", "Medium", "CRITICAL"})
    @DisplayName("a label-less enum still matches on constant name")
    void acceptsPriorityByNameOnly(String value) {
        assertThat(validator.validate(new PriorityHolder(value))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-status", "NEWLY", "CLOSED_FOREVER", "0", "New Complaints"})
    @DisplayName("unknown vocabulary is rejected rather than matching nothing")
    void rejectsUnknownStatus(String value) {
        assertThat(validator.validate(new StatusHolder(value))).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAX", "post", "Courier"})
    @DisplayName("unknown filing type is rejected")
    void rejectsUnknownFilingType(String value) {
        assertThat(validator.validate(new FilingTypeHolder(value))).hasSize(1);
    }

    @Test
    @DisplayName("the message enumerates the allowed values, so the caller can correct the request")
    void messageListsAllowedValues() {
        Set<ConstraintViolation<FilingTypeHolder>> violations =
                validator.validate(new FilingTypeHolder("FAX"));

        assertThat(violations).hasSize(1);
        ConstraintViolation<FilingTypeHolder> violation = violations.iterator().next();
        assertThat(violation.getMessage()).isEqualTo("must be one of: EMAIL, LETTER, PORTAL");
        assertThat(violation.getPropertyPath()).hasToString("mode");
    }

    // Regression guard for the whole point of this constraint: an invalid status used to produce a term
    // query that matched nothing, which the caller could not distinguish from "no complaints match".
    @Test
    @DisplayName("the violation names the offending field")
    void violationIdentifiesField() {
        Set<ConstraintViolation<StatusHolder>> violations =
                validator.validate(new StatusHolder("garbage"));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("status");
    }
}
