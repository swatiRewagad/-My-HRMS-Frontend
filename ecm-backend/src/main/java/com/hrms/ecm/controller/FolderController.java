package com.hrms.ecm.controller;

import com.hrms.ecm.dto.*;
import com.hrms.ecm.service.EcmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final EcmService ecmService;

    @GetMapping
    public ResponseEntity<List<FolderDto>> getRootFolders(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.ok(ecmService.getRootFolders(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderDto> getFolder(@PathVariable Long id) {
        return ResponseEntity.ok(ecmService.getFolderById(id));
    }

    @PostMapping
    public ResponseEntity<FolderDto> createFolder(
            @Valid @RequestBody CreateFolderRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        if (userId <= 0) userId = 1L;
        return ResponseEntity.ok(ecmService.createFolder(request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        ecmService.deleteFolder(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/access")
    public ResponseEntity<Void> grantAccess(
            @PathVariable Long id,
            @Valid @RequestBody GrantAccessRequest request) {
        ecmService.grantAccess(id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/access/bulk")
    public ResponseEntity<Void> grantBulkAccess(
            @PathVariable Long id,
            @RequestBody List<GrantAccessRequest> requests) {
        for (GrantAccessRequest request : requests) {
            ecmService.grantAccess(id, request);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{folderId}/access/{userId}")
    public ResponseEntity<Void> revokeAccess(@PathVariable Long folderId, @PathVariable Long userId) {
        ecmService.revokeAccess(folderId, userId);
        return ResponseEntity.noContent().build();
    }
}
