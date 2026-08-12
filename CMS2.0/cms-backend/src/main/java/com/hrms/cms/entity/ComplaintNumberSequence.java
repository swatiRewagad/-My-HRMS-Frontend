package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_NUMBER_SEQUENCE", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"officeCode", "financialYear"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintNumberSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 10)
    private String officeCode;

    @Column(nullable = false, length = 6)
    private String financialYear;

    @Column(nullable = false)
    @Builder.Default
    private Integer lastSequence = 0;

    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
