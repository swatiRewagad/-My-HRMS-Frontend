package com.hrms.ecm.service;

import com.hrms.ecm.dto.*;
import com.hrms.ecm.dto.DashboardResponse.*;
import com.hrms.ecm.entity.*;
import com.hrms.ecm.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EcmService {

    private static final long STORAGE_CAPACITY = 10L * 1024 * 1024 * 1024; // 10 GB

    private final EcmUserRepository userRepo;
    private final FolderRepository folderRepo;
    private final FileRepository fileRepo;
    private final FolderAccessRepository accessRepo;
    private final FileShareRepository shareRepo;
    private final ActivityLogRepository activityRepo;
    private final ChunkUploadRepository chunkUploadRepo;
    private final FileStorageService storageService;

    // ───── Dashboard ─────

    public DashboardResponse getDashboard() {
        long totalFiles = fileRepo.count();
        long totalFolders = folderRepo.count();
        long publicFolders = folderRepo.countByVisibility("public");
        long privateFolders = folderRepo.countByVisibility("private");
        long totalShares = shareRepo.count();
        long totalUsers = userRepo.count();

        Long usedBytes = fileRepo.totalStorageUsed();
        if (usedBytes == null) usedBytes = 0L;

        List<Object[]> typeCounts = fileRepo.countByContentType();
        List<FileTypeCountDto> fileTypes = typeCounts.stream()
                .map(r -> FileTypeCountDto.builder()
                        .type((String) r[0])
                        .count((Long) r[1])
                        .build())
                .toList();

        List<FileEntity> recentFiles = fileRepo.findTop10ByOrderByUploadedAtDesc();
        List<RecentFileDto> recentUploads = recentFiles.stream().map(this::toRecentFile).toList();

        List<ActivityDto> activities = activityRepo.findTop15ByOrderByPerformedAtDesc().stream()
                .map(a -> ActivityDto.builder()
                        .action(a.getAction())
                        .entityType(a.getEntityType())
                        .entityName(a.getEntityName())
                        .userName(a.getUserName())
                        .performedAt(a.getPerformedAt())
                        .build())
                .toList();

        long publicFiles = folderRepo.findByVisibility("public").stream()
                .mapToLong(f -> fileRepo.countByFolderId(f.getId()))
                .sum();
        long privateFiles = folderRepo.findByVisibility("private").stream()
                .mapToLong(f -> fileRepo.countByFolderId(f.getId()))
                .sum();

        return DashboardResponse.builder()
                .stats(StatsDto.builder()
                        .totalFiles(totalFiles).totalFolders(totalFolders)
                        .publicFolders(publicFolders).privateFolders(privateFolders)
                        .totalShares(totalShares).totalUsers(totalUsers).build())
                .storage(StorageDto.builder()
                        .usedBytes(usedBytes).capacityBytes(STORAGE_CAPACITY)
                        .usedFormatted(formatSize(usedBytes)).capacityFormatted(formatSize(STORAGE_CAPACITY))
                        .usedPercent(totalFiles == 0 ? 0 : (usedBytes * 100.0 / STORAGE_CAPACITY)).build())
                .fileTypeBreakdown(fileTypes)
                .recentUploads(recentUploads)
                .recentActivity(activities)
                .folderSummary(FolderSummaryDto.builder()
                        .publicCount(publicFolders).privateCount(privateFolders)
                        .publicFiles(publicFiles).privateFiles(privateFiles).build())
                .build();
    }

    // ───── Folders ─────

    public List<FolderDto> getRootFolders(Long userId) {
        return folderRepo.findByParentIdIsNullOrderByNameAsc().stream()
                .filter(f -> canUserAccessFolder(f, userId))
                .map(f -> toFolderDtoWithChildren(f, userId))
                .toList();
    }

    public List<FolderDto> getRootFolders() {
        return folderRepo.findByParentIdIsNullOrderByNameAsc().stream()
                .map(f -> toFolderDtoWithChildren(f, null))
                .toList();
    }

    private boolean canUserAccessFolder(Folder folder, Long userId) {
        if (userId == null) return true;
        if (folder.getOwnerId().equals(userId)) return true;
        if ("public".equalsIgnoreCase(folder.getVisibility())) {
            List<FolderAccess> accessList = accessRepo.findByFolderId(folder.getId());
            if (accessList.isEmpty()) return true;
            return accessList.stream().anyMatch(a -> a.getUserId().equals(userId));
        }
        return accessRepo.findByFolderIdAndUserId(folder.getId(), userId).isPresent();
    }

    private FolderDto toFolderDtoWithChildren(Folder f, Long userId) {
        FolderDto dto = toFolderDto(f);
        List<FolderDto> children = folderRepo.findByParentIdOrderByNameAsc(f.getId()).stream()
                .filter(child -> canUserAccessFolder(child, userId))
                .map(child -> toFolderDtoWithChildren(child, userId))
                .toList();
        dto.setChildren(children);
        return dto;
    }

    public FolderDto getFolderById(Long id) {
        Folder folder = folderRepo.findById(id).orElseThrow(() -> new RuntimeException("Folder not found"));
        FolderDto dto = toFolderDto(folder);
        dto.setChildren(folderRepo.findByParentIdOrderByNameAsc(id).stream().map(this::toFolderDto).toList());
        dto.setAccessList(accessRepo.findByFolderId(id).stream().map(this::toAccessEntry).toList());
        return dto;
    }

    public FolderDto createFolder(CreateFolderRequest request, Long ownerId) {
        Folder parent = request.getParentId() != null ? folderRepo.findById(request.getParentId()).orElse(null) : null;
        String path = parent != null ? parent.getPath() + "/" + request.getName() : "/" + request.getName();

        Folder folder = Folder.builder()
                .name(request.getName())
                .visibility(request.getVisibility())
                .description(request.getDescription())
                .parentId(request.getParentId())
                .ownerId(ownerId)
                .path(path)
                .build();
        folder = folderRepo.save(folder);
        logActivity("Folder created", "FOLDER", folder.getName(), ownerId);
        return toFolderDto(folder);
    }

    @Transactional
    public void deleteFolder(Long folderId, Long userId) {
        Folder folder = folderRepo.findById(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        if (!folder.getOwnerId().equals(userId)) {
            throw new RuntimeException("Only the folder owner can delete it");
        }
        List<Folder> children = folderRepo.findByParentIdOrderByNameAsc(folderId);
        for (Folder child : children) {
            deleteFolder(child.getId(), userId);
        }
        List<FileEntity> files = fileRepo.findByFolderIdOrderByUploadedAtDesc(folderId);
        for (FileEntity file : files) {
            try { storageService.delete(file.getStoragePath()); } catch (Exception ignored) {}
            fileRepo.delete(file);
        }
        accessRepo.findByFolderId(folderId).forEach(accessRepo::delete);
        folderRepo.delete(folder);
        logActivity("Folder deleted", "FOLDER", folder.getName(), userId);
    }

    @Transactional
    public void grantAccess(Long folderId, GrantAccessRequest request) {
        accessRepo.findByFolderIdAndUserId(folderId, request.getUserId())
                .ifPresent(existing -> accessRepo.delete(existing));

        FolderAccess access = FolderAccess.builder()
                .folderId(folderId)
                .userId(request.getUserId())
                .permission(request.getPermission())
                .build();
        accessRepo.save(access);

        Folder folder = folderRepo.findById(folderId).orElse(null);
        logActivity("Access granted", "FOLDER", folder != null ? folder.getName() : "Unknown", request.getUserId());
    }

    @Transactional
    public void revokeAccess(Long folderId, Long userId) {
        accessRepo.deleteByFolderIdAndUserId(folderId, userId);
    }

    // ───── Files ─────

    public List<FileDto> getFilesByFolder(Long folderId) {
        return fileRepo.findByFolderIdOrderByUploadedAtDesc(folderId).stream()
                .map(this::toFileDto)
                .toList();
    }

    public FileDto uploadFile(MultipartFile file, Long folderId, Long userId) throws IOException {
        Folder folder = folderRepo.findById(folderId).orElseThrow(() -> new RuntimeException("Folder not found"));
        String storagePath = storageService.store(file, folder.getVisibility() + "/" + folder.getId());

        FileEntity entity = FileEntity.builder()
                .name(UUID.randomUUID().toString())
                .originalName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .storagePath(storagePath)
                .folderId(folderId)
                .uploadedBy(userId)
                .build();
        entity = fileRepo.save(entity);
        logActivity("File uploaded", "FILE", file.getOriginalFilename(), userId);
        return toFileDto(entity);
    }

    public byte[] downloadFile(Long fileId) throws IOException {
        FileEntity file = fileRepo.findById(fileId).orElseThrow(() -> new RuntimeException("File not found"));
        return storageService.load(file.getStoragePath());
    }

    public FileEntity getFileEntity(Long fileId) {
        return fileRepo.findById(fileId).orElseThrow(() -> new RuntimeException("File not found"));
    }

    @Transactional
    public void deleteFile(Long fileId) throws IOException {
        FileEntity file = fileRepo.findById(fileId).orElseThrow(() -> new RuntimeException("File not found"));
        storageService.delete(file.getStoragePath());
        fileRepo.delete(file);
        logActivity("File deleted", "FILE", file.getOriginalName(), file.getUploadedBy());
    }

    public List<FileDto> searchFiles(String query) {
        return fileRepo.search(query).stream().map(this::toFileDto).toList();
    }

    // ───── Sharing ─────

    public FileDto.ShareDto shareFile(ShareRequest request, Long userId) {
        String token = "link".equals(request.getShareType()) ? UUID.randomUUID().toString().replace("-", "").substring(0, 16) : null;

        FileShare share = FileShare.builder()
                .fileId(request.getFileId())
                .sharedBy(userId)
                .sharedWith(request.getSharedWith())
                .shareType(request.getShareType())
                .shareToken(token)
                .permission(request.getPermission() != null ? request.getPermission() : "view")
                .expiresAt(request.getExpiresInHours() != null ? LocalDateTime.now().plusHours(request.getExpiresInHours()) : null)
                .build();
        share = shareRepo.save(share);

        FileEntity file = fileRepo.findById(request.getFileId()).orElse(null);
        logActivity("File shared", "FILE", file != null ? file.getOriginalName() : "Unknown", userId);
        return toShareDto(share);
    }

    public List<FileDto> getSharedWithMe(Long userId) {
        List<FileShare> shares = shareRepo.findBySharedWith(userId);
        return shares.stream()
                .map(s -> fileRepo.findById(s.getFileId()).orElse(null))
                .filter(Objects::nonNull)
                .map(this::toFileDto)
                .toList();
    }

    public FileEntity getFileByShareToken(String token) {
        FileShare share = shareRepo.findByShareToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired share link"));
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Share link has expired");
        }
        return fileRepo.findById(share.getFileId())
                .orElseThrow(() -> new RuntimeException("File not found"));
    }

    public List<EcmUser> getAllUsers() {
        return userRepo.findAll();
    }

    // ───── Chunked Upload ─────

    public ChunkUploadResponse initChunkUpload(ChunkUploadInitRequest request, Long userId) {
        folderRepo.findById(request.getFolderId()).orElseThrow(() -> new RuntimeException("Folder not found"));

        ChunkUpload upload = ChunkUpload.builder()
                .uploadId(UUID.randomUUID().toString())
                .fileName(request.getFileName())
                .contentType(request.getContentType())
                .totalSize(request.getTotalSize())
                .totalChunks(request.getTotalChunks())
                .folderId(request.getFolderId())
                .uploadedBy(userId)
                .build();
        upload = chunkUploadRepo.save(upload);

        return ChunkUploadResponse.builder()
                .uploadId(upload.getUploadId())
                .chunksReceived(0)
                .totalChunks(upload.getTotalChunks())
                .status("uploading")
                .build();
    }

    @Transactional
    public ChunkUploadResponse uploadChunk(String uploadId, int chunkIndex, MultipartFile chunk) throws IOException {
        ChunkUpload upload = chunkUploadRepo.findByUploadId(uploadId)
                .orElseThrow(() -> new RuntimeException("Upload not found"));

        storageService.storeChunk(uploadId, chunkIndex, chunk);
        upload.setChunksReceived(upload.getChunksReceived() + 1);

        if (upload.getChunksReceived().equals(upload.getTotalChunks())) {
            Folder folder = folderRepo.findById(upload.getFolderId()).orElseThrow();
            String storagePath = storageService.mergeChunks(uploadId, upload.getTotalChunks(),
                    folder.getVisibility() + "/" + folder.getId(), upload.getFileName());

            FileEntity entity = FileEntity.builder()
                    .name(UUID.randomUUID().toString())
                    .originalName(upload.getFileName())
                    .contentType(upload.getContentType())
                    .size(upload.getTotalSize())
                    .storagePath(storagePath)
                    .folderId(upload.getFolderId())
                    .uploadedBy(upload.getUploadedBy())
                    .build();
            entity = fileRepo.save(entity);
            upload.setStatus("completed");
            chunkUploadRepo.save(upload);
            logActivity("File uploaded", "FILE", upload.getFileName(), upload.getUploadedBy());

            return ChunkUploadResponse.builder()
                    .uploadId(uploadId)
                    .chunksReceived(upload.getChunksReceived())
                    .totalChunks(upload.getTotalChunks())
                    .status("completed")
                    .file(toFileDto(entity))
                    .build();
        }

        chunkUploadRepo.save(upload);
        return ChunkUploadResponse.builder()
                .uploadId(uploadId)
                .chunksReceived(upload.getChunksReceived())
                .totalChunks(upload.getTotalChunks())
                .status("uploading")
                .build();
    }

    // ───── Mappers ─────

    private FolderDto toFolderDto(Folder f) {
        EcmUser owner = userRepo.findById(f.getOwnerId()).orElse(null);
        return FolderDto.builder()
                .id(f.getId()).name(f.getName()).visibility(f.getVisibility())
                .description(f.getDescription()).parentId(f.getParentId()).path(f.getPath())
                .ownerId(f.getOwnerId())
                .ownerName(owner != null ? owner.getDisplayName() : "Unknown")
                .fileCount(fileRepo.countByFolderId(f.getId()))
                .createdAt(f.getCreatedAt()).updatedAt(f.getUpdatedAt())
                .build();
    }

    private FileDto toFileDto(FileEntity f) {
        Folder folder = folderRepo.findById(f.getFolderId()).orElse(null);
        EcmUser uploader = userRepo.findById(f.getUploadedBy()).orElse(null);
        List<FileShare> shares = shareRepo.findByFileId(f.getId());

        return FileDto.builder()
                .id(f.getId()).name(f.getName()).originalName(f.getOriginalName())
                .contentType(f.getContentType()).size(f.getSize())
                .sizeFormatted(formatSize(f.getSize()))
                .folderName(folder != null ? folder.getName() : "Unknown")
                .folderVisibility(folder != null ? folder.getVisibility() : "private")
                .uploadedByName(uploader != null ? uploader.getDisplayName() : "Unknown")
                .status(f.getStatus()).uploadedAt(f.getUploadedAt())
                .shares(shares.stream().map(this::toShareDto).toList())
                .build();
    }

    private FileDto.ShareDto toShareDto(FileShare s) {
        EcmUser sharedWith = s.getSharedWith() != null ? userRepo.findById(s.getSharedWith()).orElse(null) : null;
        return FileDto.ShareDto.builder()
                .id(s.getId()).shareType(s.getShareType())
                .sharedWithName(sharedWith != null ? sharedWith.getDisplayName() : null)
                .permission(s.getPermission()).shareToken(s.getShareToken())
                .expiresAt(s.getExpiresAt()).sharedAt(s.getSharedAt())
                .build();
    }

    private FolderDto.AccessEntryDto toAccessEntry(FolderAccess a) {
        EcmUser user = userRepo.findById(a.getUserId()).orElse(null);
        return FolderDto.AccessEntryDto.builder()
                .id(a.getId()).userId(a.getUserId())
                .userName(user != null ? user.getDisplayName() : "Unknown")
                .userEmail(user != null ? user.getEmail() : "")
                .permission(a.getPermission()).grantedAt(a.getGrantedAt())
                .build();
    }

    private RecentFileDto toRecentFile(FileEntity f) {
        Folder folder = folderRepo.findById(f.getFolderId()).orElse(null);
        EcmUser uploader = userRepo.findById(f.getUploadedBy()).orElse(null);
        return RecentFileDto.builder()
                .id(f.getId()).name(f.getOriginalName()).contentType(f.getContentType()).size(f.getSize())
                .folderName(folder != null ? folder.getName() : "Unknown")
                .folderVisibility(folder != null ? folder.getVisibility() : "private")
                .uploadedByName(uploader != null ? uploader.getDisplayName() : "Unknown")
                .uploadedAt(f.getUploadedAt())
                .build();
    }

    private void logActivity(String action, String entityType, String entityName, Long userId) {
        EcmUser user = userRepo.findById(userId).orElse(null);
        activityRepo.save(ActivityLog.builder()
                .action(action).entityType(entityType).entityName(entityName)
                .userId(userId).userName(user != null ? user.getDisplayName() : "System")
                .build());
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
