package com.hrms.ecm.repository;

import com.hrms.ecm.entity.ChunkUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ChunkUploadRepository extends JpaRepository<ChunkUpload, Long> {
    Optional<ChunkUpload> findByUploadId(String uploadId);
}
