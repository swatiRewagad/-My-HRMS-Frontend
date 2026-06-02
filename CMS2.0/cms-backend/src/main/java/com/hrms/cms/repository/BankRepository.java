package com.hrms.cms.repository;

import com.hrms.cms.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BankRepository extends JpaRepository<Bank, Long> {
    List<Bank> findByStatus(String status);
    List<Bank> findByType(String type);
}
