package com.hrms.cms.controller;

import com.hrms.cms.dto.DashboardResponse;
import com.hrms.cms.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ComplaintService complaintService;

    @GetMapping
    public DashboardResponse getDashboard() {
        return complaintService.getDashboard();
    }
}
