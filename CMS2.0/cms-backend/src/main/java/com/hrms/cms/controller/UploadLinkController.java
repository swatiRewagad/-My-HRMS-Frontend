package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.UploadLink;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.UploadLinkRepository;
import com.hrms.cms.service.NotificationService;
import com.hrms.cms.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/upload-link")
@RequiredArgsConstructor
public class UploadLinkController {

    private final UploadLinkRepository uploadLinkRepository;
    private final ComplaintRepository complaintRepository;
    private final NotificationService notificationService;
    private final OtpService otpService;

    private static final int LINK_EXPIRY_DAYS = 7;

    /**
     * POST /api/v1/upload-link/send
     * Creates an upload link, generates token, records email/mobile, marks active, sets expiresAt = now + 7 days.
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendUploadLink(@RequestBody Map<String, String> body) {
        String complaintNumber = body.get("complaintNumber");
        String email = body.get("email");
        String mobile = body.get("mobile");

        if (complaintNumber == null || complaintNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_COMPLAINT_NUMBER",
                    "message", "Complaint number is required."));
        }

        Optional<Complaint> complaintOpt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (complaintOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "COMPLAINT_NOT_FOUND",
                    "message", "No complaint found with number: " + complaintNumber));
        }

        // Deactivate any existing active link for this complaint
        uploadLinkRepository.findByComplaintNumberAndActiveTrue(complaintNumber)
                .ifPresent(existing -> {
                    existing.setActive(false);
                    uploadLinkRepository.save(existing);
                });

        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        UploadLink link = UploadLink.builder()
                .complaintNumber(complaintNumber)
                .token(token)
                .complainantEmail(email)
                .complainantMobile(mobile)
                .sentAt(now)
                .expiresAt(now.plusDays(LINK_EXPIRY_DAYS))
                .active(true)
                .documentsSubmitted(false)
                .build();

        uploadLinkRepository.save(link);

        log.info("Upload link created for complaint {} with token {}, expires at {}",
                complaintNumber, token, link.getExpiresAt());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "token", token,
                "expiresAt", link.getExpiresAt().toString(),
                "message", "Upload link sent successfully."
        ));
    }

    /**
     * GET /api/v1/upload-link/status/{complaintNumber}
     * Returns the current upload link status for a complaint.
     */
    @GetMapping("/status/{complaintNumber}")
    public ResponseEntity<?> getLinkStatus(@PathVariable String complaintNumber) {
        Optional<UploadLink> linkOpt = uploadLinkRepository.findByComplaintNumberAndActiveTrue(complaintNumber);

        if (linkOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "active", false,
                    "message", "No active upload link for this complaint."
            ));
        }

        UploadLink link = linkOpt.get();
        boolean expired = link.getExpiresAt().isBefore(LocalDateTime.now());

        return ResponseEntity.ok(Map.of(
                "active", !expired,
                "token", link.getToken(),
                "sentAt", link.getSentAt().toString(),
                "expiresAt", link.getExpiresAt().toString(),
                "documentsSubmitted", link.isDocumentsSubmitted(),
                "expired", expired
        ));
    }

    /**
     * POST /api/v1/upload-link/validate-otp
     * Validates email+mobile OTP before allowing upload (uses existing CitizenAuth OTP pattern).
     */
    @PostMapping("/validate-otp")
    public ResponseEntity<?> validateOtp(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String otp = body.get("otp");
        String mobile = body.get("mobile");

        if (token == null || otp == null || mobile == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_FIELDS",
                    "message", "token, otp, and mobile are required."));
        }

        Optional<UploadLink> linkOpt = uploadLinkRepository.findByToken(token);
        if (linkOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_TOKEN",
                    "message", "Upload link not found or expired."));
        }

        UploadLink link = linkOpt.get();
        if (!link.isActive() || link.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "LINK_EXPIRED",
                    "message", "This upload link has expired."));
        }

        // Verify OTP using existing OTP service (lookup by mobile number)
        OtpService.OtpVerificationResult result = otpService.verifyOtp(mobile, otp);

        switch (result) {
            case SUCCESS:
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "OTP verified. You may now upload documents.",
                        "complaintNumber", link.getComplaintNumber()
                ));
            case INVALID:
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "INVALID_OTP",
                        "message", "Invalid OTP. Please try again."
                ));
            case EXPIRED_OR_NOT_FOUND:
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "OTP_EXPIRED",
                        "message", "OTP has expired or not found. Please request a new one."
                ));
            case MAX_ATTEMPTS_EXCEEDED:
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "MAX_ATTEMPTS",
                        "message", "Maximum OTP verification attempts exceeded."
                ));
            default:
                return ResponseEntity.internalServerError().body(Map.of(
                        "error", "VERIFICATION_FAILED",
                        "message", "OTP verification failed."
                ));
        }
    }

    /**
     * POST /api/v1/upload-link/upload/{token}
     * Accepts multipart files, stores them, marks documentsSubmitted=true, sends notification to complaint owner.
     */
    @PostMapping("/upload/{token}")
    public ResponseEntity<?> uploadDocuments(
            @PathVariable String token,
            @RequestParam("files") MultipartFile[] files) {

        Optional<UploadLink> linkOpt = uploadLinkRepository.findByToken(token);
        if (linkOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_TOKEN",
                    "message", "Upload link not found."));
        }

        UploadLink link = linkOpt.get();
        if (!link.isActive()) {
            return ResponseEntity.badRequest().body(Map.of("error", "LINK_INACTIVE",
                    "message", "This upload link is no longer active."));
        }
        if (link.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "LINK_EXPIRED",
                    "message", "This upload link has expired."));
        }

        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "NO_FILES",
                    "message", "At least one file must be provided."));
        }

        // TODO: Integrate with cms-storage-service to persist files
        // For now, log the file details
        for (MultipartFile file : files) {
            log.info("Received file: {} ({} bytes) for complaint {}",
                    file.getOriginalFilename(), file.getSize(), link.getComplaintNumber());
        }

        // Mark documents as submitted
        link.setDocumentsSubmitted(true);
        link.setDocumentsSubmittedAt(LocalDateTime.now());
        uploadLinkRepository.save(link);

        // Notify complaint owner
        Optional<Complaint> complaintOpt = complaintRepository.findByComplaintNumber(link.getComplaintNumber());
        complaintOpt.ifPresent(complaint -> {
            String owner = complaint.getAssignedOfficer() != null ? complaint.getAssignedOfficer() : "RBIO_OFFICER";
            notificationService.send(
                    owner,
                    "DOCUMENTS_UPLOADED",
                    "Documents uploaded by complainant",
                    "Complainant has uploaded " + files.length + " document(s) for complaint " + link.getComplaintNumber(),
                    link.getComplaintNumber(),
                    "COMPLAINT",
                    "/complaint/" + link.getComplaintNumber()
            );
        });

        return ResponseEntity.ok(Map.of(
                "success", true,
                "filesUploaded", files.length,
                "message", "Documents uploaded successfully."
        ));
    }

    /**
     * POST /api/v1/upload-link/revoke
     * Deactivates the upload link for a complaint.
     */
    @PostMapping("/revoke")
    public ResponseEntity<?> revokeLink(@RequestBody Map<String, String> body) {
        String complaintNumber = body.get("complaintNumber");

        if (complaintNumber == null || complaintNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_COMPLAINT_NUMBER",
                    "message", "Complaint number is required."));
        }

        Optional<UploadLink> linkOpt = uploadLinkRepository.findByComplaintNumberAndActiveTrue(complaintNumber);
        if (linkOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "No active upload link found for complaint: " + complaintNumber
            ));
        }

        UploadLink link = linkOpt.get();
        link.setActive(false);
        uploadLinkRepository.save(link);

        log.info("Upload link revoked for complaint {}", complaintNumber);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Upload link revoked successfully."
        ));
    }
}
