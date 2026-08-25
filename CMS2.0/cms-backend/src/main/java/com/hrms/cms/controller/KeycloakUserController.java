package com.hrms.cms.controller;

import com.hrms.cms.entity.OfficerAvailability;
import com.hrms.cms.repository.OfficerAvailabilityRepository;
import com.hrms.cms.service.ComplaintRoutingService;
import com.hrms.cms.service.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/keycloak")
@RequiredArgsConstructor
public class KeycloakUserController {

    private final KeycloakUserService keycloakUserService;
    private final ComplaintRoutingService complaintRoutingService;
    private final OfficerAvailabilityRepository availabilityRepository;

    @GetMapping("/users/deos")
    public Map<String, Object> getDeos() {
        List<Map<String, Object>> deos = keycloakUserService.getDeos();
        List<Map<String, Object>> enriched = new ArrayList<>();
        int sortOrder = 1;
        for (Map<String, Object> deo : deos) {
            Map<String, Object> enrichedDeo = new LinkedHashMap<>(deo);
            enrichedDeo.put("isActive", Boolean.TRUE.equals(deo.get("enabled")));
            enrichedDeo.put("isOnLeave", false);
            enrichedDeo.put("maxThreshold", 20);
            enrichedDeo.put("currentAssignedCount", 0);
            enrichedDeo.put("sortOrder", sortOrder++);
            enriched.add(enrichedDeo);
        }
        return wrapResponse(enriched);
    }

    @GetMapping("/users/reviewers")
    public Map<String, Object> getReviewers() {
        List<Map<String, Object>> reviewers = keycloakUserService.getReviewers();
        List<Map<String, Object>> enriched = new ArrayList<>();
        int sortOrder = 1;
        for (Map<String, Object> reviewer : reviewers) {
            Map<String, Object> enrichedReviewer = new LinkedHashMap<>(reviewer);
            enrichedReviewer.put("isActive", Boolean.TRUE.equals(reviewer.get("enabled")));
            enrichedReviewer.put("isOnLeave", false);
            enrichedReviewer.put("maxLoad", 25);
            enrichedReviewer.put("currentLoad", 0);
            enrichedReviewer.put("region", "");
            enrichedReviewer.put("sortOrder", sortOrder++);
            enriched.add(enrichedReviewer);
        }
        return wrapResponse(enriched);
    }

    @GetMapping("/users/all")
    public Map<String, Object> getAllCrpcUsers() {
        return wrapResponse(keycloakUserService.getAllCrpcUsers());
    }

    @GetMapping("/users/by-role")
    public List<Map<String, Object>> getUsersByRole(@RequestParam String role) {
        return keycloakUserService.getUsersByRole(role);
    }

    @GetMapping("/users/next-assignee")
    public Map<String, Object> getNextAssignee(
            @RequestParam String role,
            @RequestParam(required = false) String office) {
        List<Map<String, Object>> users = keycloakUserService.getUsersByRole(role);

        // Filter by office if provided
        if (office != null && !office.isBlank()) {
            List<Map<String, Object>> officeUsers = users.stream()
                    .filter(u -> office.equals(u.get("officeCode")))
                    .collect(java.util.stream.Collectors.toList());
            if (!officeUsers.isEmpty()) {
                users = officeUsers;
            }
        }

        if (users.isEmpty()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("message", "No users found for role: " + role);
            response.put("data", null);
            return response;
        }

        String assignedUserId = complaintRoutingService.assignOfficerByRole(role);

        Map<String, Object> assignedUser = users.stream()
                .filter(u -> assignedUserId.equals(u.get("userId")))
                .findFirst()
                .orElse(users.get(0));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", assignedUser.get("userId"));
        data.put("username", assignedUser.get("userId"));
        data.put("displayName", assignedUser.getOrDefault("displayName",
                ((String) assignedUser.getOrDefault("firstName", "")) + " " +
                ((String) assignedUser.getOrDefault("lastName", ""))).toString().trim());
        data.put("officeCode", assignedUser.getOrDefault("officeCode", ""));
        data.put("assignmentMethod", "ROUND_ROBIN");
        data.put("totalPoolSize", users.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Next assignee determined via round-robin");
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    @GetMapping("/users/availability")
    public Map<String, Object> getOfficerAvailability(@RequestParam String role) {
        List<Map<String, Object>> users = keycloakUserService.getUsersByRole(role);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> user : users) {
            String userId = (String) user.get("userId");
            Optional<OfficerAvailability> avail = availabilityRepository.findByUserIdAndRole(userId, role);

            Map<String, Object> entry = new LinkedHashMap<>(user);
            if (avail.isPresent()) {
                OfficerAvailability oa = avail.get();
                entry.put("isActive", oa.isActive());
                entry.put("isOnLeave", oa.isOnLeave());
                entry.put("leaveStartDate", oa.getLeaveStartDate());
                entry.put("leaveEndDate", oa.getLeaveEndDate());
                entry.put("leaveReason", oa.getLeaveReason());
                entry.put("currentWorkload", oa.getCurrentWorkload());
                entry.put("maxWorkload", oa.getMaxWorkload());
                entry.put("officeCode", oa.getOfficeCode());
                entry.put("available", oa.isAvailable());
            } else {
                entry.put("isActive", true);
                entry.put("isOnLeave", false);
                entry.put("currentWorkload", 0);
                entry.put("maxWorkload", 20);
                entry.put("officeCode", "");
                entry.put("available", true);
            }
            result.add(entry);
        }

        return wrapResponse(result);
    }

    @PutMapping("/users/{userId}/availability")
    public Map<String, Object> updateOfficerAvailability(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {

        String role = (String) request.getOrDefault("role", "RBIO_OFFICER");
        OfficerAvailability avail = availabilityRepository.findByUserIdAndRole(userId, role)
                .orElseGet(() -> OfficerAvailability.builder()
                        .userId(userId).role(role).active(true).onLeave(false)
                        .currentWorkload(0).maxWorkload(20).build());

        if (request.containsKey("active")) {
            avail.setActive(Boolean.TRUE.equals(request.get("active")));
        }
        if (request.containsKey("onLeave")) {
            avail.setOnLeave(Boolean.TRUE.equals(request.get("onLeave")));
        }
        if (request.containsKey("leaveStartDate")) {
            avail.setLeaveStartDate(LocalDate.parse((String) request.get("leaveStartDate")));
        }
        if (request.containsKey("leaveEndDate")) {
            avail.setLeaveEndDate(LocalDate.parse((String) request.get("leaveEndDate")));
        }
        if (request.containsKey("leaveReason")) {
            avail.setLeaveReason((String) request.get("leaveReason"));
        }
        if (request.containsKey("maxWorkload")) {
            avail.setMaxWorkload((Integer) request.get("maxWorkload"));
        }
        if (request.containsKey("officeCode")) {
            avail.setOfficeCode((String) request.get("officeCode"));
        }

        availabilityRepository.save(avail);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("role", role);
        data.put("active", avail.isActive());
        data.put("onLeave", avail.isOnLeave());
        data.put("available", avail.isAvailable());
        data.put("currentWorkload", avail.getCurrentWorkload());
        data.put("maxWorkload", avail.getMaxWorkload());
        data.put("officeCode", avail.getOfficeCode());

        return wrapResponse(data);
    }

    private Map<String, Object> wrapResponse(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", data);
        response.put("correlationId", UUID.randomUUID().toString());
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
}
