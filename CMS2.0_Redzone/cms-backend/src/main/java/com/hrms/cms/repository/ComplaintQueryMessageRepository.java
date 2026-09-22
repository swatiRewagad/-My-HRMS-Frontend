package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintQueryMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComplaintQueryMessageRepository extends JpaRepository<ComplaintQueryMessage, Long> {

    List<ComplaintQueryMessage> findByQueryIdOrderByPostedAtAscIdAsc(Long queryId);

    List<ComplaintQueryMessage> findByQueryIdInOrderByPostedAtAscIdAsc(List<Long> queryIds);

    /** Newest message id in a thread — the yardstick for whether a user's read position is current. */
    @Query("SELECT MAX(m.id) FROM ComplaintQueryMessage m WHERE m.queryId = :queryId")
    Long findMaxIdByQueryId(@Param("queryId") Long queryId);
}
