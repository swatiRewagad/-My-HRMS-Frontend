package com.hrms.cms.repository;

import com.hrms.cms.entity.DraftIdSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DraftIdSequenceRepository extends JpaRepository<DraftIdSequence, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DraftIdSequence s WHERE s.id = :id")
    Optional<DraftIdSequence> findByIdForUpdate(@Param("id") Integer id);
}
