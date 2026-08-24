package com.rbi.cms.assignment.service;

import java.util.List;

public interface GroupMemberProvider {

    List<String> getMembers(String groupId);
}
