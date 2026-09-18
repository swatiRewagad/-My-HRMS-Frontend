package com.hrms.cms.repository;

import com.hrms.cms.entity.NotificationEventChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationEventChannelRepository
        extends JpaRepository<NotificationEventChannel, String> {

    Optional<NotificationEventChannel> findByEventTypeAndIsActive(String eventType, String isActive);
}
