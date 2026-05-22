package com.hrms.ecm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "INVOICE_EXTRACTIONS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "FILE_ID", nullable = false)
    private Long fileId;

    @Column(length = 200)
    private String invoiceNumber;

    @Column(length = 200)
    private String invoiceDate;

    @Column(length = 200)
    private String dueDate;

    @Column(length = 500)
    private String vendorName;

    @Column(length = 500)
    private String vendorAddress;

    @Column(length = 500)
    private String customerName;

    @Column(length = 500)
    private String customerAddress;

    @Column(length = 100)
    private String subtotal;

    @Column(length = 100)
    private String tax;

    @Column(length = 100)
    private String totalAmount;

    @Column(length = 100)
    private String currency;

    @Column(columnDefinition = "TEXT")
    private String lineItemsJson;

    @Column(columnDefinition = "TEXT")
    private String rawText;

    @Column(length = 50)
    private String status;

    @Column(length = 1000)
    private String errorMessage;

    private LocalDateTime extractedAt;

    @PrePersist
    protected void onCreate() {
        this.extractedAt = LocalDateTime.now();
        if (this.status == null) this.status = "completed";
    }
}
