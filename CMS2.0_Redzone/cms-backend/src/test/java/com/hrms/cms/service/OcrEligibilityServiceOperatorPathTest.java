package com.hrms.cms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The interactive operator upload gate (UST778).
 *
 * <h2>Why a separate test class from the ingest path</h2>
 * The two paths deliberately differ on ONE point — what an ABSENT confidence signal means — and that
 * difference is easy to "simplify" away by someone who has not read the reasoning. These tests pin it.
 *
 * <p>The underlying constraint: only {@code PaddleOcrProvider} emits {@code _confidence}, and the
 * dev-local chain is Groq alone with {@code paddle-ocr-url} empty. So reusing the ingest gate here would
 * classify EVERY interactive upload as low-confidence and suppress all prefill — a gate that appears to
 * work while in fact just disabling the feature.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OcrEligibilityService — the operator upload path")
class OcrEligibilityServiceOperatorPathTest {

    @Mock private LanguageTranslationService translationService;

    private OcrEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new OcrEligibilityService(translationService);
        ReflectionTestUtils.setField(service, "minConfidenceForPrefill", 70);
        ReflectionTestUtils.setField(service, "requireConfidenceForOperatorPrefill", false);
        when(translationService.languageName(anyString())).thenReturn("Language");
    }

    private void detects(String language) {
        when(translationService.detectLanguage(anyString())).thenReturn(language);
    }

    @Nested
    @DisplayName("The script gate is absolute on both paths")
    class ScriptGateTests {

        @Test
        @DisplayName("vernacular content is routed to manual entry even with perfect confidence")
        void vernacularAlwaysManual() {
            detects("hi");

            OcrEligibilityService.Assessment result =
                    service.assessOperatorUpload("कुछ पाठ", 100);

            // This is UST778's actual requirement, and it does not depend on any provider capability.
            assertThat(result.requiresManualEntry()).isTrue();
            assertThat(result.decision())
                    .isEqualTo(OcrEligibilityService.Decision.VERNACULAR_MANUAL_ENTRY);
            assertThat(result.vernacular()).isTrue();
            assertThat(result.reasonKey()).isEqualTo("intake.vernacular_manual_entry_required");
        }

        @Test
        @DisplayName("typed English with good confidence is allowed")
        void englishAllowed() {
            detects("en");

            assertThat(service.assessOperatorUpload("some english text", 95).ocrAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Confidence: measured-but-low blocks, unmeasurable warns")
    class ConfidenceTests {

        @Test
        @DisplayName("a measured low confidence blocks prefill")
        void measuredLowBlocks() {
            detects("en");

            OcrEligibilityService.Assessment result =
                    service.assessOperatorUpload("english text", 40);

            assertThat(result.requiresManualEntry()).isTrue();
            assertThat(result.decision())
                    .isEqualTo(OcrEligibilityService.Decision.LOW_CONFIDENCE_MANUAL_ENTRY);
        }

        @Test
        @DisplayName("confidence exactly at the threshold is allowed (boundary)")
        void thresholdBoundaryAllowed() {
            detects("en");

            assertThat(service.assessOperatorUpload("english text", 70).ocrAllowed()).isTrue();
        }

        @Test
        @DisplayName("one below the threshold is blocked (boundary)")
        void justBelowThresholdBlocked() {
            detects("en");

            assertThat(service.assessOperatorUpload("english text", 69).requiresManualEntry()).isTrue();
        }

        @Test
        @DisplayName("an ABSENT confidence still allows prefill on the operator path, by default")
        void absentConfidenceAllowedByDefault() {
            detects("en");

            // The deliberate divergence from the ingest gate. Groq reports no confidence, so treating
            // null as blocking would disable operator prefill entirely in the default configuration.
            // The operator is looking at the document, and every prefilled field is provenance-marked.
            assertThat(service.assessOperatorUpload("english text", null).ocrAllowed()).isTrue();
        }

        @Test
        @DisplayName("an absent confidence BLOCKS once the strict flag is on")
        void absentConfidenceBlocksWhenStrict() {
            detects("en");
            ReflectionTestUtils.setField(service, "requireConfidenceForOperatorPrefill", true);

            OcrEligibilityService.Assessment result =
                    service.assessOperatorUpload("english text", null);

            assertThat(result.requiresManualEntry()).isTrue();
            assertThat(result.decision())
                    .isEqualTo(OcrEligibilityService.Decision.LOW_CONFIDENCE_MANUAL_ENTRY);
        }

        @Test
        @DisplayName("the ingest gate still treats an absent confidence as blocking")
        void ingestPathUnchanged() {
            detects("en");

            // Proves the two paths remain distinct. Ingest is unattended, so an unmeasurable scan must
            // not populate a legal record there.
            assertThat(service.assessExtractedText("english text", null).requiresManualEntry()).isTrue();
        }
    }
}
