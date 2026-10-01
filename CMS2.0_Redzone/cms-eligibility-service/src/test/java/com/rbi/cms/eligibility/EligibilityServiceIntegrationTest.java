package com.rbi.cms.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.enums.EligibilityOutcome;
import com.rbi.cms.eligibility.dto.EligibilityCheckRequest;
import com.rbi.cms.eligibility.dto.EligibilityCheckResponse;
import com.rbi.cms.eligibility.entity.QuestionMaster;
import com.rbi.cms.eligibility.repository.EligibilityAuditRepository;
import com.rbi.cms.eligibility.repository.QuestionMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class EligibilityServiceIntegrationTest {

    @Container
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
            .withDatabaseName("CMSTEST")
            .withUsername("cms_test")
            .withPassword("cms_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracle::getJdbcUrl);
        registry.add("spring.datasource.username", oracle::getUsername);
        registry.add("spring.datasource.password", oracle::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuestionMasterRepository questionRepository;

    @Autowired
    private EligibilityAuditRepository auditRepository;

    @BeforeEach
    void setup() {
        auditRepository.deleteAll();
        questionRepository.deleteAll();

        questionRepository.save(QuestionMaster.builder()
                .questionCode("Q_COURT_MATTER")
                .questionText("Is the matter pending in court?")
                .questionType("YES_NO")
                .category("GENERAL")
                .isMandatory(true)
                .displayOrder(1)
                .options("YES|NO")
                .expectedAnswer("NO")
                .ruleAttribute("courtMatterPending")
                .isActive(true)
                .build());

        questionRepository.save(QuestionMaster.builder()
                .questionCode("Q_APPROACHED_BANK")
                .questionText("Have you approached the bank?")
                .questionType("YES_NO")
                .category("GENERAL")
                .isMandatory(true)
                .displayOrder(2)
                .options("YES|NO")
                .expectedAnswer("YES")
                .ruleAttribute("approachedBank")
                .isActive(true)
                .build());

        questionRepository.save(QuestionMaster.builder()
                .questionCode("Q_WAITING_PERIOD")
                .questionText("Has 30 days elapsed?")
                .questionType("YES_NO")
                .category("GENERAL")
                .isMandatory(true)
                .displayOrder(3)
                .options("YES|NO")
                .expectedAnswer("YES")
                .ruleAttribute("waitingPeriodCompleted")
                .isActive(true)
                .build());
    }

    @Test
    @DisplayName("GET /eligibility/questions - returns active questions in order")
    void shouldReturnActiveQuestions() throws Exception {
        mockMvc.perform(get("/api/v1/eligibility/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].questionCode").value("Q_COURT_MATTER"));
    }

    @Test
    @DisplayName("POST /eligibility/check - eligible when all criteria met")
    void shouldReturnEligibleWhenAllCriteriaMet() throws Exception {
        EligibilityCheckRequest request = EligibilityCheckRequest.builder()
                .channel("WEB_PORTAL")
                .sessionId("test-session-001")
                .answers(Map.of(
                        "Q_COURT_MATTER", "NO",
                        "Q_APPROACHED_BANK", "YES",
                        "Q_WAITING_PERIOD", "YES",
                        "Q_DUPLICATE", "NO"
                ))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/eligibility/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.outcome").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.nextAction").value("PROCEED_TO_REGISTRATION"))
                .andReturn();

        assertThat(auditRepository.findBySessionIdOrderByEvaluatedAtDesc("test-session-001"))
                .hasSize(1)
                .first()
                .satisfies(audit -> assertThat(audit.getOutcome()).isEqualTo(EligibilityOutcome.ELIGIBLE));
    }

    @Test
    @DisplayName("POST /eligibility/check - not eligible when court matter pending")
    void shouldReturnNotEligibleWhenCourtMatterPending() throws Exception {
        EligibilityCheckRequest request = EligibilityCheckRequest.builder()
                .channel("WEB_PORTAL")
                .sessionId("test-session-002")
                .answers(Map.of(
                        "Q_COURT_MATTER", "YES",
                        "Q_APPROACHED_BANK", "YES",
                        "Q_WAITING_PERIOD", "YES"
                ))
                .build();

        mockMvc.perform(post("/api/v1/eligibility/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.data.reasonCode").value("COURT_MATTER_PENDING"))
                .andExpect(jsonPath("$.data.nextAction").value("SHOW_ADVISORY"))
                .andExpect(jsonPath("$.data.standardResponse").isNotEmpty())
                .andExpect(jsonPath("$.data.reference").isNotEmpty());
    }

    @Test
    @DisplayName("POST /eligibility/check - not eligible when bank not approached")
    void shouldReturnNotEligibleWhenBankNotApproached() throws Exception {
        EligibilityCheckRequest request = EligibilityCheckRequest.builder()
                .channel("WEB_PORTAL")
                .sessionId("test-session-003")
                .answers(Map.of(
                        "Q_COURT_MATTER", "NO",
                        "Q_APPROACHED_BANK", "NO",
                        "Q_WAITING_PERIOD", "NO"
                ))
                .build();

        mockMvc.perform(post("/api/v1/eligibility/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.data.reasonCode").value("BANK_NOT_APPROACHED"));
    }

    @Test
    @DisplayName("POST /eligibility/check - validation error when answers empty")
    void shouldReturn400WhenAnswersEmpty() throws Exception {
        EligibilityCheckRequest request = EligibilityCheckRequest.builder()
                .channel("WEB_PORTAL")
                .answers(Map.of())
                .build();

        mockMvc.perform(post("/api/v1/eligibility/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
