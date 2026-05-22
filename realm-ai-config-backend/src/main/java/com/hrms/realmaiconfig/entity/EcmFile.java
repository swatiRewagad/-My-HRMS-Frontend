package com.hrms.realmaiconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ECM_FILES")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EcmFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REALM_ID", nullable = false, length = 100)
    private String realmId;

    @Column(name = "FILE_NAME", nullable = false, length = 500)
    private String fileName;

    @Column(name = "FILE_PATH", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "FOLDER_PATH", nullable = false, length = 1000)
    private String folderPath;

    @Column(name = "FILE_SIZE")
    private Long fileSize;

    @Column(name = "FILE_TYPE", length = 50)
    private String fileType;

    @Column(name = "UPLOADED_BY", length = 200)
    private String uploadedBy;

    @Column(name = "UPLOADED_AT")
    private LocalDateTime uploadedAt;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Transient
    private Boolean existsOnDisk;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
        if (this.status == null) this.status = "uploaded";
    }
}
