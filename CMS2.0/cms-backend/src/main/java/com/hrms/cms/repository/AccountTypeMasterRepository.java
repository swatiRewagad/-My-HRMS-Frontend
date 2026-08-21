package com.hrms.cms.repository;

import com.hrms.cms.entity.AccountTypeMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountTypeMasterRepository extends JpaRepository<AccountTypeMaster, Long> {
    List<AccountTypeMaster> findByActiveTrueOrderBySortOrderAsc();
}
