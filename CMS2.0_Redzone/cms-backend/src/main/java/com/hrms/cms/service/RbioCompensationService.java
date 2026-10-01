package com.hrms.cms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * RBIO Compensation validation service.
 * Enforces RBI Ombudsman Scheme compensation caps as BLOCKING validation.
 * <p>
 * Caps (per RB-IOS 2021):
 * - Consequential loss: max 30,00,000 (30 Lakh)
 * - Time/harassment: max 3,00,000 (3 Lakh)
 * - Combined total: must not exceed 33,00,000 (33 Lakh) = the sum of the two component caps
 *
 * <p>THE COMBINED CAP IS THE SUM OF ITS COMPONENTS. It was previously 30,00,000 — equal to the
 * consequential-loss cap alone — which made the harassment allowance partly unusable: a complainant
 * awarded the full 30 Lakh financial loss could receive nothing for mental harassment, because the
 * combined ceiling was already reached. The product owner ruled the combined cap is 33,00,000.
 * {@code RbioCompensationServiceTest} asserts combined == consequential + harassment so the three
 * figures cannot silently drift apart again.
 *
 * <p>THE CAPS ARE CONFIGURATION, NOT CONSTANTS (UST541-542). They were {@code private static final}
 * literals, so amending a statutory figure required a code change and a redeploy — and the same numbers
 * were independently duplicated in seven other places, so any change would have been applied
 * inconsistently. They now read from SYSTEM_CONFIG through {@link SystemConfigService}, making an
 * amendment a one-row update that takes effect within that service's 30-second TTL.
 *
 * <p>THE DEFAULTS ARE THE CURRENT VALUES, DELIBERATELY. With no config rows present this service behaves
 * exactly as it did before, so introducing the indirection cannot change a single award outcome. The
 * values themselves carry no verified statutory provenance — {@code database/V56__aa_order_statutory_guards.sql}
 * already records that concern — so they are preserved rather than "corrected" here. Changing them is a
 * legal decision, and it is now a config change rather than a release.
 *
 * <p>The band boundaries are configurable alongside the caps for the same reason: {@code MAXIMUM} means
 * "at or near the ceiling", so raising a cap while leaving the bands fixed would silently redefine what
 * the reporting bands mean.
 */
@Service
@Slf4j
public class RbioCompensationService {

    static final String CFG_MAX_CONSEQUENTIAL_LOSS = "cms.rbio.compensation.max_consequential_loss";
    static final String CFG_MAX_TIME_HARASSMENT = "cms.rbio.compensation.max_time_harassment";
    static final String CFG_MAX_COMBINED = "cms.rbio.compensation.max_combined";
    static final String CFG_BAND_LOW_UPTO = "cms.rbio.compensation.band_low_upto";
    static final String CFG_BAND_MEDIUM_UPTO = "cms.rbio.compensation.band_medium_upto";
    static final String CFG_BAND_HIGH_UPTO = "cms.rbio.compensation.band_high_upto";

    /** Default cap for consequential loss (Rs 30 Lakh) — the behaviour-neutral fallback. */
    static final BigDecimal DEFAULT_MAX_CONSEQUENTIAL_LOSS = new BigDecimal("3000000");

    /** Default cap for mental agony/time/harassment (Rs 3 Lakh) — the behaviour-neutral fallback. */
    static final BigDecimal DEFAULT_MAX_TIME_HARASSMENT = new BigDecimal("300000");

    /**
     * Default combined cap (Rs 33 Lakh) = consequential loss + time/harassment.
     *
     * <p>Unlike the two component caps this is NOT behaviour-neutral: it was 3000000, equal to the
     * consequential-loss cap, which capped the two components below their sum and made the harassment
     * allowance unreachable once a full financial award was granted.
     */
    static final BigDecimal DEFAULT_MAX_COMBINED =
            DEFAULT_MAX_CONSEQUENTIAL_LOSS.add(DEFAULT_MAX_TIME_HARASSMENT);

    static final BigDecimal DEFAULT_BAND_LOW_UPTO = new BigDecimal("100000");
    static final BigDecimal DEFAULT_BAND_MEDIUM_UPTO = new BigDecimal("1000000");
    static final BigDecimal DEFAULT_BAND_HIGH_UPTO = new BigDecimal("2000000");

    /**
     * Null means "no config available, use the documented defaults" — the same behaviour as an empty
     * SYSTEM_CONFIG table. Kept nullable so the service stays constructible without a Spring context;
     * a blocking validator that cannot be unit-tested in isolation would be a worse trade.
     */
    private final SystemConfigService systemConfigService;

    public RbioCompensationService() {
        this(null);
    }

    @Autowired
    public RbioCompensationService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * Reads a configured amount, falling back to the statutory default.
     *
     * <p>A non-positive or unparseable value falls back rather than throwing. {@link SystemConfigService}
     * already logs the offending row, and refusing every award because of one bad config value would be a
     * worse failure than continuing with the documented default.
     */
    private BigDecimal configuredAmount(String key, BigDecimal fallback) {
        if (systemConfigService == null) {
            return fallback;
        }
        String raw = systemConfigService.getString(key, null);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("SYSTEM_CONFIG {} is not positive: '{}' — falling back to {}", key, raw, fallback);
                return fallback;
            }
            return value;
        } catch (NumberFormatException e) {
            log.warn("SYSTEM_CONFIG {} is not a number: '{}' — falling back to {}", key, raw, fallback);
            return fallback;
        }
    }

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
        if (amount.compareTo(configuredAmount(CFG_BAND_LOW_UPTO, DEFAULT_BAND_LOW_UPTO)) <= 0) {
            return "LOW";
        }
        if (amount.compareTo(configuredAmount(CFG_BAND_MEDIUM_UPTO, DEFAULT_BAND_MEDIUM_UPTO)) <= 0) {
            return "MEDIUM";
        }
        if (amount.compareTo(configuredAmount(CFG_BAND_HIGH_UPTO, DEFAULT_BAND_HIGH_UPTO)) <= 0) {
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
            case "CONSEQUENTIAL_LOSS" ->
                    configuredAmount(CFG_MAX_CONSEQUENTIAL_LOSS, DEFAULT_MAX_CONSEQUENTIAL_LOSS);
            case "TIME_HARASSMENT" ->
                    configuredAmount(CFG_MAX_TIME_HARASSMENT, DEFAULT_MAX_TIME_HARASSMENT);
            case "COMBINED" -> configuredAmount(CFG_MAX_COMBINED, DEFAULT_MAX_COMBINED);
            default -> throw new IllegalArgumentException(
                    "Unknown compensation type: " + compensationType +
                            ". Valid types are: CONSEQUENTIAL_LOSS, TIME_HARASSMENT, COMBINED");
        };
    }
}
