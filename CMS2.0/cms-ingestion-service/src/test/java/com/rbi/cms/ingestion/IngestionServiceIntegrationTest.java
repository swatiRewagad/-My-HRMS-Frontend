package com.rbi.cms.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.enums.OutboxEventStatus;
import com.rbi.cms.ingestion.dto.ComplaintRegistrationRequest;
import com.rbi.cms.ingestion.repository.ComplaintMasterRepository;
import com.rbi.cms.ingestion.repository.OutboxEventRepository;
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
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class IngestionServiceIntegrationTest {

    @Container
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
            .withDatabaseName("CMSTEST")
            .withUsername("cms_test")
            .withPassword("cms_test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracle::getJdbcUrl);
        registry.add("spring.datasource.username", oracle::getUsername);
        registry.add("spring.datasource.password", oracle::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ComplaintMasterRepository complaintRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @BeforeEach
    void setup() {
        outboxRepository.deleteAll();
        complaintRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /complaints - registers complaint and creates outbox event")
    void shouldRegisterComplaintAndCreateOutboxEvent() throws Exception {
        ComplaintRegistrationRequest request = ComplaintRegistrationRequest.builder()
                .channel("WEB_PORTAL")
                .category("ATM")
                .complainantName("Rajesh Kumar")
                .complainantEmail("rajesh@example.com")
                .complainantPhone("9876543210")
                .entityName("State Bank of India")
                .entityType("BANK")
                .subject("ATM failed transaction - amount debited but cash not dispensed")
                .description("On 20th May 2026, I attempted to withdraw Rs 10,000 from SBI ATM at MG Road branch. "
                        + "Transaction failed but amount was debited from my account. ATM slip shows transaction declined.")
                .amountInvolved(10000.0)
                .transactionDate(LocalDate.of(2026, 5, 20))
                .jurisdictionCode("KAR")
                .build();

        mockMvc.perform(post("/api/v1/complaints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.complaintId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.slaDueDate").isNotEmpty())
                .andExpect(jsonPath("$.data.acknowledgement").isNotEmpty());

        assertThat(complaintRepository.count()).isEqualTo(1);

        var outboxEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getTopic()).isEqualTo("complaint.ingested");
        assertThat(outboxEvents.get(0).getAggregateType()).isEqualTo("COMPLAINT");
    }

    @Test
    @DisplayName("POST /complaints - returns 400 for missing required fields")
    void shouldReturn400ForMissingFields() throws Exception {
        ComplaintRegistrationRequest request = ComplaintRegistrationRequest.builder()
                .channel("WEB_PORTAL")
                .build();

        mockMvc.perform(post("/api/v1/complaints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /complaints - returns 400 for invalid channel")
    void shouldReturn400ForInvalidChannel() throws Exception {
        ComplaintRegistrationRequest request = ComplaintRegistrationRequest.builder()
                .channel("INVALID_CHANNEL")
                .category("ATM")
                .complainantName("Test User")
                .complainantEmail("test@example.com")
                .entityName("Test Bank")
                .subject("Test subject")
                .description("Test description")
                .build();

        mockMvc.perform(post("/api/v1/complaints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /complaints/{id} - returns complaint details")
    void shouldReturnComplaintDetails() throws Exception {
        ComplaintRegistrationRequest request = ComplaintRegistrationRequest.builder()
                .channel("WEB_PORTAL")
                .category("UPI")
                .complainantName("Priya Sharma")
                .complainantEmail("priya@example.com")
                .complainantPhone("9988776655")
                .entityName("HDFC Bank")
                .entityType("BANK")
                .subject("UPI transaction failed")
                .description("Amount debited but not received by beneficiary")
                .amountInvolved(5000.0)
                .transactionDate(LocalDate.of(2026, 5, 18))
                .build();

        String responseJson = mockMvc.perform(post("/api/v1/complaints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String complaintId = objectMapper.readTree(responseJson).path("data").path("complaintId").asText();

        mockMvc.perform(get("/api/v1/complaints/" + complaintId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complaintId").value(complaintId))
                .andExpect(jsonPath("$.data.category").value("UPI"))
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.complainantName").value("Priya Sharma"));
    }

    @Test
    @DisplayName("GET /complaints/{id} - returns 404 for non-existent complaint")
    void shouldReturn404ForNonExistentComplaint() throws Exception {
        mockMvc.perform(get("/api/v1/complaints/CMP-NONEXIST-000001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
