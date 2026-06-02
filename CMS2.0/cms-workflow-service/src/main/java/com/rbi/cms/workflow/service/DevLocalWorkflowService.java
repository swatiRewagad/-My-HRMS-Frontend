package com.rbi.cms.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.event.ComplaintEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Profile("dev-local")
@Primary
@RequiredArgsConstructor
public class DevLocalWorkflowService implements ComplaintWorkflowProcessor {

    private final ConcurrentHashMap<String, String> activeProcesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ComplaintStatus> complaintStatuses = new ConcurrentHashMap<>();
    private final DevLocalTaskQueryService taskQueryService;
    private final ObjectMapper objectMapper;

    public String startComplaintWorkflow(ComplaintEvent event) {
        String processId = UUID.randomUUID().toString();
        activeProcesses.put(event.getComplaintId(), processId);
        complaintStatuses.put(event.getComplaintId(), ComplaintStatus.ASSIGNED);

        String category = "GENERAL";
        String priority = "MEDIUM";
        String assignedTeam = "GENERAL_TEAM";
        try {
            if (event.getPayload() != null && event.getPayload().startsWith("{")) {
                Map<String, String> payload = objectMapper.readValue(event.getPayload(), Map.class);
                category = payload.getOrDefault("category", "GENERAL");
                priority = payload.getOrDefault("priority", "MEDIUM");
            }
        } catch (Exception e) {
            log.warn("Failed to parse payload: {}", e.getMessage());
        }

        switch (category) {
            case "ATM" -> assignedTeam = "ATM_TEAM";
            case "UPI" -> assignedTeam = "DIGITAL_TEAM";
            case "NEFT_RTGS" -> assignedTeam = "PAYMENT_SYSTEMS_TEAM";
            case "LOAN" -> assignedTeam = "LENDING_TEAM";
            case "CREDIT_CARD" -> assignedTeam = "CARDS_TEAM";
        }

        taskQueryService.registerTask(event.getComplaintId(), category, priority, assignedTeam);

        // Update DB via ingestion service using POST (PATCH not supported by default HttpURLConnection on Windows)
        try {
            RestTemplate restTemplate = new RestTemplate();
            String assignUrl = String.format(
                    "http://localhost:8082/cms-ingestion/api/v1/complaints/%s/assignment?team=%s",
                    event.getComplaintId(), assignedTeam);
            restTemplate.exchange(assignUrl, HttpMethod.POST, HttpEntity.EMPTY, Void.class);
        } catch (Exception e) {
            log.warn("[DEV-LOCAL WORKFLOW] Could not update assignment in DB: {}", e.getMessage());
        }

        log.info("[DEV-LOCAL WORKFLOW] Started process {} for complaint: {} → team: {} (status: ASSIGNED)",
                processId, event.getComplaintId(), assignedTeam);
        return processId;
    }

    public void transitionState(String complaintId, ComplaintStatus targetStatus, String remarks) {
        complaintStatuses.put(complaintId, targetStatus);
        taskQueryService.updateTaskStatus(complaintId, targetStatus.name());

        // Update the DB via ingestion service using POST (PATCH not supported on Windows default HTTP client)
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = String.format(
                    "http://localhost:8082/cms-ingestion/api/v1/complaints/%s/status?status=%s&remarks=%s&performedBy=OFFICER",
                    complaintId, targetStatus.name(), remarks != null ? java.net.URLEncoder.encode(remarks, "UTF-8") : "");
            restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, Void.class);
            log.info("[DEV-LOCAL WORKFLOW] DB updated successfully for {}", complaintId);
        } catch (Exception e) {
            log.warn("[DEV-LOCAL WORKFLOW] Could not update ingestion service: {}", e.getMessage());
        }

        log.info("[DEV-LOCAL WORKFLOW] Transitioning complaint {} to status: {} (remarks: {})",
                complaintId, targetStatus, remarks);
    }

    public void escalateComplaint(String complaintId, String reason) {
        complaintStatuses.put(complaintId, ComplaintStatus.ESCALATED);
        taskQueryService.updateTaskStatus(complaintId, "ESCALATED");

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = String.format(
                    "http://localhost:8082/cms-ingestion/api/v1/complaints/%s/status?status=ESCALATED&remarks=%s&performedBy=OFFICER",
                    complaintId, reason != null ? java.net.URLEncoder.encode(reason, "UTF-8") : "");
            restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, Void.class);
        } catch (Exception e) {
            log.warn("[DEV-LOCAL WORKFLOW] Could not update ingestion service: {}", e.getMessage());
        }

        log.info("[DEV-LOCAL WORKFLOW] Escalating complaint: {} - reason: {}", complaintId, reason);
    }
}
