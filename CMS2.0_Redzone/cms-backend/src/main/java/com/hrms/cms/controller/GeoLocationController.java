package com.hrms.cms.controller;

import com.hrms.cms.service.ComplaintNumberGeneratorService;
import com.hrms.cms.service.GeoLocationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/geo")

public class GeoLocationController {

    private final GeoLocationService geoLocationService;
    private final ComplaintNumberGeneratorService officeResolver;

    public GeoLocationController(GeoLocationService geoLocationService,
                                 ComplaintNumberGeneratorService officeResolver) {
        this.geoLocationService = geoLocationService;
        this.officeResolver = officeResolver;
    }

    @GetMapping("/locate")
    public ResponseEntity<Map<String, Object>> locate(HttpServletRequest request) {
        if (!geoLocationService.isAvailable()) {
            return ResponseEntity.ok(Map.of(
                "available", false,
                "message", "GeoLocation service not configured"
            ));
        }

        String ip = extractClientIp(request);

        return geoLocationService.lookup(ip)
            .map(result -> ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(60)).cachePrivate())
                .body(Map.<String, Object>of(
                    "available", true,
                    "location", result.toMap()
                )))
            .orElse(ResponseEntity.ok(Map.of(
                "available", true,
                "location", Map.of()
            )));
    }

    @GetMapping("/jurisdiction")
    public ResponseEntity<Map<String, Object>> getJurisdiction(HttpServletRequest request) {
        if (!geoLocationService.isAvailable()) {
            return ResponseEntity.ok(Map.of("resolved", false));
        }

        String ip = extractClientIp(request);

        return geoLocationService.lookup(ip)
            .map(result -> {
                // Resolved against OMBUDSMAN_OFFICE_MASTER, the same resolver the complaint-numbering
                // path uses, so this hint cannot contradict the office a filed complaint lands in.
                // The city is passed as the district so split jurisdictions (Mumbai-I/II, Chennai-I/II)
                // resolve rather than collapsing to one name.
                String ombudsmanOffice = officeResolver.resolveOfficeName(
                        "RBIO", result.getState(), result.getCity());
                return ResponseEntity.ok(Map.<String, Object>of(
                    "resolved", true,
                    "state", result.getState() != null ? result.getState() : "",
                    "city", result.getCity() != null ? result.getCity() : "",
                    "ombudsmanOffice", ombudsmanOffice != null ? ombudsmanOffice : ""
                ));
            })
            .orElse(ResponseEntity.ok(Map.of("resolved", false)));
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

}
