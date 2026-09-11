package com.hrms.cms.service;

import com.hrms.cms.entity.RoleStatusMapping;
import com.hrms.cms.repository.RoleStatusMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleStatusMappingService {

    private final RoleStatusMappingRepository repository;

    public List<RoleStatusMapping> getStatusesByRole(String roleName) {
        return repository.findByRoleNameOrderBySequenceAsc(roleName);
    }
}
