package com.rbi.cms.mailintake.admin;

import com.rbi.cms.mailintake.admin.dto.AdminActionDto;
import com.rbi.cms.mailintake.admin.dto.DecisionRequest;
import com.rbi.cms.mailintake.admin.dto.ForceLinkRequest;
import com.rbi.cms.mailintake.admin.dto.InboundEmailDetailDto;
import com.rbi.cms.mailintake.admin.dto.InboundEmailSummaryDto;
import com.rbi.cms.mailintake.admin.dto.ReplayRequest;
import com.rbi.cms.mailintake.entity.AdminAction;
import com.rbi.cms.mailintake.entity.InboundEmail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Every mutating endpoint here writes to the audit trail (see AdminMailIntakeService /
 * InboundEmailStateMachine#recordAuditEvent) and requires cms.mail.intake.admin.required-role —
 * see MailIntakeSecurityConfig. Replay and force-link are maker-checker: the POST endpoints only
 * create a pending {@code AdminAction}; nothing on the email itself changes until a *different*
 * operator calls the approve/reject endpoint.
 */
@RestController
@RequestMapping("/admin/mail-intake")
@RequiredArgsConstructor
public class AdminMailIntakeController {

    private final AdminMailIntakeService service;

    @GetMapping("/quarantined")
    public Page<InboundEmailSummaryDto> listQuarantined(@PageableDefault(size = 25) Pageable pageable) {
        return service.listQuarantined(pageable).map(InboundEmailSummaryDto::from);
    }

    @GetMapping("/emails/{id}")
    public InboundEmailDetailDto getEmail(@PathVariable Long id) {
        InboundEmail email = service.getEmailOrThrow(id);
        return InboundEmailDetailDto.from(email, service.getAttachments(id), service.getTimeline(id));
    }

    @GetMapping("/emails/{id}/raw")
    public ResponseEntity<byte[]> downloadRaw(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        byte[] bytes = service.downloadRaw(id, actor(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inbound-email-" + id + ".eml\"")
                .body(bytes);
    }

    @GetMapping("/emails/{id}/actions")
    public List<AdminActionDto> getActionHistory(@PathVariable Long id) {
        return service.getActionHistory(id).stream().map(AdminActionDto::from).toList();
    }

    @PostMapping("/emails/{id}/replay-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminActionDto requestReplay(@PathVariable Long id, @Valid @RequestBody ReplayRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return AdminActionDto.from(service.requestReplay(id, actor(jwt), request.reason()));
    }

    @PostMapping("/emails/{id}/force-link-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminActionDto requestForceLink(@PathVariable Long id, @Valid @RequestBody ForceLinkRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return AdminActionDto.from(service.requestForceLink(id, actor(jwt), request.reason(), request.targetComplaintId()));
    }

    @GetMapping("/actions")
    public List<AdminActionDto> listPendingActions(@PageableDefault(size = 50) Pageable pageable) {
        return service.listPendingActions(pageable).stream().map(AdminActionDto::from).toList();
    }

    @PostMapping("/actions/{id}/approve")
    public AdminActionDto approve(@PathVariable Long id, @Valid @RequestBody DecisionRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return AdminActionDto.from(service.decide(id, actor(jwt), true, request.note()));
    }

    @PostMapping("/actions/{id}/reject")
    public AdminActionDto reject(@PathVariable Long id, @Valid @RequestBody DecisionRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        if (request.note() == null || request.note().isBlank()) {
            throw new IllegalArgumentException("A decision note is required when rejecting an admin action");
        }
        return AdminActionDto.from(service.decide(id, actor(jwt), false, request.note()));
    }

    /** preferred_username is Keycloak's standard human-readable claim; sub (always present on a
     *  valid token) is the fallback so this never silently records a blank actor. */
    private static String actor(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        return preferredUsername != null ? preferredUsername : jwt.getSubject();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AdminMailIntakeService.RawBytesPurgedException.class)
    public ResponseEntity<Map<String, String>> handlePurged(AdminMailIntakeService.RawBytesPurgedException e) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<Map<String, String>> handleIoFailure(UncheckedIOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Could not read raw message bytes"));
    }
}
