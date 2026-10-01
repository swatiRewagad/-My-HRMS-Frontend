package com.hrms.cms.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "cms.attachments")
@Getter
@Setter
public class FileStorageConfig {

    private String rootPath = "/data/cms-attachments";

    /**
     * NFR-006: 2MB per file.
     *
     * <p>No enforcement path reads this any more, and none should. It is the fallback the yml binds
     * when SYSTEM_CONFIG has no row, and it must equal the yml default; the live value comes from
     * {@link com.hrms.cms.service.UploadLimitsService} so an administrator can change the limit
     * without a release. Reading it directly is how {@code /api/files/upload} came to ignore the
     * configured limit while the browser was being shown it.
     */
    private long maxFileSize = 2097152;  // 2MB

    /** NFR-006: 25MB across all files on one record. Fallback only — see maxFileSize. */
    private long maxTotalSize = 26214400; // 25MB

    private long chunkSize = 5242880;    // 5MB
    private String allowedTypes = "pdf,png,jpg,jpeg,doc,docx,xls,xlsx,txt,csv,zip,mp4,webm,ogg,mov,mp3,wav,aac";

    /** NFR-006: 10 files per record. */
    private int maxFilesPerComplaint = 10;
    private String tempDir = "temp-chunks";

    @PostConstruct
    public void init() throws IOException {
        Path root = Paths.get(rootPath);
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }
        Path temp = root.resolve(tempDir);
        if (!Files.exists(temp)) {
            Files.createDirectories(temp);
        }
    }

    public Path getComplaintDir(String complaintNumber) {
        return Paths.get(rootPath, complaintNumber);
    }

    public Path getTempChunkDir() {
        return Paths.get(rootPath, tempDir);
    }

    public boolean isAllowedType(String fileName) {
        if (fileName == null || !fileName.contains(".")) return false;
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        Set<String> allowed = Set.of(allowedTypes.split(","));
        return allowed.contains(ext);
    }
}
