package com.hrms.ecm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ECM_ACTIVITY_LOG")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 20)
    private String entityType;

    @Column(length = 500)
    private String entityName;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(length = 200)
    private String userName;

    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() { this.performedAt = LocalDateTime.now(); }
}
