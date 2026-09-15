package com.rbi.cms.workflow.service;

import com.rbi.cms.workflow.dto.OfficerTaskResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Profile("dev-local")
public class DevLocalTaskQueryService implements TaskQueryService {

    private final ConcurrentHashMap<String, OfficerTaskResponse> taskStore = new ConcurrentHashMap<>();

    public void registerTask(String complaintId, String category, String priority, String assignedTeam) {
        OfficerTaskResponse task = OfficerTaskResponse.builder()
                .complaintId(complaintId)
                .category(category)
                .priority(priority)
                .status("ASSIGNED")
                .subject("Complaint regarding " + category + " services")
                .complainantName("Customer")
                .entityName("Sample Bank")
                .assignedTeam(assignedTeam)
                .amountInvolved(0.0)
                .createdAt(Instant.now())
                .slaDueDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .slaPercentage(0)
                .build();
        taskStore.put(complaintId, task);
        log.info("[DEV-LOCAL TASKS] Registered task for complaint: {} → team: {}", complaintId, assignedTeam);
    }

    public void updateTaskStatus(String complaintId, String status) {
        OfficerTaskResponse task = taskStore.get(complaintId);
        if (task != null) {
            task.setStatus(status);
            log.info("[DEV-LOCAL TASKS] Updated complaint {} status to {}", complaintId, status);
        }
    }

    @Override
    public List<OfficerTaskResponse> getTasksForTeam(String team, String status) {
        List<OfficerTaskResponse> result = new ArrayList<>();
        for (OfficerTaskResponse task : taskStore.values()) {
            boolean teamMatch = team == null || team.equals(task.getAssignedTeam());
            boolean statusMatch = status == null || status.isEmpty() || status.equals(task.getStatus());
            if (teamMatch && statusMatch) {
                updateSlaPercentage(task);
                result.add(task);
            }
        }
        log.info("[DEV-LOCAL TASKS] Query: team={}, status={} → found {} tasks", team, status, result.size());
        return result;
    }

    private void updateSlaPercentage(OfficerTaskResponse task) {
        if (task.getCreatedAt() != null && task.getSlaDueDate() != null) {
            long totalDuration = ChronoUnit.HOURS.between(task.getCreatedAt(), task.getSlaDueDate());
            long elapsed = ChronoUnit.HOURS.between(task.getCreatedAt(), Instant.now());
            if (totalDuration > 0) {
                task.setSlaPercentage((int) ((elapsed * 100) / totalDuration));
            }
        }
    }
}
