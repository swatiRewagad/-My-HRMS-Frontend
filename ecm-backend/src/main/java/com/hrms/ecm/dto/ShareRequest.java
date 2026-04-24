package com.hrms.ecm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShareRequest {
    @NotNull
    private Long fileId;
    @NotBlank
    private String shareType;
    private Long sharedWith;
    private String permission;
    private Integer expiresInHours;
}
