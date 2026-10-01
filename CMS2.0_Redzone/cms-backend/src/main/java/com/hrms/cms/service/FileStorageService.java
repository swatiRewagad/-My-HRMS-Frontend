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
    private final FileUploadValidator uploadValidator;

    /** Size and count limits are runtime configuration; see {@link FileUploadValidator}. */
    private final UploadLimitsService uploadLimits;

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

        try {
            uploadValidator.validateName(fileName);
        } catch (FileUploadValidator.InvalidUploadException e) {
            return ChunkUploadResponse.builder()
                    .message(e.getMessage())
                    .complete(false)
                    .build();
        }

        long maxFileSize = uploadLimits.maxFileSizeBytes();
        if (totalFileSize > maxFileSize) {
            return ChunkUploadResponse.builder()
                    .message("File exceeds maximum size of " + (maxFileSize / 1048576) + "MB")
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

        // The magic bytes only exist once every chunk has landed, so a disguised payload can only be
        // caught here — and must be deleted, not merely reported, or it stays on disk unreferenced.
        String signatureError = uploadValidator.checkAssembledSignature(finalPath, fileName);
        if (signatureError != null) {
            Files.deleteIfExists(finalPath);
            return ChunkUploadResponse.builder()
                    .message(signatureError)
                    .complete(false)
                    .build();
        }

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
        return handleSingleUpload(file, complaintNumber, complaintId, null, null, null);
    }

    /**
     * Stores one file, recording WHO supplied it and in what capacity (UST589).
     *
     * <p>The three-argument overload above delegates here with nulls, so every existing caller keeps
     * its current behaviour and no call site silently starts claiming a provenance it does not know.
     *
     * @param uploadedBy   the uploader's identity; null when genuinely unknown
     * @param source       ComplaintAttachment.SOURCE_* — the capacity the uploader acted in
     * @param documentType optional classification, e.g. MEETING_MINUTES
     */
    @Transactional
    public ComplaintAttachment handleSingleUpload(
            MultipartFile file,
            String complaintNumber,
            Long complaintId,
            String uploadedBy,
            String source,
            String documentType
    ) throws IOException {

        uploadValidator.validate(file);

        List<ComplaintAttachment> existing = attachmentRepository.findByComplaintId(complaintId);
        int maxFileCount = uploadLimits.maxFileCount();
        if (existing.size() >= maxFileCount) {
            throw new IllegalArgumentException("Max files per complaint reached (" + maxFileCount + ")");
        }

        // The AGGREGATE cap, which handleSingleUpload previously did not apply at all: ten separate
        // single-file uploads could each pass the per-file check and together exceed the record's total
        // budget. validateBatch already expressed this rule and had no production caller.
        long existingBytes = existing.stream()
                .mapToLong(a -> a.getFileSize() == null ? 0L : a.getFileSize())
                .sum();
        uploadValidator.validateBatch(List.of(file), existing.size(), existingBytes);

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
                .uploadedBy(uploadedBy)
                .source(source)
                .documentType(documentType)
                .build();

        return attachmentRepository.save(attachment);
    }

    /**
     * Streams every attachment on a complaint as one ZIP (UST588).
     *
     * <p>Written to an OutputStream rather than assembled in memory: the per-record budget is 25MB and
     * buffering that per concurrent request is avoidable waste.
     *
     * <p>MUST OMIT NO VALID ATTACHMENT, which is the story's explicit requirement — so a row whose file
     * is missing from disk does not abort the bundle. It is recorded in a MANIFEST entry instead,
     * because silently returning a short zip would let a reader believe they had everything. A caller
     * who receives 9 of 10 documents and no warning is worse off than one who is told which is missing.
     *
     * @return the number of files successfully written
     */
    @Transactional(readOnly = true)
    public int writeAttachmentBundle(Long complaintId, String complaintNumber, OutputStream out)
            throws IOException {

        List<ComplaintAttachment> attachments = attachmentRepository.findByComplaintId(complaintId);
        int written = 0;
        StringBuilder missing = new StringBuilder();

        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            java.util.Set<String> usedNames = new java.util.HashSet<>();

            for (ComplaintAttachment a : attachments) {
                Path source = Paths.get(config.getRootPath(), a.getStoragePath());
                if (!Files.exists(source) || !Files.isReadable(source)) {
                    missing.append(a.getOriginalName()).append(" (id ").append(a.getId()).append(")\n");
                    log.warn("Attachment {} for complaint {} is recorded but missing on disk at {}",
                            a.getId(), complaintNumber, source);
                    continue;
                }

                // Two documents may legitimately share an original filename; a duplicate zip entry name
                // would throw and abort the whole bundle.
                String entryName = uniqueEntryName(a, usedNames);
                zip.putNextEntry(new java.util.zip.ZipEntry(entryName));
                Files.copy(source, zip);
                zip.closeEntry();
                written++;
            }

            if (missing.length() > 0) {
                zip.putNextEntry(new java.util.zip.ZipEntry("MISSING-FILES.txt"));
                zip.write(("These attachments are recorded against complaint " + complaintNumber
                        + " but their stored files could not be read. Report this to support:\n\n"
                        + missing).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return written;
    }

    private String uniqueEntryName(ComplaintAttachment a, java.util.Set<String> used) {
        String base = sanitizeFileName(a.getOriginalName() == null ? a.getFileName() : a.getOriginalName());
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate)) {
            int dot = base.lastIndexOf('.');
            candidate = dot > 0
                    ? base.substring(0, dot) + "(" + suffix + ")" + base.substring(dot)
                    : base + "(" + suffix + ")";
            suffix++;
        }
        return candidate;
    }

    public String getRootPath() {
        return config.getRootPath();
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
