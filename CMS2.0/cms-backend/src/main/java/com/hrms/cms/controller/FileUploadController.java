package com.hrms.cms.controller;

import com.hrms.cms.dto.ApiResponse;
import com.hrms.cms.dto.ChunkUploadResponse;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.entity.EmailDraftAttachment;
import com.hrms.cms.repository.EmailDraftAttachmentRepository;
import com.hrms.cms.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {

    private static final long DEFAULT_PREVIEW_CHUNK = 1024L * 1024L;

    private final FileStorageService fileStorageService;
    private final EmailDraftAttachmentRepository draftAttachmentRepository;

    /**
     * Chunked upload endpoint.
     * Frontend sends each chunk as a multipart POST.
     */
    @PostMapping("/upload/chunk")
    public ResponseEntity<ChunkUploadResponse> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("fileName") String fileName,
            @RequestParam("complaintNumber") String complaintNumber,
            @RequestParam("complaintId") Long complaintId,
            @RequestParam("totalFileSize") long totalFileSize
    ) throws IOException {

        return ResponseEntity.ok(fileStorageService.handleChunkUpload(
                chunk, uploadId, chunkIndex, totalChunks,
                fileName, complaintNumber, complaintId, totalFileSize
        ));
    }

    /**
     * Single file upload for small files (< chunk size).
     */
    @PostMapping("/upload")
    public ResponseEntity<ComplaintAttachment> uploadSingle(
            @RequestParam("file") MultipartFile file,
            @RequestParam("complaintNumber") String complaintNumber,
            @RequestParam("complaintId") Long complaintId
    ) throws IOException {

        return ResponseEntity.ok(fileStorageService.handleSingleUpload(file, complaintNumber, complaintId));
    }

    /**
     * List attachments for a complaint.
     */
    @GetMapping("/complaint/{complaintId}")
    public ResponseEntity<ApiResponse<List<ComplaintAttachment>>> listAttachments(@PathVariable Long complaintId) {
        List<ComplaintAttachment> attachments = fileStorageService.getAttachments(complaintId);
        return ResponseEntity.ok(ApiResponse.<List<ComplaintAttachment>>builder()
                .success(true)
                .message("Attachments fetched successfully")
                .data(attachments)
                .totalCount((long) attachments.size())
                .build());
    }

    /**
     * Download an attachment by ID — supports forced download.
     */
    @GetMapping("/download/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long attachmentId) {
        ComplaintAttachment attachment = fileStorageService.getAttachmentMetadata(attachmentId);
        byte[] content = fileStorageService.getFileBytes(attachmentId);

        return ResponseEntity.ok()
                .contentType(mediaTypeOf(attachment))
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getOriginalName() + "\"")
                .body(new ByteArrayResource(content));
    }

    /**
     * Stream/preview an attachment — supports HTTP Range requests for video/audio seeking.
     */
    @GetMapping("/stream/{attachmentId}")
    public ResponseEntity<ResourceRegion> stream(
            @PathVariable Long attachmentId,
            @RequestHeader HttpHeaders headers
    ) {
        ComplaintAttachment attachment = fileStorageService.getAttachmentMetadata(attachmentId);
        Resource resource = new ByteArrayResource(fileStorageService.getFileBytes(attachmentId));
        long fileLength = ((ByteArrayResource) resource).getByteArray().length;

        ResourceRegion region;
        List<HttpRange> ranges = headers.getRange();
        if (!ranges.isEmpty()) {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(fileLength);
            long end = range.getRangeEnd(fileLength);
            region = new ResourceRegion(resource, start, Math.min(end - start + 1, fileLength));
        } else {
            region = new ResourceRegion(resource, 0, Math.min(DEFAULT_PREVIEW_CHUNK, fileLength));
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaTypeOf(attachment))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + attachment.getOriginalName() + "\"")
                .body(region);
    }

    /**
     * Download every attachment of a complaint as a single zip.
     */
    @GetMapping("/complaint/{complaintId}/download-all")
    public ResponseEntity<StreamingResponseBody> downloadAll(@PathVariable Long complaintId) {
        StreamingResponseBody body = fileStorageService.downloadAllAttachmentsAsZip(complaintId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"complaint_" + complaintId + "_attachments.zip\"")
                .body(body);
    }

    /**
     * Delete an attachment.
     */
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long attachmentId) {
        fileStorageService.deleteAttachment(attachmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Serve an email-draft attachment file by its DB id.
     */
    @GetMapping("/email-draft/{attachmentId}")
    public ResponseEntity<Resource> downloadDraftAttachment(@PathVariable Long attachmentId) {
        EmailDraftAttachment att = draftAttachmentRepository.findById(attachmentId).orElse(null);
        if (att == null || att.getStoragePath() == null || att.getStoragePath().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(att.getStoragePath()).normalize();
        Path allowedRoot = Paths.get(fileStorageService.getRootPath()).normalize();
        if (!filePath.startsWith(allowedRoot)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);
        String contentType = att.getFileType() != null ? att.getFileType() : "application/octet-stream";
        String safeFileName = Paths.get(att.getFileName()).getFileName().toString();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + safeFileName + "\"")
                .body(resource);
    }

    /**
     * List email-draft attachments by draft ID.
     */
    @GetMapping("/email-draft/by-draft/{draftId}")
    public ResponseEntity<List<EmailDraftAttachment>> listDraftAttachments(@PathVariable String draftId) {
        return ResponseEntity.ok(draftAttachmentRepository.findByDraftIdOrderByCreatedAtAsc(draftId));
    }

    /**
     * Cleanup stale temporary uploads (called periodically or manually).
     */
    @PostMapping("/cleanup")
    public ResponseEntity<String> cleanup() {
        fileStorageService.cleanupStaleTempUploads();
        return ResponseEntity.ok("Cleanup initiated");
    }

    private static MediaType mediaTypeOf(ComplaintAttachment attachment) {
        if (attachment.getContentType() == null || attachment.getContentType().isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(attachment.getContentType());
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
