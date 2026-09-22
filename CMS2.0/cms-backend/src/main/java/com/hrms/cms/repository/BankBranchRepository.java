package com.hrms.cms.repository;

import com.hrms.cms.entity.BankBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BankBranchRepository extends JpaRepository<BankBranch, Long> {

    List<BankBranch> findByBankIdAndPincode(Long bankId, String pincode);

    List<BankBranch> findByBankCodeIgnoreCaseAndPincode(String bankCode, String pincode);
}
