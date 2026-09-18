package com.hrms.cms.controller;

import com.hrms.cms.dto.ChunkUploadResponse;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.entity.EmailDraftAttachment;
import com.hrms.cms.repository.EmailDraftAttachmentRepository;
import com.hrms.cms.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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

        ChunkUploadResponse response = fileStorageService.handleChunkUpload(
                chunk, uploadId, chunkIndex, totalChunks,
                fileName, complaintNumber, complaintId, totalFileSize
        );

        if (response.getMessage() != null && response.getMessage().contains("not allowed")) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Single file upload for small files (< chunk size).
     *
     * <p>Records the uploader and source when supplied (UST589). Both are optional so the existing
     * callers that pass neither keep working; when absent, provenance is recorded as an OFFICER upload
     * attributed to the authenticated user, which is what every caller of this endpoint actually is.
     */
    @PostMapping("/upload")
    public ResponseEntity<ComplaintAttachment> uploadSingle(
            @RequestParam("file") MultipartFile file,
            @RequestParam("complaintNumber") String complaintNumber,
            @RequestParam("complaintId") Long complaintId,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestHeader(value = "X-User-Name", required = false) String userName
    ) throws IOException {

        ComplaintAttachment attachment = fileStorageService.handleSingleUpload(
                file, complaintNumber, complaintId,
                userName, ComplaintAttachment.SOURCE_OFFICER, documentType
        );
        return ResponseEntity.ok(attachment);
    }

    /**
     * GET /api/files/complaint/{complaintId}/bundle — every attachment as one ZIP (UST588).
     *
     * <p>No bundle capability existed anywhere in the product: {@code java.util.zip} appeared in zero
     * backend files, so an officer wanting a case's documents downloaded them one at a time.
     *
     * <p>Streamed rather than buffered, and the story's "must omit no valid attachment" requirement is
     * enforced in {@link FileStorageService#writeAttachmentBundle} — a row whose file is missing from
     * disk is reported inside the archive rather than silently skipped, because a short zip with no
     * warning would let a reader believe they held the complete record.
     */
    @GetMapping("/complaint/{complaintId}/bundle")
    public ResponseEntity<StreamingResponseBody> downloadBundle(
            @PathVariable Long complaintId,
            @RequestParam(value = "complaintNumber", required = false) String complaintNumber) {

        String reference = complaintNumber == null ? String.valueOf(complaintId) : complaintNumber;
        String safeReference = reference.replaceAll("[^A-Za-z0-9._-]", "_");

        StreamingResponseBody body = out -> fileStorageService
                .writeAttachmentBundle(complaintId, reference, out);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"complaint-" + safeReference + "-documents.zip\"")
                .body(body);
    }

    /**
     * Download an attachment by ID — supports forced download.
     */
    @GetMapping("/download/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long attachmentId) throws IOException {
        Path filePath = fileStorageService.getFilePath(attachmentId);

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filePath.getFileName().toString() + "\"")
                .body(resource);
    }

    /**
     * Stream/preview an attachment — supports HTTP Range requests for video/audio seeking.
     */
    @GetMapping("/stream/{attachmentId}")
    public ResponseEntity<ResourceRegion> stream(
            @PathVariable Long attachmentId,
            @RequestHeader HttpHeaders headers
    ) throws IOException {
        Path filePath = fileStorageService.getFilePath(attachmentId);

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);
        long fileLength = resource.contentLength();
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "application/octet-stream";

        ResourceRegion region;
        List<HttpRange> ranges = headers.getRange();

        if (!ranges.isEmpty()) {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(fileLength);
            long end = range.getRangeEnd(fileLength);
            long rangeLength = Math.min(end - start + 1, fileLength);

            region = new ResourceRegion(resource, start, rangeLength);
        } else {
            long chunkSize = Math.min(1024 * 1024, fileLength);
            region = new ResourceRegion(resource, 0, chunkSize);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    /**
     * List attachments for a complaint.
     */
    @GetMapping("/complaint/{complaintId}")
    public ResponseEntity<List<ComplaintAttachment>> listAttachments(@PathVariable Long complaintId) {
        return ResponseEntity.ok(fileStorageService.getAttachments(complaintId));
    }

    /**
     * Delete an attachment.
     */
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long attachmentId) throws IOException {
        fileStorageService.deleteAttachment(attachmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Serve an email-draft attachment file by its DB id.
     */
    @GetMapping("/email-draft/{attachmentId}")
    public ResponseEntity<Resource> downloadDraftAttachment(@PathVariable Long attachmentId) throws IOException {
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
}
