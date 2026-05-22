package com.hrms.realmaiconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ECM_EXTRACTED_FIELDS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EcmExtractedField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ECM_FILE_ID", nullable = false)
    private Long ecmFileId;

    @Column(name = "FIELD_NAME", nullable = false, length = 200)
    private String fieldName;

    @Column(name = "FIELD_VALUE", length = 2000)
    private String fieldValue;

    @Column(name = "CONFIDENCE")
    private Double confidence;

    @Column(name = "OCR_ENGINE", length = 100)
    private String ocrEngine;

    @Column(name = "EXTRACTED_AT")
    private LocalDateTime extractedAt;

    @PrePersist
    protected void onCreate() {
        this.extractedAt = LocalDateTime.now();
    }
}
