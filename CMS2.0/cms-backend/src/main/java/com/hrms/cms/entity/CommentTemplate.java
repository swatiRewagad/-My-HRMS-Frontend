package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMMENT_TEMPLATES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 30)
    private String modeOfReceipt; // EMAIL, PHYSICAL_LETTER, PORTAL, ALL

    @Column(length = 50)
    private String category; // GENERAL, CLOSURE, REJECTION, FOLLOWUP

    private boolean active;

    @Column(length = 100)
    private String createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
