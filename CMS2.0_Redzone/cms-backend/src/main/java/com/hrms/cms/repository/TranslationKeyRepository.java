package com.hrms.cms.repository;

import com.hrms.cms.entity.TranslationKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TranslationKeyRepository extends JpaRepository<TranslationKey, Long> {

    Optional<TranslationKey> findByCode(String code);

    List<TranslationKey> findByModule(String module);

    boolean existsByCode(String code);
}
