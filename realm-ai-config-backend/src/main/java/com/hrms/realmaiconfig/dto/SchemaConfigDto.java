package com.hrms.realmaiconfig.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SchemaConfigDto {
    private String schemaName;
    private String dbType;
    private String connectionString;
    private String secretPath;
}
