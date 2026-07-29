package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ROUND_ROBIN_POINTERS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoundRobinPointer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String poolKey; // DEO, REVIEWER, RBIO_OFFICER, CEPC_DO, etc.

    private int currentIndex;

    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
