package com.hrms.cms.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UST5: the declaration checkbox used to exist only as a [disabled] binding on the Submit button, so a
 * direct POST filed a complaint with no consent at all. These tests pin the server-side gate.
 */
class FileComplaintRequestConsentTest {

    private static final String VIOLATED_PROPERTY = "declarationAcceptedWhenRequired";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (factory != null) factory.close();
    }

    private FileComplaintRequest minimalRequest(String filingType, Boolean declarationAccepted) {
        FileComplaintRequest req = new FileComplaintRequest();
        req.setComplainantName("Asha Menon");
        req.setSubject("Unauthorised debit not reversed");
        req.setDescription("An amount was debited without my authorisation and has not been reversed.");
        req.setFilingType(filingType);
        req.setDeclarationAccepted(declarationAccepted);
        return req;
    }

    private boolean rejectedForConsent(FileComplaintRequest req) {
        Set<ConstraintViolation<FileComplaintRequest>> violations = validator.validate(req);
        return violations.stream()
                .anyMatch(v -> VIOLATED_PROPERTY.equals(v.getPropertyPath().toString()));
    }

    @Test
    @DisplayName("ONLINE filing without the declaration is rejected")
    void onlineWithoutDeclarationRejected() {
        assertThat(rejectedForConsent(minimalRequest("ONLINE", false))).isTrue();
    }

    @Test
    @DisplayName("ONLINE filing that omits the declaration entirely is rejected")
    void onlineWithNullDeclarationRejected() {
        assertThat(rejectedForConsent(minimalRequest("ONLINE", null))).isTrue();
    }

    @Test
    @DisplayName("a request with no filingType is treated as online and still requires the declaration")
    void absentFilingTypeTreatedAsOnline() {
        assertThat(rejectedForConsent(minimalRequest(null, null))).isTrue();
    }

    @Test
    @DisplayName("ONLINE filing with the declaration accepted passes")
    void onlineWithDeclarationAccepted() {
        assertThat(rejectedForConsent(minimalRequest("ONLINE", true))).isFalse();
    }

    @Test
    @DisplayName("filingType is matched case-insensitively, so 'online' is still gated")
    void filingTypeMatchIsCaseInsensitive() {
        FileComplaintRequest req = minimalRequest("ONLINE", null);
        req.setFilingType("online");
        assertThat(rejectedForConsent(req)).isTrue();
    }

    /**
     * Email, physical letter and walk-in intake never displayed a checkbox. Rejecting them would drop
     * legitimate complaints on the floor, so the gate must not fire for those channels.
     */
    @ParameterizedTest
    @ValueSource(strings = {"EMAIL", "PHYSICAL_LETTER", "WALK_IN"})
    @DisplayName("offline intake channels are exempt from the declaration requirement")
    void offlineChannelsExempt(String filingType) {
        assertThat(rejectedForConsent(minimalRequest(filingType, null))).isFalse();
    }
}
