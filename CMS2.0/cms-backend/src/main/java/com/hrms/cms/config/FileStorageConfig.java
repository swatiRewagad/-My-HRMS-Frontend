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

@Configuration
@ConfigurationProperties(prefix = "cms.attachments")
@Getter
@Setter
public class FileStorageConfig {

    private String rootPath = "/data/cms-attachments";
    private long maxFileSize = 52428800; // 50MB
    private long chunkSize = 5242880;    // 5MB
    private String allowedTypes = "pdf,png,jpg,jpeg,doc,docx,xls,xlsx,txt,csv,zip,mp4,webm,ogg,mov,mp3,wav,aac";
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
        if (fileName == null) return false;
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return allowedTypes.contains(ext);
    }
}
