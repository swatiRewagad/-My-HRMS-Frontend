package com.hrms.cms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for RbioCompensationService.
 * Validates RBI Ombudsman Scheme compensation caps and band calculations.
 */
class RbioCompensationServiceTest {

    private RbioCompensationService compensationService;

    @BeforeEach
    void setUp() {
        compensationService = new RbioCompensationService();
    }

    // ═══════════════════════════════════════════════════════════════════
    // validateAward() - CONSEQUENTIAL_LOSS cap (30 Lakh)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validateAward() - CONSEQUENTIAL_LOSS")
    class ValidateAwardConsequentialLoss {

        @Test
        @DisplayName("should pass when amount equals the cap (Rs 30 Lakh)")
        void shouldPassAtCap() {
            assertThatCode(() -> compensationService.validateAward(
                    new BigDecimal("3000000"), "CONSEQUENTIAL_LOSS"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should pass when amount is below the cap")
        void shouldPassBelowCap() {
            assertThatCode(() -> compensationService.validateAward(
                    new BigDecimal("2500000"), "CONSEQUENTIAL_LOSS"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should throw when amount exceeds the cap")
        void shouldThrowWhenExceedsCap() {
            assertThatThrownBy(() -> compensationService.validateAward(
                    new BigDecimal("3000001"), "CONSEQUENTIAL_LOSS"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds the maximum permitted cap")
                    .hasMessageContaining("3000000")
                    .hasMessageContaining("CONSEQUENTIAL_LOSS");
        }

        @Test
        @DisplayName("should pass with very small amount")
        void shouldPassWithSmallAmount() {
            assertThatCode(() -> compensationService.validateAward(
                    new BigDecimal("1"), "CONSEQUENTIAL_LOSS"))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // validateAward() - TIME_HARASSMENT cap (3 Lakh)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validateAward() - TIME_HARASSMENT")
    class ValidateAwardTimeHarassment {

        @Test
        @DisplayName("should pass when amount equals the cap (Rs 3 Lakh)")
        void shouldPassAtCap() {
            assertThatCode(() -> compensationService.validateAward(
                    new BigDecimal("300000"), "TIME_HARASSMENT"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should pass when amount is below the cap")
        void shouldPassBelowCap() {
            assertThatCode(() -> compensationService.validateAward(
                    new BigDecimal("200000"), "TIME_HARASSMENT"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should throw when amount exceeds the cap")
        void shouldThrowWhenExceedsCap() {
            assertThatThrownBy(() -> compensationService.validateAward(
                    new BigDecimal("300001"), "TIME_HARASSMENT"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds the maximum permitted cap")
                    .hasMessageContaining("300000")
                    .hasMessageContaining("TIME_HARASSMENT");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // validateAward() - COMBINED cap (30 Lakh)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validateAward() - COMBINED")
    class ValidateAwardCombined {

        @Test
        @DisplayName("should pass when combined amount is at cap (Rs 30 Lakh)")
        void shouldPassAtCap() {
            assertThatCode(() -> compensationService.validateAward(
                    new BigDecimal("3000000"), "COMBINED"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should pass when combined amount is below cap")
        void shouldPassBelowCap() {
            assertThatCode(() -> compensationService.validateAward(
                    new BigDecimal("1500000"), "COMBINED"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should throw when combined amount exceeds cap")
        void shouldThrowWhenExceedsCap() {
            assertThatThrownBy(() -> compensationService.validateAward(
                    new BigDecimal("3500000"), "COMBINED"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds the maximum permitted cap")
                    .hasMessageContaining("3000000");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // validateAward() - Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validateAward() - Edge Cases")
    class ValidateAwardEdgeCases {

        @Test
        @DisplayName("should pass with zero amount")
        void shouldPassWithZero() {
            assertThatCode(() -> compensationService.validateAward(
                    BigDecimal.ZERO, "CONSEQUENTIAL_LOSS"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should throw for negative amount")
        void shouldThrowForNegative() {
            assertThatThrownBy(() -> compensationService.validateAward(
                    new BigDecimal("-100"), "CONSEQUENTIAL_LOSS"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be negative");
        }

        @Test
        @DisplayName("should throw for null amount")
        void shouldThrowForNullAmount() {
            assertThatThrownBy(() -> compensationService.validateAward(null, "CONSEQUENTIAL_LOSS"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        @DisplayName("should throw for null compensation type")
        void shouldThrowForNullType() {
            assertThatThrownBy(() -> compensationService.validateAward(new BigDecimal("100000"), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Compensation type must be specified");
        }

        @Test
        @DisplayName("should throw for blank compensation type")
        void shouldThrowForBlankType() {
            assertThatThrownBy(() -> compensationService.validateAward(new BigDecimal("100000"), ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Compensation type must be specified");
        }

        @Test
        @DisplayName("should be case-insensitive for compensation type")
        void shouldBeCaseInsensitive() {
            assertThatCode(() -> compensationService.validateAward(
                    new BigDecimal("200000"), "consequential_loss"))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // calculateCompensationBand()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateCompensationBand()")
    class CalculateCompensationBand {

        @Test
        @DisplayName("should return NONE for null amount")
        void shouldReturnNoneForNull() {
            assertThat(compensationService.calculateCompensationBand(null)).isEqualTo("NONE");
        }

        @Test
        @DisplayName("should return NONE for zero amount")
        void shouldReturnNoneForZero() {
            assertThat(compensationService.calculateCompensationBand(BigDecimal.ZERO)).isEqualTo("NONE");
        }

        @Test
        @DisplayName("should return NONE for negative amount")
        void shouldReturnNoneForNegative() {
            assertThat(compensationService.calculateCompensationBand(new BigDecimal("-50000"))).isEqualTo("NONE");
        }

        @ParameterizedTest
        @CsvSource({
                "50000, LOW",
                "100000, LOW",
                "1, LOW"
        })
        @DisplayName("should return LOW for amount <= 1 Lakh")
        void shouldReturnLow(String amount, String expected) {
            assertThat(compensationService.calculateCompensationBand(new BigDecimal(amount))).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "100001, MEDIUM",
                "500000, MEDIUM",
                "1000000, MEDIUM"
        })
        @DisplayName("should return MEDIUM for amount > 1 Lakh and <= 10 Lakh")
        void shouldReturnMedium(String amount, String expected) {
            assertThat(compensationService.calculateCompensationBand(new BigDecimal(amount))).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "1000001, HIGH",
                "1500000, HIGH",
                "2000000, HIGH"
        })
        @DisplayName("should return HIGH for amount > 10 Lakh and <= 20 Lakh")
        void shouldReturnHigh(String amount, String expected) {
            assertThat(compensationService.calculateCompensationBand(new BigDecimal(amount))).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "2000001, MAXIMUM",
                "2500000, MAXIMUM",
                "3000000, MAXIMUM"
        })
        @DisplayName("should return MAXIMUM for amount > 20 Lakh")
        void shouldReturnMaximum(String amount, String expected) {
            assertThat(compensationService.calculateCompensationBand(new BigDecimal(amount))).isEqualTo(expected);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getMaxAllowed()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getMaxAllowed()")
    class GetMaxAllowed {

        @Test
        @DisplayName("should return Rs 30 Lakh for CONSEQUENTIAL_LOSS")
        void shouldReturnCapForConsequentialLoss() {
            assertThat(compensationService.getMaxAllowed("CONSEQUENTIAL_LOSS"))
                    .isEqualByComparingTo(new BigDecimal("3000000"));
        }

        @Test
        @DisplayName("should return Rs 3 Lakh for TIME_HARASSMENT")
        void shouldReturnCapForTimeHarassment() {
            assertThat(compensationService.getMaxAllowed("TIME_HARASSMENT"))
                    .isEqualByComparingTo(new BigDecimal("300000"));
        }

        @Test
        @DisplayName("should return Rs 30 Lakh for COMBINED")
        void shouldReturnCapForCombined() {
            assertThat(compensationService.getMaxAllowed("COMBINED"))
                    .isEqualByComparingTo(new BigDecimal("3000000"));
        }

        @Test
        @DisplayName("should throw for unknown compensation type")
        void shouldThrowForUnknownType() {
            assertThatThrownBy(() -> compensationService.getMaxAllowed("UNKNOWN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown compensation type");
        }

        @Test
        @DisplayName("should throw for null compensation type")
        void shouldThrowForNullType() {
            assertThatThrownBy(() -> compensationService.getMaxAllowed(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        @DisplayName("should be case-insensitive")
        void shouldBeCaseInsensitive() {
            assertThat(compensationService.getMaxAllowed("consequential_loss"))
                    .isEqualByComparingTo(new BigDecimal("3000000"));
            assertThat(compensationService.getMaxAllowed("time_harassment"))
                    .isEqualByComparingTo(new BigDecimal("300000"));
        }
    }
}
