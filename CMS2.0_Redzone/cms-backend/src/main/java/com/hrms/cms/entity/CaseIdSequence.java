package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Mirrors ComplaintNumberSequence as a separate counter: Case IDs (non-maintainable complaints) and
 * Complaint Numbers are distinct identifier spaces and must not share a sequence.
 */
@Entity
@Table(name = "CASE_ID_SEQUENCE", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"officeCode", "financialYear"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CaseIdSequence {

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
