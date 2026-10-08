package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * The citizen-facing acknowledgement letter body, keyed by department (RBIO/CEPC) and
 * language (EN/HI), so wording changes are a data UPDATE rather than a code deploy.
 *
 * <p>{@code version} exists so an edit can be made as a new active row without losing the
 * row it replaces — {@code active=false} on the old version keeps it around for audit
 * rather than overwriting it in place.
 */
@Entity
@Table(name = "ACKNOWLEDGEMENT_LETTER_TEMPLATES", indexes = {
    @Index(name = "idx_ack_tpl_dept_lang_active", columnList = "department,language,active")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AcknowledgementLetterTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String department; // RBIO, CEPC

    @Column(nullable = false, length = 5)
    private String language; // EN, HI

    @Column(columnDefinition = "TEXT", nullable = false)
    private String bodyTemplate; // {{placeholder}} syntax

    @Builder.Default
    private Integer version = 1;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
