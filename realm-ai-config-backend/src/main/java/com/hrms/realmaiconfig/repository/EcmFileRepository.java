package com.hrms.realmaiconfig.repository;

import com.hrms.realmaiconfig.entity.EcmFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EcmFileRepository extends JpaRepository<EcmFile, Long> {
    List<EcmFile> findByRealmIdAndFolderPathOrderByUploadedAtDesc(String realmId, String folderPath);
    List<EcmFile> findByRealmIdOrderByUploadedAtDesc(String realmId);
}
