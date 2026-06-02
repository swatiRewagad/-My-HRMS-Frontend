package com.hrms.cms.controller;

import com.hrms.cms.entity.Bank;
import com.hrms.cms.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/banks")
@RequiredArgsConstructor
public class BankController {

    private final ComplaintService complaintService;

    @GetMapping
    public List<Bank> getAll(@RequestParam(required = false) String type) {
        if (type != null) return complaintService.getBanksByType(type);
        return complaintService.getAllBanks();
    }
}
