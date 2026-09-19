package com.hrms.cms.service;

import com.hrms.cms.config.FileStorageConfig;
import com.hrms.cms.dto.ChunkUploadResponse;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.exception.FileStorageException;
import com.hrms.cms.repository.ComplaintAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final FileStorageConfig config;
    private final ComplaintAttachmentRepository attachmentRepository;
    private final StorageServiceClient storageServiceClient;

    @Transactional
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
            throw FileStorageException.unsupportedType(config.getAllowedTypes());
        }
        if (totalFileSize > config.getMaxFileSize()) {
            throw FileStorageException.fileTooLarge(config.getMaxFileSize());
        }

        String actualUploadId = uploadId != null ? uploadId : UUID.randomUUID().toString();

        Path chunkDir = config.getTempChunkDir().resolve(actualUploadId);
        Files.createDirectories(chunkDir);

        Path chunkFile = chunkDir.resolve("chunk_" + String.format("%05d", chunkIndex));
        try (InputStream in = chunk.getInputStream()) {
            Files.copy(in, chunkFile, StandardCopyOption.REPLACE_EXISTING);
        }

        long receivedChunks;
        try (Stream<Path> entries = Files.list(chunkDir)) {
            receivedChunks = entries
                    .filter(p -> p.getFileName().toString().startsWith("chunk_"))
                    .count();
        }

        if (receivedChunks < totalChunks) {
            return ChunkUploadResponse.builder()
                    .uploadId(actualUploadId)
                    .chunkIndex(chunkIndex)
                    .totalChunks(totalChunks)
                    .complete(false)
                    .message("Chunk " + (chunkIndex + 1) + "/" + totalChunks + " received")
                    .build();
        }

        // All chunks received — assemble into a scratch file, hand the bytes to the storage
        // service, then drop the scratch file. Nothing permanent stays on local disk.
        Path assembled = chunkDir.resolve("assembled.tmp");
        byte[] content;
        try {
            assembleChunks(chunkDir, assembled, totalChunks);
            content = Files.readAllBytes(assembled);
        } finally {
            cleanupChunkDir(chunkDir);
        }

        String storedName = UUID.randomUUID() + "_" + sanitizeFileName(fileName);
        String contentType = detectContentType(fileName);
        String objectId = storageServiceClient.store(content, storedName, contentType, complaintNumber);

        ComplaintAttachment saved = attachmentRepository.save(ComplaintAttachment.builder()
                .complaintId(complaintId)
                .fileName(storedName)
                .originalName(fileName)
                .contentType(contentType)
                .fileSize((long) content.length)
                .storagePath(objectId)
                .build());

        log.info("Assembled {} chunks of {} into storage objectId={}", totalChunks, fileName, objectId);

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

        String originalName = file.getOriginalFilename();

        if (!config.isAllowedType(originalName)) {
            throw FileStorageException.unsupportedType(config.getAllowedTypes());
        }
        if (file.getSize() > config.getMaxFileSize()) {
            throw FileStorageException.fileTooLarge(config.getMaxFileSize());
        }
        if (attachmentRepository.findByComplaintId(complaintId).size() >= config.getMaxFilesPerComplaint()) {
            throw FileStorageException.attachmentLimitReached(config.getMaxFilesPerComplaint());
        }

        String storedName = UUID.randomUUID() + "_" + sanitizeFileName(originalName);
        String contentType = file.getContentType() != null ? file.getContentType() : detectContentType(originalName);
        String objectId = storageServiceClient.store(file.getBytes(), storedName, contentType, complaintNumber);

        return attachmentRepository.save(ComplaintAttachment.builder()
                .complaintId(complaintId)
                .fileName(storedName)
                .originalName(originalName)
                .contentType(contentType)
                .fileSize(file.getSize())
                .storagePath(objectId)
                .build());
    }

    public String getRootPath() {
        return config.getRootPath();
    }

    @Transactional(readOnly = true)
    public ComplaintAttachment getAttachmentMetadata(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> FileStorageException.attachmentNotFound(attachmentId));
    }

    public byte[] getFileBytes(Long attachmentId) {
        return storageServiceClient.fetch(getAttachmentMetadata(attachmentId).getStoragePath());
    }

    @Transactional
    public void deleteAttachment(Long attachmentId) {
        ComplaintAttachment attachment = getAttachmentMetadata(attachmentId);
        storageServiceClient.delete(attachment.getStoragePath());
        attachmentRepository.delete(attachment);
    }

    @Transactional(readOnly = true)
    public List<ComplaintAttachment> getAttachments(Long complaintId) {
        return attachmentRepository.findByComplaintId(complaintId);
    }

    /**
     * Metadata is read up front; each payload is pulled from the storage service only as its zip
     * entry is written, so a complaint's attachments are never all held in memory at once.
     */
    @Transactional(readOnly = true)
    public StreamingResponseBody downloadAllAttachmentsAsZip(Long complaintId) {
        List<ComplaintAttachment> attachments = attachmentRepository.findByComplaintId(complaintId);
        if (attachments.isEmpty()) {
            throw new FileStorageException("No attachments found for complaint " + complaintId,
                    HttpStatus.NOT_FOUND, "NO_ATTACHMENTS");
        }

        return outputStream -> {
            Set<String> usedNames = new HashSet<>();
            try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
                for (ComplaintAttachment attachment : attachments) {
                    byte[] content;
                    try {
                        content = storageServiceClient.fetch(attachment.getStoragePath());
                    } catch (FileStorageException e) {
                        log.warn("Skipping attachment {} in zip for complaint {}: {}",
                                attachment.getId(), complaintId, e.getMessage());
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry(uniqueEntryName(usedNames, attachment)));
                    zip.write(content);
                    zip.closeEntry();
                }
            }
        };
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

    private static String uniqueEntryName(Set<String> usedNames, ComplaintAttachment attachment) {
        String name = attachment.getOriginalName() != null ? attachment.getOriginalName() : attachment.getFileName();
        if (usedNames.add(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        String unique = base + "_" + attachment.getId() + ext;
        usedNames.add(unique);
        return unique;
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
