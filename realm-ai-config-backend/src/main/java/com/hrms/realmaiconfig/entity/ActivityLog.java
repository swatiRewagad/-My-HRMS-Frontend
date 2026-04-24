package com.hrms.realmaiconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ACTIVITY_LOG")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ACTION", nullable = false, length = 100)
    private String action;

    @Column(name = "ENTITY_TYPE", nullable = false, length = 20)
    private String entityType;

    @Column(name = "ENTITY_NAME", length = 300)
    private String entityName;

    @Column(name = "PERFORMED_BY", length = 200)
    private String performedBy;

    @Column(name = "PERFORMED_AT")
    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        this.performedAt = LocalDateTime.now();
    }
}
