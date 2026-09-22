package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_CATEGORIES", indexes = {
    @Index(name = "idx_category_parent", columnList = "PARENT_ID"),
    @Index(name = "idx_category_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "PARENT_ID")
    private Long parentId;

    @Column(length = 20)
    private String status;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "active";
        if (this.sortOrder == null) this.sortOrder = 0;
    }
}
