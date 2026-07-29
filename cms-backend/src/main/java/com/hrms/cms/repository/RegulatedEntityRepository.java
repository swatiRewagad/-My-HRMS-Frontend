package com.hrms.cms.repository;

import com.hrms.cms.entity.RegulatedEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RegulatedEntityRepository extends JpaRepository<RegulatedEntity, Long> {

    List<RegulatedEntity> findByDepartment(String department);

    Optional<RegulatedEntity> findByNameNormalized(String nameNormalized);

    @Query("SELECT e FROM RegulatedEntity e WHERE e.nameNormalized LIKE %:term%")
    List<RegulatedEntity> searchByNormalizedName(@Param("term") String term);

    @Query("SELECT e FROM RegulatedEntity e WHERE e.nameNormalized = :exact OR e.nameNormalized LIKE %:partial% ORDER BY CASE WHEN e.nameNormalized = :exact THEN 0 ELSE 1 END")
    List<RegulatedEntity> findBestMatch(@Param("exact") String exact, @Param("partial") String partial);

    long countByDepartment(String department);

    boolean existsByNameNormalizedContainingIgnoreCase(String term);

    boolean existsByNameContainingIgnoreCase(String term);
}
