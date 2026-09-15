package com.hrms.cms.controller;

import com.hrms.cms.entity.InAppNotification;
import com.hrms.cms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<InAppNotification>> getNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok(notificationService.getNotifications(userId, page, size));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<InAppNotification>> getUnread(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(notificationService.getUnread(jwt.getSubject()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        long count = notificationService.getUnreadCount(jwt.getSubject());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, Integer>> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        int updated = notificationService.markAllRead(jwt.getSubject());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/mark-read")
    public ResponseEntity<Map<String, Integer>> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody List<Long> ids) {
        int updated = notificationService.markRead(ids, jwt.getSubject());
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
