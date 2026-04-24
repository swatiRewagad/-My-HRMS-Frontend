package com.hrms.realmaiconfig.repository;

import com.hrms.realmaiconfig.entity.RegisteredService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegisteredServiceRepository extends JpaRepository<RegisteredService, Long> {
    List<RegisteredService> findByStatus(String status);
    long countByStatus(String status);
    long countByCategory(String category);

    @Query("SELECT s FROM RegisteredService s WHERE " +
           "(:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%',:search,'%')) " +
           "OR LOWER(s.slug) LIKE LOWER(CONCAT('%',:search,'%')) " +
           "OR LOWER(s.baseUrl) LIKE LOWER(CONCAT('%',:search,'%'))) " +
           "AND (:category IS NULL OR s.category = :category) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "ORDER BY s.registeredAt DESC")
    List<RegisteredService> search(@Param("search") String search,
                                   @Param("category") String category,
                                   @Param("status") String status);
}
