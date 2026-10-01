package com.hrms.cms.repository;

import com.hrms.cms.entity.AppealOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppealOutboxEventRepository extends JpaRepository<AppealOutboxEvent, Long> {

    List<AppealOutboxEvent> findByAggregateIdOrderByEventIdAsc(String aggregateId);

    List<AppealOutboxEvent> findByAggregateTypeAndStatusOrderByEventIdAsc(String aggregateType,
                                                                          String status);

    long countByAggregateIdAndEventType(String aggregateId, String eventType);
}
