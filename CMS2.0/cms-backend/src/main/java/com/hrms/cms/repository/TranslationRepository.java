package com.hrms.cms.repository;

import com.hrms.cms.entity.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, Long> {

    @Query("SELECT t FROM Translation t JOIN FETCH t.translationKey WHERE t.locale = :locale")
    List<Translation> findAllByLocale(@Param("locale") String locale);

    @Query("SELECT t FROM Translation t JOIN FETCH t.translationKey tk WHERE t.locale = :locale AND tk.module = :module")
    List<Translation> findByLocaleAndModule(@Param("locale") String locale, @Param("module") String module);

    @Query("SELECT t FROM Translation t WHERE t.translationKey.id = :keyId AND t.locale = :locale")
    List<Translation> findByKeyIdAndLocale(@Param("keyId") Long keyId, @Param("locale") String locale);
}
