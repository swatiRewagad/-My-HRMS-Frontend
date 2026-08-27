package com.rbi.cms.assignment.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/assignment")
public class HealthCheckController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok", "service", "cms-assignment-service", "engine", "decision-table");
    }
}
