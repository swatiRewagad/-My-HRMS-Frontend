package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CONFIG_AUDIT_LOG", indexes = {
    @Index(name = "idx_config_audit_key", columnList = "configKey"),
    @Index(name = "idx_config_audit_date", columnList = "changedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfigAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String configKey;

    @Column(length = 500)
    private String oldValue;

    @Column(length = 500)
    private String newValue;

    @Column(nullable = false, length = 200)
    private String changedBy;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        this.changedAt = LocalDateTime.now();
    }
}
