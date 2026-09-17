package com.hrms.cms.repository;

import com.hrms.cms.entity.ClosureClauseMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClosureClauseMasterRepository extends JpaRepository<ClosureClauseMaster, Long> {

    /**
     * The clause in force for a scheme version on a given date.
     *
     * The date bound matters: a complaint must be judged under the clause as it stood when the
     * complaint was created, so that seeding a later Scheme cannot retroactively change how an
     * existing closure classifies.
     */
    @Query("""
            SELECT c FROM ClosureClauseMaster c
             WHERE c.schemeVersion = :schemeVersion
               AND UPPER(c.clauseCode) = UPPER(:clauseCode)
               AND c.active = true
               AND (c.effectiveFrom IS NULL OR c.effectiveFrom <= :onDate)
               AND (c.effectiveTo IS NULL OR c.effectiveTo >= :onDate)
            """)
    Optional<ClosureClauseMaster> findInForce(@Param("schemeVersion") String schemeVersion,
                                             @Param("clauseCode") String clauseCode,
                                             @Param("onDate") LocalDate onDate);

    Optional<ClosureClauseMaster> findBySchemeVersionAndClauseCode(String schemeVersion, String clauseCode);

    List<ClosureClauseMaster> findBySchemeVersionAndActiveTrueOrderByClauseCodeAsc(String schemeVersion);

    boolean existsBySchemeVersionAndClauseCode(String schemeVersion, String clauseCode);
}
