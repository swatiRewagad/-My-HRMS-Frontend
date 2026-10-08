package com.hrms.cms.repository;

import com.hrms.cms.entity.AccountTypeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountTypeMasterRepository extends JpaRepository<AccountTypeMaster, Long> {
}
