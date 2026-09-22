package com.hrms.cms.repository;

import com.hrms.cms.entity.AaAssignmentAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AaAssignmentAuditRepository extends JpaRepository<AaAssignmentAudit, Long> {

    List<AaAssignmentAudit> findBySubjectUserIdOrderByPerformedAtDescIdDesc(String subjectUserId);

    /** Id is the tie-break because performed_at is not precise enough to order same-second rows. */
    List<AaAssignmentAudit> findByActionOrderByPerformedAtDescIdDesc(String action);

    List<AaAssignmentAudit> findByAppealNumberOrderByPerformedAtDescIdDesc(String appealNumber);
}
