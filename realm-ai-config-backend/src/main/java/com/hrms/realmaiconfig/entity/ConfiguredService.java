package com.hrms.realmaiconfig.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "CONFIGURED_SERVICES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfiguredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CONFIGURATION_ID", nullable = false)
    private Long configurationId;

    @Column(name = "SERVICE_ID", nullable = false, length = 100)
    private String serviceId;

    @Column(name = "SERVICE_LABEL", length = 200)
    private String serviceLabel;

    @Column(name = "SERVICE_GROUP", length = 20)
    private String serviceGroup;

    @Column(name = "SCHEMA_NAME", length = 200)
    private String schemaName;

    @Column(name = "DB_TYPE", length = 50)
    private String dbType;

    @Column(name = "CONNECTION_STRING", length = 500)
    private String connectionString;

    @Column(name = "SECRET_PATH", length = 500)
    private String secretPath;
}
