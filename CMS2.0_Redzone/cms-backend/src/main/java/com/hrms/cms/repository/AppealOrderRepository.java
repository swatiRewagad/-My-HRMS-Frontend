package com.hrms.cms.repository;

import com.hrms.cms.entity.AppealOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppealOrderRepository extends JpaRepository<AppealOrder, Long> {

    /** Every revision, oldest first: the original order followed by each correction. */
    List<AppealOrder> findByAppealNumberOrderByRevisionNoAscIdAsc(String appealNumber);

    /** The order currently in force -- the one revision not superseded by a correction. */
    @Query("""
            SELECT o FROM AppealOrder o
             WHERE o.appealNumber = :appealNumber
               AND o.supersededAt IS NULL
             ORDER BY o.revisionNo DESC, o.id DESC
            """)
    List<AppealOrder> findOperative(@Param("appealNumber") String appealNumber);

    default Optional<AppealOrder> findOperativeOne(String appealNumber) {
        return findOperative(appealNumber).stream().findFirst();
    }

    @Query("SELECT COALESCE(MAX(o.revisionNo), 0) FROM AppealOrder o WHERE o.appealNumber = :appealNumber")
    int maxRevisionNo(@Param("appealNumber") String appealNumber);

    boolean existsByAppealNumber(String appealNumber);

    void deleteByAppealNumber(String appealNumber);
}
