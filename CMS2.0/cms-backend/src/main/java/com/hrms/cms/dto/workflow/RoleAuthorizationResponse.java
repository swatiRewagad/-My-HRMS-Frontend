package com.hrms.cms.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleAuthorizationResponse {

    private String userRole;
    private String action;
    private boolean authorized;
}
