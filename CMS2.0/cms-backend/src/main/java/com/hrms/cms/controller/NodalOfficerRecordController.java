package com.hrms.cms.controller;

import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/nodal-officer-records")
@RequiredArgsConstructor
public class NodalOfficerRecordController {

    private final NodalOfficerRecordRepository repository;

    @GetMapping
    public List<NodalOfficerRecord> getAll() {
        return repository.findAll();
    }
}
