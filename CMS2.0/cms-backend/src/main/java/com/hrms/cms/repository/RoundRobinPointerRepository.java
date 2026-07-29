package com.hrms.cms.repository;

import com.hrms.cms.entity.RoundRobinPointer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoundRobinPointerRepository extends JpaRepository<RoundRobinPointer, Long> {
    Optional<RoundRobinPointer> findByPoolKey(String poolKey);
}
