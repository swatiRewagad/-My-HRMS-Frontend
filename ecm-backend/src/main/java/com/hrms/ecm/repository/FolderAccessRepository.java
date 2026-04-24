package com.hrms.ecm.repository;

import com.hrms.ecm.entity.FolderAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FolderAccessRepository extends JpaRepository<FolderAccess, Long> {
    List<FolderAccess> findByFolderId(Long folderId);
    List<FolderAccess> findByUserId(Long userId);
    Optional<FolderAccess> findByFolderIdAndUserId(Long folderId, Long userId);
    void deleteByFolderIdAndUserId(Long folderId, Long userId);
}
