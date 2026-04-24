package com.hrms.ecm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class FileStorageService {

    @Value("${ecm.storage.root-path}")
    private String rootPath;

    public String store(MultipartFile file, String folderPath) throws IOException {
        String uniqueName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path dir = Paths.get(rootPath, folderPath);
        Files.createDirectories(dir);
        Path target = dir.resolve(uniqueName);
        file.transferTo(target.toFile());
        return target.toString();
    }

    public byte[] load(String storagePath) throws IOException {
        return Files.readAllBytes(Paths.get(storagePath));
    }

    public void delete(String storagePath) throws IOException {
        Files.deleteIfExists(Paths.get(storagePath));
    }

    public void storeChunk(String uploadId, int chunkIndex, MultipartFile chunk) throws IOException {
        Path chunkDir = Paths.get(rootPath, "chunks", uploadId);
        Files.createDirectories(chunkDir);
        Path target = chunkDir.resolve(String.valueOf(chunkIndex));
        chunk.transferTo(target.toFile());
    }

    public String mergeChunks(String uploadId, int totalChunks, String folderPath, String originalName) throws IOException {
        Path chunkDir = Paths.get(rootPath, "chunks", uploadId);
        String uniqueName = UUID.randomUUID() + "_" + originalName;
        Path dir = Paths.get(rootPath, folderPath);
        Files.createDirectories(dir);
        Path target = dir.resolve(uniqueName);

        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            for (int i = 0; i < totalChunks; i++) {
                Path chunkFile = chunkDir.resolve(String.valueOf(i));
                out.write(Files.readAllBytes(chunkFile));
            }
        }

        deleteDirectory(chunkDir);
        return target.toString();
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }
}
