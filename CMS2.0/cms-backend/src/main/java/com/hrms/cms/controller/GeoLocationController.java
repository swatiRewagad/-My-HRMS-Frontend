package com.hrms.cms.controller;

import com.hrms.cms.service.GeoLocationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/geo")
@CrossOrigin(origins = "*")
public class GeoLocationController {

    private final GeoLocationService geoLocationService;

    public GeoLocationController(GeoLocationService geoLocationService) {
        this.geoLocationService = geoLocationService;
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
                String ombudsmanOffice = mapStateToOmbudsmanOffice(result.getState());
                return ResponseEntity.ok(Map.<String, Object>of(
                    "resolved", true,
                    "state", result.getState() != null ? result.getState() : "",
                    "city", result.getCity() != null ? result.getCity() : "",
                    "ombudsmanOffice", ombudsmanOffice
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

    private String mapStateToOmbudsmanOffice(String state) {
        if (state == null) return "Central Office";
        return switch (state) {
            case "Maharashtra", "Goa" -> "Mumbai";
            case "Delhi", "Haryana", "Jammu and Kashmir", "Ladakh" -> "New Delhi";
            case "Karnataka" -> "Bengaluru";
            case "Tamil Nadu", "Puducherry" -> "Chennai";
            case "West Bengal", "Sikkim", "Andaman and Nicobar Islands" -> "Kolkata";
            case "Telangana", "Andhra Pradesh" -> "Hyderabad";
            case "Gujarat", "Dadra and Nagar Haveli and Daman and Diu" -> "Ahmedabad";
            case "Rajasthan" -> "Jaipur";
            case "Madhya Pradesh", "Chhattisgarh" -> "Bhopal";
            case "Uttar Pradesh", "Uttarakhand" -> "Kanpur";
            case "Bihar", "Jharkhand" -> "Patna";
            case "Punjab", "Chandigarh", "Himachal Pradesh" -> "Chandigarh";
            case "Kerala", "Lakshadweep" -> "Thiruvananthapuram";
            case "Odisha" -> "Bhubaneswar";
            case "Assam", "Meghalaya", "Arunachal Pradesh", "Nagaland", "Manipur", "Mizoram", "Tripura" -> "Guwahati";
            default -> "Central Office";
        };
    }
}
