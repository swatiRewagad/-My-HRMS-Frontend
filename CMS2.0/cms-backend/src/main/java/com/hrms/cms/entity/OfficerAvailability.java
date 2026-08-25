package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "OFFICER_AVAILABILITY", indexes = {
    @Index(name = "idx_officer_user_id", columnList = "userId"),
    @Index(name = "idx_officer_role", columnList = "role")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OfficerAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean onLeave;

    private LocalDate leaveStartDate;

    private LocalDate leaveEndDate;

    @Column(length = 200)
    private String leaveReason;

    private int currentWorkload;

    private int maxWorkload;

    @Column(length = 50)
    private String officeCode;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (maxWorkload == 0) maxWorkload = 20;
        active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isAvailable() {
        if (!active) return false;
        if (onLeave) {
            LocalDate today = LocalDate.now();
            if (leaveStartDate != null && leaveEndDate != null) {
                return today.isBefore(leaveStartDate) || today.isAfter(leaveEndDate);
            }
            return false;
        }
        return currentWorkload < maxWorkload;
    }
}
