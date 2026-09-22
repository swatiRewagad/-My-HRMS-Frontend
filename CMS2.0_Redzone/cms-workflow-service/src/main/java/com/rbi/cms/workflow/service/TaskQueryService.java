package com.rbi.cms.workflow.service;

import com.rbi.cms.workflow.dto.OfficerTaskResponse;

import java.util.List;

public interface TaskQueryService {
    List<OfficerTaskResponse> getTasksForTeam(String team, String status);
}
