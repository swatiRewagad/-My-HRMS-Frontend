package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "FORM_CONFIGS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FormConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String formKey;

    @Column(length = 200)
    private String formName;

    @Column(columnDefinition = "JSON")
    private String schemaJson;

    @Column(nullable = false)
    private Boolean active;

    @Column(length = 50)
    private String version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.active == null) this.active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
