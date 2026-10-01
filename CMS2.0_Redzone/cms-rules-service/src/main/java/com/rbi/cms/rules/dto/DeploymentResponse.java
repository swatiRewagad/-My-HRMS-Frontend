package com.rbi.cms.rules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentResponse {

    private String deploymentId;
    private int rulesDeployed;
    private String status;
    private Instant deployedAt;
    private String error;
}
