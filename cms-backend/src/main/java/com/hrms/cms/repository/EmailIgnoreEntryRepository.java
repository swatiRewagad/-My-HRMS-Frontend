package com.hrms.cms.repository;

import com.hrms.cms.entity.EmailIgnoreEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailIgnoreEntryRepository extends JpaRepository<EmailIgnoreEntry, Long> {
    List<EmailIgnoreEntry> findByIsActiveTrueOrderByCreatedAtDesc();
    boolean existsByEmailPatternIgnoreCase(String emailPattern);
}
