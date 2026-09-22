package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "DRAFT_ID_SEQUENCE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DraftIdSequence {

    @Id
    private Integer id;

    @Column(nullable = false)
    @Builder.Default
    private Integer lastSequence = 0;

    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
