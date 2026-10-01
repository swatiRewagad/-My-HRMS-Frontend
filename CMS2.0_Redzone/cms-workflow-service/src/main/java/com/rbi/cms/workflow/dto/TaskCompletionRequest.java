package com.rbi.cms.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCompletionRequest {
    private String userId;
    private Map<String, Object> taskData;
}
