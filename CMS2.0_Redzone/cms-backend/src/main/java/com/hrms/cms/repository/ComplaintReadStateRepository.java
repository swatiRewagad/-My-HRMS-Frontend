package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComplaintReadStateRepository extends JpaRepository<ComplaintReadState, Long> {

    Optional<ComplaintReadState> findByComplaintIdAndUserId(Long complaintId, String userId);

    /**
     * The subset of the given complaints this user has read.
     *
     * <p>Returns ids rather than entities: the grid needs only set membership, and one page can ask about
     * 200 complaints at once.
     */
    @Query("select r.complaintId from ComplaintReadState r "
            + "where r.userId = :userId and r.complaintId in :complaintIds")
    List<Long> findReadComplaintIds(@Param("userId") String userId,
                                    @Param("complaintIds") Collection<Long> complaintIds);
}
