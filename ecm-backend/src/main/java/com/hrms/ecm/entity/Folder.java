package com.hrms.ecm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "FOLDERS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(nullable = false, length = 20)
    private String visibility;

    @Column(length = 1000)
    private String description;

    @Column(name = "PARENT_ID")
    private Long parentId;

    @Column(name = "OWNER_ID", nullable = false)
    private Long ownerId;

    @Column(length = 500)
    private String path;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
