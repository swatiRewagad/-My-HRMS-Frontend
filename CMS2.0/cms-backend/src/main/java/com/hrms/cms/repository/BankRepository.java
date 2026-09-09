package com.hrms.cms.repository;

import com.hrms.cms.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface BankRepository extends JpaRepository<Bank, Long> {
    List<Bank> findByStatus(String status);
    List<Bank> findByType(String type);

    // Entity-name-selection screens (e.g. regulated_entities) often carry legal-suffix variants
    // of the same bank ("ICICI Bank Limited" vs. our "ICICI Bank") — a one-directional
    // Containing check misses whichever variant is longer than our stored name, so match in
    // both directions instead.
    @Query("SELECT b FROM Bank b WHERE LOWER(:entityName) LIKE LOWER(CONCAT('%', b.name, '%')) " +
           "OR LOWER(b.name) LIKE LOWER(CONCAT('%', :entityName, '%'))")
    List<Bank> findByNameFuzzyMatch(@Param("entityName") String entityName);
}
