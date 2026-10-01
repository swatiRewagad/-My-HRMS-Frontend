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

    /**
     * Which header the pattern is matched against. FROM preserves the historical sender-only
     * behaviour, so existing rows keep working without a data migration.
     */
    @Column(name = "match_field", nullable = false, length = 20)
    @Builder.Default
    private String matchField = "FROM";

    @Column(name = "to_pattern", length = 300)
    private String toPattern;

    @Column(name = "cc_pattern", length = 300)
    private String ccPattern;

    @Column(name = "bcc_pattern", length = 300)
    private String bccPattern;

    @Column(name = "subject_pattern", length = 500)
    private String subjectPattern;

    /**
     * A counter-rule: when this matches, the ignore rule is overridden and the draft IS created.
     * Lets an admin suppress a whole domain while still admitting named senders.
     */
    @Column(name = "exception_pattern", length = 500)
    private String exceptionPattern;

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
