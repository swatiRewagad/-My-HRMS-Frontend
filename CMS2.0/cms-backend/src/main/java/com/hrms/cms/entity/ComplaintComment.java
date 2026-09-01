package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_COMMENTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String complaintNumber;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(length = 10)
    private String initials;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(length = 50)
    private String role;

    @Column(length = 10)
    private String color;

    // Set only for Nodal Officer Record comments (scopes them to a specific NO record,
    // separate from the complaint's own general assessment comment thread).
    @Column(length = 50)
    private String noRecordNumber;

    // "NO" or "PNO" — which recipient this Nodal Officer Record comment targets.
    @Column(length = 10)
    private String target;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (color == null) color = "#6366f1";
    }
}
