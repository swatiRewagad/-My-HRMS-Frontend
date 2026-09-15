package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "email_ignore_list")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailIgnoreEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_pattern", nullable = false, length = 300)
    private String emailPattern;

    @Column(name = "pattern_type", nullable = false, length = 20)
    @Builder.Default
    private String patternType = "EXACT";

    @Column(length = 500)
    private String reason;

    @Column(name = "added_by", length = 100)
    @Builder.Default
    private String addedBy = "admin";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
