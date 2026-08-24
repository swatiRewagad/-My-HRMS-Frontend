package com.rbi.cms.assignment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stub group-member provider.
 * Replace with integration to user/org-unit management service.
 */
@Slf4j
@Component
public class DefaultGroupMemberProvider implements GroupMemberProvider {

    @Override
    public List<String> getMembers(String groupId) {
        log.warn("Using stub GroupMemberProvider for group '{}' — configure a real provider", groupId);
        return List.of();
    }
}
