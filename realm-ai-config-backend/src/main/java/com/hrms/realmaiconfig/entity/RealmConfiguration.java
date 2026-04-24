package com.hrms.realmaiconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "REALM_CONFIGURATIONS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RealmConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REALM_ID", nullable = false, length = 100)
    private String realmId;

    @Column(name = "MODE", nullable = false, length = 50)
    private String mode;

    @Column(name = "PLATFORM_VERSION", nullable = false, length = 50)
    private String platformVersion;

    @Column(name = "DEPLOYMENT_TYPE", nullable = false, length = 50)
    private String deploymentType;

    @Column(name = "DEPLOY_SCHEMA_NAME", length = 200)
    private String deploySchemaName;

    @Column(name = "DEPLOY_DB_TYPE", length = 50)
    private String deployDbType;

    @Column(name = "DEPLOY_CONNECTION_STRING", length = 500)
    private String deployConnectionString;

    @Column(name = "DEPLOY_SECRET_PATH", length = 500)
    private String deploySecretPath;

    @Column(name = "CONFIGURED_BY", length = 200)
    private String configuredBy;

    @Column(name = "CONFIGURED_AT")
    private LocalDateTime configuredAt;

    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @PrePersist
    protected void onCreate() {
        this.configuredAt = LocalDateTime.now();
        this.isActive = true;
    }
}
