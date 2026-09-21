package com.hrms.cms.controller;

import com.hrms.cms.dto.keycloak.AvailabilityUpdateResponse;
import com.hrms.cms.dto.keycloak.DeoUserResponse;
import com.hrms.cms.dto.keycloak.KeycloakUserResponse;
import com.hrms.cms.dto.keycloak.NextAssigneeResponse;
import com.hrms.cms.dto.keycloak.OfficeResponse;
import com.hrms.cms.dto.keycloak.OfficerAvailabilityResponse;
import com.hrms.cms.dto.keycloak.ReviewerUserResponse;
import com.hrms.cms.entity.OfficeCodeMaster;
import com.hrms.cms.entity.OfficerAvailability;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import com.hrms.cms.repository.OfficerAvailabilityRepository;
import com.hrms.cms.service.ComplaintRoutingService;
import com.hrms.cms.service.KeycloakUserService;
import com.rbi.cms.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/keycloak")
@RequiredArgsConstructor
public class KeycloakUserController {

    private static final int DEFAULT_DEO_THRESHOLD = 20;
    private static final int DEFAULT_REVIEWER_LOAD = 25;
    private static final int DEFAULT_MAX_WORKLOAD = 20;

    private final KeycloakUserService keycloakUserService;
    private final ComplaintRoutingService complaintRoutingService;
    private final OfficerAvailabilityRepository availabilityRepository;
    private final OfficeCodeMasterRepository officeCodeMasterRepository;

    @GetMapping("/offices")
    public ResponseEntity<ApiResponse<List<OfficeResponse>>> getOffices() {
        return wrapResponse(officeCodeMasterRepository.findAll().stream()
                .filter(OfficeCodeMaster::getIsActive)
                .map(o -> OfficeResponse.builder()
                        .officeCode(o.getOfficeCode())
                        .officeName(o.getOfficeName())
                        .officeType(o.getOfficeType())
                        .build())
                .toList());
    }

    /**
     * Flat {@code username -> displayName} map for the whole realm. Names are not sensitive — they are
     * already shown on every complaint.
     *
     * <p>The only consumer is {@code cms-search-service}, which denormalizes these names into its index.
     * That service reads the payload out of the envelope, so the two must be released together: it is
     * built to degrade quietly on failure, and a one-sided deployment would silently replace officer
     * display names with usernames rather than erroring.
     */
    @GetMapping("/users/directory")
    public ResponseEntity<ApiResponse<Map<String, String>>> getUserDirectory() {
        return wrapResponse(keycloakUserService.getUserDirectory());
    }

    @GetMapping("/users/deos")
    public ResponseEntity<ApiResponse<List<DeoUserResponse>>> getDeos() {
        List<DeoUserResponse> deos = new ArrayList<>();
        int sortOrder = 1;
        for (Map<String, Object> deo : keycloakUserService.getDeos()) {
            deos.add(DeoUserResponse.builder()
                    .userId(str(deo, "userId"))
                    .id(str(deo, "id"))
                    .displayName(str(deo, "displayName"))
                    .email(str(deo, "email"))
                    .firstName(str(deo, "firstName"))
                    .lastName(str(deo, "lastName"))
                    .enabled(bool(deo, "enabled"))
                    .officeCode(str(deo, "officeCode"))
                    .isActive(Boolean.TRUE.equals(deo.get("enabled")))
                    .isOnLeave(false)
                    .maxThreshold(DEFAULT_DEO_THRESHOLD)
                    .currentAssignedCount(0)
                    .sortOrder(sortOrder++)
                    .build());
        }
        return wrapResponse(deos);
    }

    @GetMapping("/users/reviewers")
    public ResponseEntity<ApiResponse<List<ReviewerUserResponse>>> getReviewers() {
        List<ReviewerUserResponse> reviewers = new ArrayList<>();
        int sortOrder = 1;
        for (Map<String, Object> reviewer : keycloakUserService.getReviewers()) {
            reviewers.add(ReviewerUserResponse.builder()
                    .userId(str(reviewer, "userId"))
                    .id(str(reviewer, "id"))
                    .displayName(str(reviewer, "displayName"))
                    .email(str(reviewer, "email"))
                    .firstName(str(reviewer, "firstName"))
                    .lastName(str(reviewer, "lastName"))
                    .enabled(bool(reviewer, "enabled"))
                    .officeCode(str(reviewer, "officeCode"))
                    .isActive(Boolean.TRUE.equals(reviewer.get("enabled")))
                    .isOnLeave(false)
                    .maxLoad(DEFAULT_REVIEWER_LOAD)
                    .currentLoad(0)
                    .region("")
                    .sortOrder(sortOrder++)
                    .build());
        }
        return wrapResponse(reviewers);
    }

    @GetMapping("/users/all")
    public ResponseEntity<ApiResponse<List<KeycloakUserResponse>>> getAllCrpcUsers() {
        return wrapResponse(keycloakUserService.getAllCrpcUsers().stream()
                .map(KeycloakUserController::toUser)
                .toList());
    }

    @GetMapping("/users/by-role")
    public ResponseEntity<ApiResponse<List<KeycloakUserResponse>>> getUsersByRole(@RequestParam String role) {
        return wrapResponse(keycloakUserService.getUsersByRole(role).stream()
                .map(KeycloakUserController::toUser)
                .toList());
    }

    @GetMapping("/users/next-assignee")
    public ResponseEntity<ApiResponse<NextAssigneeResponse>> getNextAssignee(
            @RequestParam String role,
            @RequestParam(required = false) String office) {
        List<Map<String, Object>> users = keycloakUserService.getUsersByRole(role);

        // Enrich with OfficerAvailability (officeCode, active/on-leave) — the Keycloak
        // roles/{role}/users API returns bare user objects with no attributes, so officeCode
        // must come from the same Team-Management-managed table getOfficerAvailability() uses.
        for (Map<String, Object> u : users) {
            String userId = (String) u.get("userId");
            availabilityRepository.findByUserIdAndRole(userId, role).ifPresent(oa -> {
                u.put("officeCode", oa.getOfficeCode());
                u.put("active", oa.isActive());
                u.put("onLeave", oa.isOnLeave());
            });
        }

        // Filter by office if provided
        if (office != null && !office.isBlank()) {
            List<Map<String, Object>> officeUsers = users.stream()
                    .filter(u -> office.equals(u.get("officeCode")))
                    .toList();
            if (!officeUsers.isEmpty()) {
                users = officeUsers;
            }
        }

        if (users.isEmpty()) {
            // Was an HTTP 200 carrying success:false and no timestamp, so this looked like a
            // successful call returning nobody.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("No users found for role: " + role));
        }

        String assignedUserId = complaintRoutingService.assignOfficerByRole(role);

        Map<String, Object> assignedUser = users.stream()
                .filter(u -> assignedUserId.equals(u.get("userId")))
                .findFirst()
                .orElse(users.get(0));

        String displayName = assignedUser.getOrDefault("displayName",
                assignedUser.getOrDefault("firstName", "") + " "
                        + assignedUser.getOrDefault("lastName", "")).toString().trim();

        return wrapResponse(NextAssigneeResponse.builder()
                .userId(str(assignedUser, "userId"))
                .username(str(assignedUser, "userId"))
                .displayName(displayName)
                .officeCode(assignedUser.getOrDefault("officeCode", "").toString())
                .assignmentMethod("ROUND_ROBIN")
                .totalPoolSize(users.size())
                .build());
    }

    @GetMapping("/users/availability")
    public ResponseEntity<ApiResponse<List<OfficerAvailabilityResponse>>> getOfficerAvailability(
            @RequestParam String role) {
        List<OfficerAvailabilityResponse> result = new ArrayList<>();

        for (Map<String, Object> user : keycloakUserService.getUsersByRole(role)) {
            String userId = (String) user.get("userId");
            Optional<OfficerAvailability> avail = availabilityRepository.findByUserIdAndRole(userId, role);

            // officeCode is deliberately not copied from the Keycloak user here: the availability row is
            // the authority for it, and Team Management is what edits that row.
            OfficerAvailabilityResponse.OfficerAvailabilityResponseBuilder entry =
                    OfficerAvailabilityResponse.builder()
                            .userId(userId)
                            .id(str(user, "id"))
                            .displayName(str(user, "displayName"))
                            .email(str(user, "email"))
                            .firstName(str(user, "firstName"))
                            .lastName(str(user, "lastName"))
                            .enabled(bool(user, "enabled"));

            if (avail.isPresent()) {
                OfficerAvailability oa = avail.get();
                entry.isActive(oa.isActive())
                        .isOnLeave(oa.isOnLeave())
                        .leaveStartDate(oa.getLeaveStartDate())
                        .leaveEndDate(oa.getLeaveEndDate())
                        .leaveReason(oa.getLeaveReason())
                        .currentWorkload(oa.getCurrentWorkload())
                        .maxWorkload(oa.getMaxWorkload())
                        .officeCode(oa.getOfficeCode())
                        .available(oa.isAvailable());
            } else {
                entry.isActive(true)
                        .isOnLeave(false)
                        .currentWorkload(0)
                        .maxWorkload(DEFAULT_MAX_WORKLOAD)
                        .officeCode("")
                        .available(true);
            }
            result.add(entry.build());
        }

        return wrapResponse(result);
    }

    @PutMapping("/users/{userId}/availability")
    public ResponseEntity<ApiResponse<AvailabilityUpdateResponse>> updateOfficerAvailability(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {

        String role = (String) request.getOrDefault("role", "RBIO_OFFICER");
        OfficerAvailability avail = availabilityRepository.findByUserIdAndRole(userId, role)
                .orElseGet(() -> OfficerAvailability.builder()
                        .userId(userId).role(role).active(true).onLeave(false)
                        .currentWorkload(0).maxWorkload(DEFAULT_MAX_WORKLOAD).build());

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

        return wrapResponse(AvailabilityUpdateResponse.builder()
                .userId(userId)
                .role(role)
                .isActive(avail.isActive())
                .isOnLeave(avail.isOnLeave())
                .available(avail.isAvailable())
                .currentWorkload(avail.getCurrentWorkload())
                .maxWorkload(avail.getMaxWorkload())
                .officeCode(avail.getOfficeCode())
                .build());
    }

    private static KeycloakUserResponse toUser(Map<String, Object> u) {
        return KeycloakUserResponse.builder()
                .userId(str(u, "userId"))
                .id(str(u, "id"))
                .displayName(str(u, "displayName"))
                .email(str(u, "email"))
                .firstName(str(u, "firstName"))
                .lastName(str(u, "lastName"))
                .enabled(bool(u, "enabled"))
                .officeCode(str(u, "officeCode"))
                .build();
    }

    private static String str(Map<String, Object> user, String key) {
        Object value = user.get(key);
        return value == null ? null : value.toString();
    }

    private static Boolean bool(Map<String, Object> user, String key) {
        return user.get(key) instanceof Boolean value ? value : null;
    }

    /**
     * Every success from this controller carried the message "OK" back when the envelope was assembled
     * by hand here, so it is preserved rather than replaced with something per-endpoint.
     */
    private static <T> ResponseEntity<ApiResponse<T>> wrapResponse(T data) {
        return ResponseEntity.ok(ApiResponse.success(data, "OK"));
    }
}
