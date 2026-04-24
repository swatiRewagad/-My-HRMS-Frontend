package com.hrms.ecm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "FOLDER_ACCESS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FolderAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "FOLDER_ID", nullable = false)
    private Long folderId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String permission;

    private LocalDateTime grantedAt;

    @PrePersist
    protected void onCreate() { this.grantedAt = LocalDateTime.now(); }
}
