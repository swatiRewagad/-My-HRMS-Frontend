package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "REGULATED_ENTITIES", indexes = {
    @Index(name = "idx_re_department", columnList = "department"),
    @Index(name = "idx_re_name", columnList = "name"),
    @Index(name = "idx_re_entity_type", columnList = "entityType")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegulatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(length = 500)
    private String nameNormalized;

    @Column(nullable = false, length = 10)
    private String department;

    @Column(length = 100)
    private String entityType;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 20)
    private String status;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "active";
        if (this.nameNormalized == null && this.name != null) {
            this.nameNormalized = normalize(this.name);
        }
    }

    public static String normalize(String name) {
        if (name == null) return "";
        return name.toUpperCase()
                .replaceAll("[^A-Z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
