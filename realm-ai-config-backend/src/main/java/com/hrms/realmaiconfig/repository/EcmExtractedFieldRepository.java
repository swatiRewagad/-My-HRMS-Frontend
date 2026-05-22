package com.hrms.realmaiconfig.repository;

import com.hrms.realmaiconfig.entity.EcmExtractedField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EcmExtractedFieldRepository extends JpaRepository<EcmExtractedField, Long> {
    List<EcmExtractedField> findByEcmFileIdOrderByIdAsc(Long ecmFileId);
    void deleteByEcmFileId(Long ecmFileId);
}
