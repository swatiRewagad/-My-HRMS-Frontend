package com.hrms.cms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * RBIO Compensation validation service.
 * Enforces RBI Ombudsman Scheme compensation caps as BLOCKING validation.
 * <p>
 * Caps (per RB-IOS 2021):
 * - Consequential loss: max 30,00,000 (30 Lakh)
 * - Time/harassment: max 3,00,000 (3 Lakh)
 * - Combined total: must not exceed 30,00,000
 */
@Service
@Slf4j
public class RbioCompensationService {

    /** Maximum compensation for consequential loss (Rs 30 Lakh) */
    private static final BigDecimal MAX_CONSEQUENTIAL_LOSS = new BigDecimal("3000000");

    /** Maximum compensation for mental agony/time/harassment (Rs 3 Lakh) */
    private static final BigDecimal MAX_TIME_HARASSMENT = new BigDecimal("300000");

    /** Maximum combined compensation (Rs 30 Lakh) */
    private static final BigDecimal MAX_COMBINED = new BigDecimal("3000000");

    /**
     * Validates the award amount against RBI Ombudsman caps.
     * This is a BLOCKING validation — throws IllegalArgumentException if cap exceeded.
     *
     * @param amount           the proposed award amount
     * @param compensationType one of: CONSEQUENTIAL_LOSS, TIME_HARASSMENT, COMBINED
     * @throws IllegalArgumentException if the amount exceeds the applicable cap
     */
    public void validateAward(BigDecimal amount, String compensationType) {
        if (amount == null) {
            throw new IllegalArgumentException("Award amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Award amount must not be negative");
        }
        if (compensationType == null || compensationType.isBlank()) {
            throw new IllegalArgumentException("Compensation type must be specified");
        }

        BigDecimal maxAllowed = getMaxAllowed(compensationType);
        if (amount.compareTo(maxAllowed) > 0) {
            throw new IllegalArgumentException(String.format(
                    "Award amount Rs %s exceeds the maximum permitted cap of Rs %s for compensation type '%s'. " +
                            "Per RBI Ombudsman Scheme, this award cannot be issued.",
                    amount.toPlainString(), maxAllowed.toPlainString(), compensationType));
        }

        log.info("Award validation passed: amount={}, type={}, cap={}", amount, compensationType, maxAllowed);
    }

    /**
     * Calculates the compensation band for reporting purposes.
     *
     * @param amount the award amount
     * @return LOW (<=1L), MEDIUM (<=10L), HIGH (<=20L), MAXIMUM (<=30L)
     */
    public String calculateCompensationBand(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "NONE";
        }
        if (amount.compareTo(new BigDecimal("100000")) <= 0) {
            return "LOW";
        }
        if (amount.compareTo(new BigDecimal("1000000")) <= 0) {
            return "MEDIUM";
        }
        if (amount.compareTo(new BigDecimal("2000000")) <= 0) {
            return "HIGH";
        }
        return "MAXIMUM";
    }

    /**
     * Returns the maximum permitted amount for a given compensation type.
     *
     * @param compensationType one of: CONSEQUENTIAL_LOSS, TIME_HARASSMENT, COMBINED
     * @return the maximum permitted BigDecimal amount
     * @throws IllegalArgumentException if the compensation type is unknown
     */
    public BigDecimal getMaxAllowed(String compensationType) {
        if (compensationType == null) {
            throw new IllegalArgumentException("Compensation type must not be null");
        }

        return switch (compensationType.toUpperCase()) {
            case "CONSEQUENTIAL_LOSS" -> MAX_CONSEQUENTIAL_LOSS;
            case "TIME_HARASSMENT" -> MAX_TIME_HARASSMENT;
            case "COMBINED" -> MAX_COMBINED;
            default -> throw new IllegalArgumentException(
                    "Unknown compensation type: " + compensationType +
                            ". Valid types are: CONSEQUENTIAL_LOSS, TIME_HARASSMENT, COMBINED");
        };
    }
}
