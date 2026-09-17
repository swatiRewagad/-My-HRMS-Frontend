package com.hrms.cms.controller;

import com.hrms.cms.entity.InAppNotification;
import com.hrms.cms.security.AaIdentityResolver;
import com.hrms.cms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AaIdentityResolver identityResolver;

    /**
     * The acting user, as {@code preferred_username}.
     *
     * WHY THIS CHANGED: every method here used to read {@code jwt.getSubject()}, which is the Keycloak
     * UUID. Producers write {@code IN_APP_NOTIFICATIONS.TARGET_USER_ID} as a business username
     * ({@code aa_do_001}, {@code deo_001}, {@code CRPC_HEAD}, and {@code KeycloakUserService.mapUser}
     * publishes the Keycloak <em>username</em> as its {@code userId}), so the UUID matched no row and
     * these five endpoints returned an empty list and a zero count to every caller. The websocket
     * principal is derived the same way, so the live push and the REST re-read now agree — otherwise a
     * push would arrive and the follow-up fetch would still show nothing.
     *
     * Reuses {@link AaIdentityResolver#resolveActor()} rather than re-deriving: it already resolves
     * {@code preferred_username} → {@code sub} → {@code X-User-Id}, with the header honoured only while
     * {@code cms.security.allow-dev-identity-headers} is true. That also makes these endpoints usable
     * under dev-local, where there is no token at all and {@code @AuthenticationPrincipal Jwt} was null.
     *
     * Despite the class name, only {@code resolveAaRole()} is AA-specific; {@code resolveActor()} is
     * the generic identity accessor.
     */
    private String currentUserId() {
        String userId = identityResolver.resolveActor();
        if (userId == null) {
            // Fail closed. Defaulting would hand the caller some other user's notifications.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identity could not be established");
        }
        return userId;
    }

    @GetMapping
    public ResponseEntity<Page<InAppNotification>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getNotifications(currentUserId(), page, size));
    }

    @GetMapping("/unread")
    public ResponseEntity<List<InAppNotification>> getUnread() {
        return ResponseEntity.ok(notificationService.getUnread(currentUserId()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        long count = notificationService.getUnreadCount(currentUserId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        int updated = notificationService.markAllRead(currentUserId());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/mark-read")
    public ResponseEntity<Map<String, Integer>> markRead(@RequestBody List<Long> ids) {
        int updated = notificationService.markRead(ids, currentUserId());
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
