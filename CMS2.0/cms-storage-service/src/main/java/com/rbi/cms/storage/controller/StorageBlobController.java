package com.rbi.cms.storage.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.storage.dto.StorageBlobMetadata;
import com.rbi.cms.storage.dto.StorageBlobResponse;
import com.rbi.cms.storage.entity.StorageBlob;
import com.rbi.cms.storage.service.BlobStorageService;
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

@RestController
@RequestMapping("/api/v1/storage/blobs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Storage Blobs", description = "Database-backed binary storage (STORAGE_BLOBS)")
public class StorageBlobController {

    private final BlobStorageService blobStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Store a blob", description = "Persists the payload into STORAGE_BLOBS and returns its objectId")
    public ResponseEntity<ApiResponse<StorageBlobResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bucket", required = false) String bucket,
            @RequestParam(value = "tenantId", required = false) String tenantId) {

        StorageBlobResponse stored = blobStorageService.store(bucket, tenantId, file);
        return ResponseEntity.ok(ApiResponse.success(stored, "Blob stored successfully"));
    }

    @GetMapping("/{objectId}")
    @Operation(summary = "Fetch blob metadata", description = "Returns blob attributes without the payload")
    public ResponseEntity<ApiResponse<StorageBlobMetadata>> metadata(@PathVariable String objectId) {
        return ResponseEntity.ok(ApiResponse.success(blobStorageService.metadata(objectId)));
    }

    @GetMapping("/{objectId}/content")
    @Operation(summary = "Fetch blob payload", description = "Streams the stored bytes for the given objectId")
    public ResponseEntity<Resource> content(@PathVariable String objectId) {
        StorageBlob blob = blobStorageService.fetch(objectId);
        byte[] data = blob.getStorageData();

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (blob.getContentType() != null) {
            try {
                mediaType = MediaType.parseMediaType(blob.getContentType());
            } catch (org.springframework.http.InvalidMediaTypeException ignored) {
                // stored value is not a parseable media type — fall back to octet-stream
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(data.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + blob.getFileName() + "\"")
                .body(new ByteArrayResource(data));
    }

    @DeleteMapping("/{objectId}")
    @Operation(summary = "Delete a blob", description = "Removes the STORAGE_BLOBS row for the given objectId")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String objectId) {
        blobStorageService.delete(objectId);
        return ResponseEntity.ok(ApiResponse.success(null, "Blob deleted"));
    }
}
