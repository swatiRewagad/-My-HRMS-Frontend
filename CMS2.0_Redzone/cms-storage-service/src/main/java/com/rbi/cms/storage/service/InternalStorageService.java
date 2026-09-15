package com.rbi.cms.storage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
public class InternalStorageService {

    @Value("${cms.storage.root-path:/data/cms/storage}")
    private String rootPath;

    public String store(String bucket, String fileName, byte[] content) throws IOException {
        Path bucketPath = Paths.get(rootPath, bucket);
        Files.createDirectories(bucketPath);

        String uniqueName = UUID.randomUUID() + "_" + fileName;
        Path filePath = bucketPath.resolve(uniqueName);
        Files.write(filePath, content);

        log.info("Stored file: {} in bucket: {} ({} bytes)", uniqueName, bucket, content.length);
        return filePath.toAbsolutePath().toString();
    }

    public String store(String bucket, MultipartFile file) throws IOException {
        return store(bucket, file.getOriginalFilename(), file.getBytes());
    }

    public byte[] retrieve(String storagePath) throws IOException {
        Path path = Paths.get(storagePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + storagePath);
        }
        return Files.readAllBytes(path);
    }

    public void delete(String storagePath) throws IOException {
        Files.deleteIfExists(Paths.get(storagePath));
        log.info("Deleted file: {}", storagePath);
    }

    public String computeChecksum(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
