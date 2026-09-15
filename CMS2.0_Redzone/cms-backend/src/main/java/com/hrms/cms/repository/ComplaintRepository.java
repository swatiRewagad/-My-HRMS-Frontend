package com.hrms.cms.repository;

import com.hrms.cms.entity.Complaint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    Optional<Complaint> findByComplaintNumber(String complaintNumber);
    List<Complaint> findByStatusOrderByCreatedAtDesc(String status);
    List<Complaint> findByComplainantEmailOrderByCreatedAtDesc(String email);
    List<Complaint> findByComplainantPhoneOrderByCreatedAtDesc(String phone);
    List<Complaint> findByCategoryIdOrderByCreatedAtDesc(Long categoryId);
    List<Complaint> findByBankIdOrderByCreatedAtDesc(Long bankId);
    List<Complaint> findAllByOrderByCreatedAtDesc();

    Page<Complaint> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Complaint> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    @Query("SELECT c FROM Complaint c WHERE LOWER(c.subject) LIKE LOWER(CONCAT('%', :q, '%')) OR c.complaintNumber LIKE CONCAT('%', :q, '%') OR LOWER(c.complainantName) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Complaint> search(@Param("q") String query);

    @Query("SELECT c FROM Complaint c WHERE LOWER(c.subject) LIKE LOWER(CONCAT('%', :q, '%')) OR c.complaintNumber LIKE CONCAT('%', :q, '%') OR LOWER(c.complainantName) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY c.createdAt DESC")
    Page<Complaint> searchPaged(@Param("q") String query, Pageable pageable);

    long countByStatus(String status);
    long countByPriority(String priority);
    long countByDepartment(String department);
    long countByDepartmentAndStatus(String department, String status);

    List<Complaint> findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
            String department, String assignedRole, List<String> excludeStatuses);

    List<Complaint> findByDepartmentAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
            String department, String assignedOfficer, List<String> excludeStatuses);

    List<Complaint> findByDepartmentAndStatusOrderByCreatedAtDesc(String department, String status);

    List<Complaint> findByDepartmentAndStatusNotInOrderByCreatedAtDesc(String department, List<String> excludeStatuses);

    List<Complaint> findByDepartmentAndAssignedOfficerAndStatusOrderByCreatedAtDesc(
            String department, String assignedOfficer, String status);

    List<Complaint> findByStatusAndDepartmentIsNullOrderByCreatedAtDesc(String status);

    List<Complaint> findByDepartmentAndAssignedRoleAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
            String department, String assignedRole, String assignedOfficer, List<String> excludeStatuses);

    List<Complaint> findByDepartmentAndAssignedOfficerOrderByCreatedAtDesc(String department, String assignedOfficer);

    List<Complaint> findByDepartmentOrderByCreatedAtDesc(String department);

    List<Complaint> findByDepartmentAndAssignedRoleOrderByCreatedAtDesc(String department, String assignedRole);

    List<Complaint> findByDepartmentAndStatusInOrderByCreatedAtDesc(String department, List<String> statuses);

    // RE Portal queries
    Page<Complaint> findByEntityCodeOrderByCreatedAtDesc(String entityCode, Pageable pageable);

    Page<Complaint> findByEntityCodeAndStatusOrderByCreatedAtDesc(String entityCode, String status, Pageable pageable);

    // Scheduled notification queries
    List<Complaint> findByStatusAndLastStatusChangeDateBefore(String status, LocalDateTime cutoff);

    List<Complaint> findByStatusNotInAndLastStatusChangeDateBefore(List<String> excludeStatuses, LocalDateTime cutoff);

    @Query("SELECT c FROM Complaint c WHERE c.status NOT IN :closedStatuses AND c.createdAt < :cutoff AND c.department = :department")
    List<Complaint> findOpenComplaintsOlderThan(@Param("closedStatuses") List<String> closedStatuses,
                                                @Param("cutoff") LocalDateTime cutoff,
                                                @Param("department") String department);

    @Query("SELECT c FROM Complaint c WHERE c.reResponseDeadline IS NOT NULL AND c.reResponseDeadline < :today AND c.status NOT IN :closedStatuses")
    List<Complaint> findPastReResponseDeadline(@Param("today") LocalDate today,
                                              @Param("closedStatuses") List<String> closedStatuses);

    // Citizen portal: paginated queries by phone
    Page<Complaint> findByComplainantPhone(String phone, Pageable pageable);

    Page<Complaint> findByComplainantPhoneAndStatus(String phone, String status, Pageable pageable);

    /**
     * Duplicate pre-check for public filing: the same complainant (matched on phone OR email)
     * raising the same category against the same regulated entity while an earlier complaint is
     * still live. Terminal statuses are excluded so a citizen may re-file after closure.
     */
    @Query("""
           SELECT c FROM Complaint c
           WHERE (
                   (:phone IS NOT NULL AND c.complainantPhone = :phone)
                OR (:email IS NOT NULL AND LOWER(c.complainantEmail) = LOWER(:email))
           )
             AND (:bankId IS NULL OR c.bankId = :bankId)
             AND (:categoryId IS NULL OR c.categoryId = :categoryId)
             AND c.status NOT IN :terminalStatuses
             AND c.createdAt >= :since
           ORDER BY c.createdAt DESC
           """)
    List<Complaint> findPotentialDuplicates(@Param("phone") String phone,
                                            @Param("email") String email,
                                            @Param("bankId") Long bankId,
                                            @Param("categoryId") Long categoryId,
                                            @Param("terminalStatuses") List<String> terminalStatuses,
                                            @Param("since") LocalDateTime since);
}
