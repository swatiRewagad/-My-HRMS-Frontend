package com.hrms.cms.controller;

import com.hrms.cms.entity.WfOfficerPool;
import com.hrms.cms.repository.WfOfficerPoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/officers")
@RequiredArgsConstructor
public class OfficerPoolController {

    private final WfOfficerPoolRepository officerPoolRepository;

    @GetMapping("/{userId}")
    public ResponseEntity<WfOfficerPool> getByUserId(@PathVariable String userId) {
        return officerPoolRepository.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
