package com.hrms.ecm.controller;

import com.hrms.ecm.dto.*;
import com.hrms.ecm.entity.FileEntity;
import com.hrms.ecm.service.EcmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final EcmService ecmService;

    @GetMapping("/folder/{folderId}")
    public ResponseEntity<List<FileDto>> getFilesByFolder(@PathVariable Long folderId) {
        return ResponseEntity.ok(ecmService.getFilesByFolder(folderId));
    }

    @PostMapping("/upload")
    public ResponseEntity<FileDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folderId") Long folderId,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) throws IOException {
        return ResponseEntity.ok(ecmService.uploadFile(file, folderId, userId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id) throws IOException {
        FileEntity fileEntity = ecmService.getFileEntity(id);
        byte[] data = ecmService.downloadFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileEntity.getOriginalName() + "\"")
                .contentType(MediaType.parseMediaType(fileEntity.getContentType() != null ? fileEntity.getContentType() : "application/octet-stream"))
                .body(data);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> previewFile(@PathVariable Long id) throws IOException {
        FileEntity fileEntity = ecmService.getFileEntity(id);
        byte[] data = ecmService.downloadFile(id);
        String contentType = fileEntity.getContentType() != null ? fileEntity.getContentType() : "application/octet-stream";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileEntity.getOriginalName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .contentType(MediaType.parseMediaType(contentType))
                .body(data);
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<Resource> streamFile(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {

        FileEntity fileEntity = ecmService.getFileEntity(id);
        Path filePath = Paths.get(fileEntity.getStoragePath());
        Resource resource = new FileSystemResource(filePath);

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        long fileLength = resource.contentLength();
        String contentType = fileEntity.getContentType() != null ? fileEntity.getContentType() : "application/octet-stream";

        if (rangeHeader == null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileEntity.getOriginalName() + "\"")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileLength))
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        }

        String range = rangeHeader.replace("bytes=", "");
        String[] ranges = range.split("-");
        long rangeStart = Long.parseLong(ranges[0]);
        long rangeEnd = ranges.length > 1 && !ranges[1].isEmpty() ? Long.parseLong(ranges[1]) : fileLength - 1;

        if (rangeEnd >= fileLength) {
            rangeEnd = fileLength - 1;
        }

        long contentLength = rangeEnd - rangeStart + 1;

        java.io.InputStream inputStream = java.nio.file.Files.newInputStream(filePath);
        inputStream.skip(rangeStart);
        byte[] data = inputStream.readNBytes((int) contentLength);
        inputStream.close();

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileEntity.getOriginalName() + "\"")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + rangeStart + "-" + rangeEnd + "/" + fileLength)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new org.springframework.core.io.ByteArrayResource(data));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) throws IOException {
        ecmService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<FileDto>> searchFiles(@RequestParam String query) {
        return ResponseEntity.ok(ecmService.searchFiles(query));
    }

    @PostMapping("/share")
    public ResponseEntity<FileDto.ShareDto> shareFile(
            @Valid @RequestBody ShareRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return ResponseEntity.ok(ecmService.shareFile(request, userId));
    }

    @GetMapping("/shared-with-me")
    public ResponseEntity<List<FileDto>> getSharedWithMe(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return ResponseEntity.ok(ecmService.getSharedWithMe(userId));
    }

    @GetMapping("/shared/{token}")
    public ResponseEntity<byte[]> downloadByShareToken(@PathVariable String token) throws IOException {
        FileEntity fileEntity = ecmService.getFileByShareToken(token);
        byte[] data = ecmService.downloadFile(fileEntity.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileEntity.getOriginalName() + "\"")
                .contentType(MediaType.parseMediaType(fileEntity.getContentType() != null ? fileEntity.getContentType() : "application/octet-stream"))
                .body(data);
    }

    @PostMapping("/upload/init")
    public ResponseEntity<ChunkUploadResponse> initChunkUpload(
            @Valid @RequestBody ChunkUploadInitRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return ResponseEntity.ok(ecmService.initChunkUpload(request, userId));
    }

    @PostMapping("/upload/chunk")
    public ResponseEntity<ChunkUploadResponse> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("chunk") MultipartFile chunk) throws IOException {
        return ResponseEntity.ok(ecmService.uploadChunk(uploadId, chunkIndex, chunk));
    }
}
