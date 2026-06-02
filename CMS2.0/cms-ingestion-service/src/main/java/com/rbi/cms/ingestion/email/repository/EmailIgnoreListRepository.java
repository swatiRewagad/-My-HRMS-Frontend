package com.rbi.cms.ingestion.email.repository;

import com.rbi.cms.ingestion.email.entity.EmailIgnoreList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailIgnoreListRepository extends JpaRepository<EmailIgnoreList, Long> {

    Optional<EmailIgnoreList> findByEmailPattern(String emailPattern);

    List<EmailIgnoreList> findByIsActiveTrue();

    @Query("SELECT e FROM EmailIgnoreList e WHERE e.isActive = true AND (e.emailPattern = :email OR (:domain IS NOT NULL AND e.emailPattern = :domain))")
    List<EmailIgnoreList> findMatchingPatterns(String email, String domain);
}
