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

    /**
     * Translation key for the citizen-facing label, e.g. {@code category.atm_debit_card}.
     *
     * <p>{@link #name} stays ENGLISH and is the value submitted on a complaint and matched by routing,
     * so it cannot be localised without changing what is stored. This column localises only what is
     * DISPLAYED. Null means the caller falls back to {@code name}, so a category added by an operator
     * who has no key for it still renders rather than showing a raw key.
     */
    @Column(name = "LABEL_KEY", length = 100)
    private String labelKey;

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
