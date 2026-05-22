package com.hrms.ecm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "INVOICE_FIELD_CONFIGS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceFieldConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "DOC_TYPE_CONFIG_ID", nullable = false)
    private Long docTypeConfigId;

    @Column(nullable = false, length = 100)
    private String fieldName;

    @Column(nullable = false, length = 100)
    private String fieldKey;

    @Column(nullable = false, length = 50)
    private String fieldType;

    @Column(nullable = false)
    @Builder.Default
    private Boolean required = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String fieldCategory = "header";

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }
}
