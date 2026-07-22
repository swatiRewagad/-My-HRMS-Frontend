package com.rbi.cms.workflow.service;

import com.rbi.cms.workflow.dto.OfficerTaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Profile("!dev-local")
@RequiredArgsConstructor
public class JbpmTaskQueryService implements TaskQueryService {

    private final RuntimeManager runtimeManager;

    @Override
    public List<OfficerTaskResponse> getTasksForTeam(String team, String status) {
        RuntimeEngine engine = runtimeManager.getRuntimeEngine(null);
        TaskService taskService = engine.getTaskService();

        List<TaskSummary> tasks = taskService.getTasksAssignedAsPotentialOwner(team, "en-UK");

        List<OfficerTaskResponse> responses = new ArrayList<>();
        for (TaskSummary summary : tasks) {
            if (status != null && !status.isEmpty()) {
                if (!status.equalsIgnoreCase(summary.getStatusId())) continue;
            }

            Task task = taskService.getTaskById(summary.getId());
            Map<String, Object> content = taskService.getTaskContent(task.getId());

            String complaintId = (String) content.getOrDefault("complaintId", "");
            String category = (String) content.getOrDefault("category", "GENERAL");
            String priority = (String) content.getOrDefault("priority", "MEDIUM");

            OfficerTaskResponse response = OfficerTaskResponse.builder()
                    .complaintId(complaintId)
                    .category(category)
                    .priority(priority)
                    .status(summary.getStatusId())
                    .subject(summary.getName())
                    .assignedTeam(team)
                    .assignedTo(summary.getActualOwnerId())
                    .createdAt(summary.getCreatedOn() != null ? summary.getCreatedOn().toInstant() : Instant.now())
                    .slaDueDate(Instant.now().plus(30, ChronoUnit.DAYS))
                    .slaPercentage(calculateSlaPercentage(summary.getCreatedOn() != null ? summary.getCreatedOn().toInstant() : Instant.now()))
                    .build();

            responses.add(response);
        }

        log.info("[JBPM-TASKS] Query for team={}, status={} returned {} tasks", team, status, responses.size());
        return responses;
    }

    private int calculateSlaPercentage(Instant createdAt) {
        long totalHours = 30L * 24;
        long elapsedHours = ChronoUnit.HOURS.between(createdAt, Instant.now());
        if (totalHours <= 0) return 0;
        return (int) Math.min(100, (elapsedHours * 100) / totalHours);
    }
}
