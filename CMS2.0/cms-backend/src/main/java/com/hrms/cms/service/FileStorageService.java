package com.hrms.cms.service;

import com.hrms.cms.config.FileStorageConfig;
import com.hrms.cms.dto.ChunkUploadResponse;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.repository.ComplaintAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final FileStorageConfig config;
    private final ComplaintAttachmentRepository attachmentRepository;

    public ChunkUploadResponse handleChunkUpload(
            MultipartFile chunk,
            String uploadId,
            int chunkIndex,
            int totalChunks,
            String fileName,
            String complaintNumber,
            Long complaintId,
            long totalFileSize
    ) throws IOException {

        if (!config.isAllowedType(fileName)) {
            return ChunkUploadResponse.builder()
                    .message("File type not allowed. Allowed: " + config.getAllowedTypes())
                    .complete(false)
                    .build();
        }

        if (totalFileSize > config.getMaxFileSize()) {
            return ChunkUploadResponse.builder()
                    .message("File exceeds maximum size of " + (config.getMaxFileSize() / 1048576) + "MB")
                    .complete(false)
                    .build();
        }

        String actualUploadId = uploadId != null ? uploadId : UUID.randomUUID().toString();

        Path chunkDir = config.getTempChunkDir().resolve(actualUploadId);
        Files.createDirectories(chunkDir);

        Path chunkFile = chunkDir.resolve("chunk_" + String.format("%05d", chunkIndex));
        try (InputStream in = chunk.getInputStream()) {
            Files.copy(in, chunkFile, StandardCopyOption.REPLACE_EXISTING);
        }

        long receivedChunks = Files.list(chunkDir)
                .filter(p -> p.getFileName().toString().startsWith("chunk_"))
                .count();

        if (receivedChunks < totalChunks) {
            return ChunkUploadResponse.builder()
                    .uploadId(actualUploadId)
                    .chunkIndex(chunkIndex)
                    .totalChunks(totalChunks)
                    .complete(false)
                    .message("Chunk " + (chunkIndex + 1) + "/" + totalChunks + " received")
                    .build();
        }

        // All chunks received — assemble
        Path complaintDir = config.getComplaintDir(complaintNumber);
        Files.createDirectories(complaintDir);

        String storedName = UUID.randomUUID() + "_" + sanitizeFileName(fileName);
        Path finalPath = complaintDir.resolve(storedName);

        assembleChunks(chunkDir, finalPath, totalChunks);

        // Cleanup temp chunks
        cleanupChunkDir(chunkDir);

        String checksum = computeChecksum(finalPath);

        ComplaintAttachment attachment = ComplaintAttachment.builder()
                .complaintId(complaintId)
                .fileName(storedName)
                .originalName(fileName)
                .contentType(detectContentType(fileName))
                .fileSize(Files.size(finalPath))
                .storagePath(complaintNumber + "/" + storedName)
                .build();

        ComplaintAttachment saved = attachmentRepository.save(attachment);

        log.info("File assembled: {} -> {} (checksum: {})", fileName, finalPath, checksum);

        return ChunkUploadResponse.builder()
                .uploadId(actualUploadId)
                .chunkIndex(chunkIndex)
                .totalChunks(totalChunks)
                .complete(true)
                .attachmentId(saved.getId())
                .fileName(saved.getOriginalName())
                .storagePath(saved.getStoragePath())
                .message("Upload complete")
                .build();
    }

    @Transactional
    public ComplaintAttachment handleSingleUpload(
            MultipartFile file,
            String complaintNumber,
            Long complaintId
    ) throws IOException {

        if (!config.isAllowedType(file.getOriginalFilename())) {
            throw new IllegalArgumentException("File type not allowed");
        }

        if (file.getSize() > config.getMaxFileSize()) {
            throw new IllegalArgumentException("File exceeds max size");
        }

        long existingCount = attachmentRepository.findByComplaintId(complaintId).size();
        if (existingCount >= config.getMaxFilesPerComplaint()) {
            throw new IllegalArgumentException("Max files per complaint reached (" + config.getMaxFilesPerComplaint() + ")");
        }

        Path complaintDir = config.getComplaintDir(complaintNumber);
        Files.createDirectories(complaintDir);

        String storedName = UUID.randomUUID() + "_" + sanitizeFileName(file.getOriginalFilename());
        Path target = complaintDir.resolve(storedName);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        ComplaintAttachment attachment = ComplaintAttachment.builder()
                .complaintId(complaintId)
                .fileName(storedName)
                .originalName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .storagePath(complaintNumber + "/" + storedName)
                .build();

        return attachmentRepository.save(attachment);
    }

    @Transactional(readOnly = true)
    public Path getFilePath(Long attachmentId) {
        ComplaintAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));
        return Paths.get(config.getRootPath(), attachment.getStoragePath());
    }

    @Transactional
    public void deleteAttachment(Long attachmentId) throws IOException {
        ComplaintAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));

        Path filePath = Paths.get(config.getRootPath(), attachment.getStoragePath());
        Files.deleteIfExists(filePath);
        attachmentRepository.delete(attachment);
    }

    @Transactional(readOnly = true)
    public List<ComplaintAttachment> getAttachments(Long complaintId) {
        return attachmentRepository.findByComplaintId(complaintId);
    }

    @Async("taskExecutor")
    public void cleanupStaleTempUploads() {
        try {
            Path tempDir = config.getTempChunkDir();
            if (!Files.exists(tempDir)) return;

            long cutoff = System.currentTimeMillis() - 3600_000; // 1 hour
            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(tempDir)) {
                for (Path dir : dirs) {
                    if (Files.isDirectory(dir) && Files.getLastModifiedTime(dir).toMillis() < cutoff) {
                        cleanupChunkDir(dir);
                        log.info("Cleaned stale temp upload: {}", dir.getFileName());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to clean stale uploads", e);
        }
    }

    private void assembleChunks(Path chunkDir, Path target, int totalChunks) throws IOException {
        try (FileChannel outChannel = FileChannel.open(target,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            for (int i = 0; i < totalChunks; i++) {
                Path chunkFile = chunkDir.resolve("chunk_" + String.format("%05d", i));
                try (FileChannel inChannel = FileChannel.open(chunkFile, StandardOpenOption.READ)) {
                    inChannel.transferTo(0, inChannel.size(), outChannel);
                }
            }
        }
    }

    private void cleanupChunkDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
                for (Path f : files) {
                    Files.deleteIfExists(f);
                }
            }
            Files.deleteIfExists(dir);
        }
    }

    private String computeChecksum(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = is.read(buf)) != -1) {
                    md.update(buf, 0, read);
                }
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "unnamed";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String detectContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "csv" -> "text/csv";
            case "txt" -> "text/plain";
            case "zip" -> "application/zip";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "ogg" -> "video/ogg";
            case "mov" -> "video/quicktime";
            case "avi" -> "video/x-msvideo";
            case "mkv" -> "video/x-matroska";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "aac" -> "audio/aac";
            case "flac" -> "audio/flac";
            default -> "application/octet-stream";
        };
    }
}
