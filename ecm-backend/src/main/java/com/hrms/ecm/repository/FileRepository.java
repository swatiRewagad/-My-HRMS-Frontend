package com.hrms.ecm.repository;

import com.hrms.ecm.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    List<FileEntity> findByFolderIdOrderByUploadedAtDesc(Long folderId);
    List<FileEntity> findByUploadedByOrderByUploadedAtDesc(Long userId);
    long countByFolderId(Long folderId);

    @Query("SELECT SUM(f.size) FROM FileEntity f")
    Long totalStorageUsed();

    @Query("SELECT f.contentType, COUNT(f) FROM FileEntity f GROUP BY f.contentType")
    List<Object[]> countByContentType();

    List<FileEntity> findTop10ByOrderByUploadedAtDesc();

    @Query("SELECT f FROM FileEntity f WHERE LOWER(f.originalName) LIKE LOWER(CONCAT('%',:search,'%'))")
    List<FileEntity> search(String search);
}
