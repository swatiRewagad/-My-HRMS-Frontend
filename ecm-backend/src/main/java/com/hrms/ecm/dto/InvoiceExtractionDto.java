package com.hrms.ecm.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceExtractionDto {
    private Long id;
    private Long fileId;
    private String fileName;
    private String invoiceNumber;
    private String invoiceDate;
    private String dueDate;
    private String vendorName;
    private String vendorAddress;
    private String customerName;
    private String customerAddress;
    private String subtotal;
    private String tax;
    private String totalAmount;
    private String currency;
    private List<LineItem> lineItems;
    private List<ConfiguredField> configuredFields;
    private String status;
    private String errorMessage;
    private LocalDateTime extractedAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LineItem {
        private int slNo;
        private String description;
        private String quantity;
        private String unitPrice;
        private String amount;
        private String hsnCode;
        private String taxRate;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConfiguredField {
        private String fieldName;
        private String fieldKey;
        private String fieldType;
        private Boolean required;
        private String fieldCategory;
        private Integer displayOrder;
    }
}
