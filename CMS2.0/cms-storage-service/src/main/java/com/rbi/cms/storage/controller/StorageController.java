package com.rbi.cms.storage.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.storage.service.InternalStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Tag(name = "Storage", description = "Internal file storage service")
public class StorageController {

    private final InternalStorageService storageService;

    @PostMapping(value = "/{bucket}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload file to storage", description = "Store a file in the specified bucket")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(
            @PathVariable String bucket,
            @RequestParam("file") MultipartFile file) throws IOException {

        String path = storageService.store(bucket, file);
        String checksum = storageService.computeChecksum(file.getBytes());
        return ResponseEntity.ok(ApiResponse.success(Map.of("storagePath", path, "checksum", checksum)));
    }

    @GetMapping("/download")
    @Operation(summary = "Download file", description = "Retrieve a file by its storage path")
    public ResponseEntity<Resource> download(@RequestParam String path) throws IOException {
        byte[] content = storageService.retrieve(path);
        ByteArrayResource resource = new ByteArrayResource(content);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"file\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.length)
                .body(resource);
    }

    @DeleteMapping
    @Operation(summary = "Delete file", description = "Remove a file from storage")
    public ResponseEntity<ApiResponse<Void>> delete(@RequestParam String path) throws IOException {
        storageService.delete(path);
        return ResponseEntity.ok(ApiResponse.success(null, "File deleted"));
    }
}
