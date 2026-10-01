package com.rbi.cms.workflow.service;

import com.rbi.cms.workflow.dto.OfficerTaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.kogito.Model;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.WorkItem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Kogito-based task query service.
 * Queries active human tasks from Kogito process instances.
 */
@Slf4j
@Service
@Profile("!dev-local")
@RequiredArgsConstructor
public class KogitoTaskQueryService implements TaskQueryService {

    @Qualifier("complaint_lifecycle")
    private final Process<? extends Model> complaintProcess;

    @Override
    public List<OfficerTaskResponse> getTasksForTeam(String team, String status) {
        List<OfficerTaskResponse> responses = new ArrayList<>();

        complaintProcess.instances().stream()
                .filter(instance -> instance.status() == ProcessInstance.STATE_ACTIVE)
                .forEach(instance -> {
                    List<WorkItem> workItems = instance.workItems();
                    for (WorkItem workItem : workItems) {
                        Map<String, Object> params = workItem.getParameters();
                        String complaintId = (String) params.getOrDefault("complaintId", "");
                        String category = (String) params.getOrDefault("category", "GENERAL");
                        String priority = (String) params.getOrDefault("priority", "MEDIUM");

                        boolean teamMatch = team == null || workItem.getName().toUpperCase().contains(team.replace("_TEAM", ""));
                        if (!teamMatch) continue;

                        OfficerTaskResponse response = OfficerTaskResponse.builder()
                                .complaintId(complaintId)
                                .category(category)
                                .priority(priority)
                                .status(workItem.getPhaseStatus())
                                .subject(workItem.getName())
                                .assignedTeam(team)
                                .createdAt(Instant.now())
                                .slaDueDate(Instant.now().plus(30, ChronoUnit.DAYS))
                                .slaPercentage(0)
                                .build();

                        responses.add(response);
                    }
                });

        log.info("[KOGITO-TASKS] Query for team={}, status={} returned {} tasks", team, status, responses.size());
        return responses;
    }
}
