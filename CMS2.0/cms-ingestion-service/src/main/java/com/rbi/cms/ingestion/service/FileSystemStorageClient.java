package com.rbi.cms.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class FileSystemStorageClient implements StorageClient {

    @Value("${cms.storage.base-path:/data/cms/attachments}")
    private String basePath;

    @Override
    public String store(String complaintId, MultipartFile file) {
        try {
            Path directory = Paths.get(basePath, complaintId);
            Files.createDirectories(directory);

            String uniqueName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = directory.resolve(uniqueName);
            Files.write(filePath, file.getBytes());

            log.info("Stored attachment: {} for complaint: {}", uniqueName, complaintId);
            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store attachment for complaint: " + complaintId, e);
        }
    }

    @Override
    public byte[] retrieve(String storagePath) {
        try {
            return Files.readAllBytes(Paths.get(storagePath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve file: " + storagePath, e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(Paths.get(storagePath));
            log.info("Deleted attachment: {}", storagePath);
        } catch (IOException e) {
            log.error("Failed to delete attachment: {}", storagePath, e);
        }
    }
}
