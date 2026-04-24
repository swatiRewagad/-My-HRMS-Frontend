package com.hrms.ecm.repository;

import com.hrms.ecm.entity.FileShare;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FileShareRepository extends JpaRepository<FileShare, Long> {
    List<FileShare> findByFileId(Long fileId);
    List<FileShare> findBySharedWith(Long userId);
    List<FileShare> findBySharedBy(Long userId);
    Optional<FileShare> findByShareToken(String token);
    long countBySharedBy(Long userId);
}
