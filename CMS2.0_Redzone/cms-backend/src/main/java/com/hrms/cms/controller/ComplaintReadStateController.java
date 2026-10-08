package com.hrms.cms.controller;

import com.hrms.cms.entity.ComplaintReadState;
import com.hrms.cms.repository.ComplaintReadStateRepository;
import com.hrms.cms.security.CepcIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Marks a complaint read for the caller.
 *
 * <p><b>Per user, not per browser.</b> The dashboard used to record this in {@code localStorage}, so an
 * officer's read marks vanished when site data was cleared and never followed them to a second machine —
 * and two officers sharing a machine saw each other's.
 *
 * <p>Nothing here writes to {@code COMPLAINTS}, which is what keeps opening a complaint from touching its
 * {@code @Version} and losing a race with a concurrent workflow transition.
 */
@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class ComplaintReadStateController {

    private final ComplaintReadStateRepository readStateRepository;
    private final CepcIdentityResolver identity;

    /**
     * Idempotent: the first call creates the row, later calls only move {@code lastReadAt}. Re-opening a
     * complaint must not flip it back to unread, and the UI calls this on every open.
     */
    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id) {
        String userId = identity.resolveActor();
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(body(false, "complaints.read.error.unidentified_caller"));
        }

        LocalDateTime now = LocalDateTime.now();
        ComplaintReadState state = readStateRepository.findByComplaintIdAndUserId(id, userId)
                .orElseGet(() -> ComplaintReadState.builder()
                        .complaintId(id)
                        .userId(userId)
                        .firstReadAt(now)
                        .build());
        state.setLastReadAt(now);
        readStateRepository.save(state);

        return ResponseEntity.ok(body(true, "OK"));
    }

    private static Map<String, Object> body(boolean success, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", success);
        out.put("message", message);
        out.put("data", Map.of("isRead", success));
        return out;
    }
}
