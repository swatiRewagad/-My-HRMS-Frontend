package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.entity.UploadLink;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.UploadLinkRepository;
import com.hrms.cms.service.FileStorageService;
import com.hrms.cms.service.FileUploadValidator;
import com.hrms.cms.service.NotificationConfigService;
import com.hrms.cms.service.NotificationService;
import com.hrms.cms.service.OtpService;
import com.hrms.cms.service.OutboundMessagePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final NotificationConfigService notificationConfig;
    private final OutboundMessagePort outboundMessagePort;
    private final FileStorageService fileStorageService;

    /** SMS channel label for the mobile OTP, matching OtpAttempt.channel. */
    private static final String CHANNEL_SMS = "SMS";
    /** EMAIL channel label for the email OTP. */
    private static final String CHANNEL_EMAIL = "EMAIL";

    /**
     * POST /api/v1/upload-link/send — creates a link AND dispatches it (UST598, UST601).
     *
     * <p>WHAT CHANGED AND WHY. This previously only persisted a row and returned the token in the HTTP
     * response, dispatching nothing at all — while the officer's screen told them the link had been
     * "sent via email and SMS". The token appearing solely in an API response body meant the only way a
     * complainant could ever receive it was for a developer to read it out of the network tab.
     *
     * <p>Expiry now comes from {@code notification.upload_link.expiry_days} rather than a
     * {@code private static final int LINK_EXPIRY_DAYS = 7}, per UST601.
     *
     * <p>The email and mobile now FALL BACK to the complaint's own contact details when the caller omits
     * them. The frontend posts only {@code complaintNumber}, so both columns were being written null —
     * meaning that even once dispatch existed there would have been no address to dispatch to.
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendUploadLink(@RequestBody Map<String, String> body) {
        String complaintNumber = body.get("complaintNumber");

        if (complaintNumber == null || complaintNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_COMPLAINT_NUMBER",
                    "message", "Complaint number is required."));
        }

        Optional<Complaint> complaintOpt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (complaintOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "COMPLAINT_NOT_FOUND",
                    "message", "No complaint found with number: " + complaintNumber));
        }
        Complaint complaint = complaintOpt.get();

        String email = firstNonBlank(body.get("email"), complaint.getComplainantEmail());
        String mobile = firstNonBlank(body.get("mobile"), complaint.getComplainantPhone());

        // Refuse rather than create a link nobody can receive. Persisting an undeliverable link would
        // block closure (UST603) while giving the complainant no way to satisfy the request.
        if (isBlank(email) && isBlank(mobile)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "NO_CONTACT_DETAILS",
                    "messageKey", "upload_link.error_no_contact",
                    "message", "This complaint has neither an email address nor a mobile number, "
                            + "so a secure upload link cannot be delivered."));
        }

        // UST601: only one link may be live at a time, so issuing a new one retires the previous.
        uploadLinkRepository.findByComplaintNumberAndActiveTrue(complaintNumber)
                .ifPresent(existing -> {
                    existing.setActive(false);
                    uploadLinkRepository.save(existing);
                });

        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        int expiryDays = notificationConfig.uploadLinkExpiryDays();

        UploadLink link = uploadLinkRepository.save(UploadLink.builder()
                .complaintNumber(complaintNumber)
                .token(token)
                .complainantEmail(email)
                .complainantMobile(mobile)
                .sentAt(now)
                .expiresAt(now.plusDays(expiryDays))
                .active(true)
                .documentsSubmitted(false)
                .build());

        int dispatched = dispatchLink(link, complaintNumber);

        log.info("Upload link created for complaint {} (expires {}), dispatched on {} channel(s)",
                complaintNumber, link.getExpiresAt(), dispatched);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("expiresAt", link.getExpiresAt().toString());
        response.put("channelsDispatched", dispatched);
        response.put("message", dispatched > 0
                ? "Upload link sent to the complainant."
                : "Upload link created, but no channel could be dispatched. Check the contact details.");
        return ResponseEntity.ok(response);
    }

    /**
     * Sends the link over every channel with an address.
     *
     * <p>The TOKEN IS NOT RETURNED to the caller any more. It is a bearer credential — anyone holding it
     * can upload against the complaint — and returning it to the officer's browser put it in network
     * logs and browser history for no functional reason, since the complainant receives it by email or
     * SMS. Only the fact of dispatch comes back.
     *
     * <p>Dispatch failure does not fail the request: the link exists and can be re-sent, and a 500 here
     * would leave the officer unsure whether a link is live — which matters because a live link blocks
     * closure.
     */
    private int dispatchLink(UploadLink link, String complaintNumber) {
        String uploadPath = "/public/upload-documents/" + link.getToken();
        String bodyText = "A secure link has been created so you can upload documents for complaint "
                + complaintNumber + ". Open " + uploadPath + " and enter the two one-time passwords "
                + "we send you. The link expires on " + link.getExpiresAt() + ".";

        int dispatched = 0;
        if (!isBlank(link.getComplainantEmail())) {
            dispatched += tryDispatch(NotificationService.CHANNEL_EMAIL, link.getComplainantEmail(),
                    "Upload documents for complaint " + complaintNumber, bodyText, complaintNumber);
        }
        if (!isBlank(link.getComplainantMobile())) {
            dispatched += tryDispatch(NotificationService.CHANNEL_SMS, link.getComplainantMobile(),
                    "Upload documents for complaint " + complaintNumber, bodyText, complaintNumber);
        }
        return dispatched;
    }

    private int tryDispatch(String channel, String recipient, String subject, String bodyText,
                            String reference) {
        try {
            outboundMessagePort.send(channel, recipient, subject, bodyText, reference);
            return 1;
        } catch (Exception e) {
            log.warn("{} dispatch of upload link failed for complaint {}: {}", channel, reference,
                    e.getMessage());
            return 0;
        }
    }

    /**
     * GET /api/v1/upload-link/status/{complaintNumber} — current link state (UST776, UST601).
     *
     * <p>Emits {@code linkActive} as well as {@code active}: the frontend service types the field as
     * {@code linkActive} and reads {@code status()?.linkActive}, which was permanently undefined against
     * the old {@code active}-only response. That is why the closure-block banner and the lock icon never
     * appeared — the UI believed no link was ever live. Both names carry the same value so neither
     * contract breaks.
     *
     * <p>{@code documentsSubmittedDisplay} gives UST776 its Yes/No value directly rather than making
     * each caller re-derive it from a boolean.
     *
     * <p>THE TOKEN IS NO LONGER RETURNED. It is a bearer credential for an unauthenticated upload path;
     * echoing it to every officer viewing the complaint served no purpose and widened its exposure.
     */
    @GetMapping("/status/{complaintNumber}")
    public ResponseEntity<?> getLinkStatus(@PathVariable String complaintNumber) {
        Optional<UploadLink> linkOpt = uploadLinkRepository.findByComplaintNumberAndActiveTrue(complaintNumber);

        Map<String, Object> response = new LinkedHashMap<>();

        if (linkOpt.isEmpty()) {
            response.put("active", false);
            response.put("linkActive", false);
            response.put("documentsSubmitted", false);
            response.put("documentsSubmittedDisplay", "No");
            response.put("messageKey", "upload_link.status_none");
            response.put("message", "No active upload link for this complaint.");
            return ResponseEntity.ok(response);
        }

        UploadLink link = linkOpt.get();
        boolean expired = link.getExpiresAt() == null
                || link.getExpiresAt().isBefore(LocalDateTime.now());
        boolean live = !expired;

        response.put("active", live);
        response.put("linkActive", live);
        response.put("sentAt", String.valueOf(link.getSentAt()));
        response.put("expiresAt", String.valueOf(link.getExpiresAt()));
        response.put("expired", expired);
        response.put("documentsSubmitted", link.isDocumentsSubmitted());
        response.put("documentsSubmittedDisplay", link.isDocumentsSubmitted() ? "Yes" : "No");
        response.put("documentsSubmittedAt", String.valueOf(link.getDocumentsSubmittedAt()));
        response.put("otpVerified", link.getOtpVerifiedAt() != null);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/upload-link/request-otp — issues BOTH one-time passwords (UST599).
     *
     * <p>THIS ENDPOINT DID NOT EXIST. The controller had a validate-otp but nothing that generated an
     * OTP, so there was never a code to validate: the citizen page opened straight onto the OTP form and
     * every submission necessarily failed.
     *
     * <p>Two independent codes are issued, keyed by the link TOKEN plus the channel — see
     * {@code OtpService.generateSessionOtp}. Keying by mobile, as the login flow does, cannot work here:
     * {@code invalidateActiveOtps} matches on mobile alone, so the second code issued would retire the
     * first and the user could never hold both at once.
     *
     * <p>The codes are NEVER returned in the response, even in dev. They go out over their channels
     * only.
     */
    @PostMapping("/request-otp")
    public ResponseEntity<?> requestOtp(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "MISSING_TOKEN",
                    "messageKey", "upload_link.error_missing_token",
                    "message", "An upload link token is required."));
        }

        Optional<UploadLink> linkOpt = uploadLinkRepository.findByToken(token);
        if (linkOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "INVALID_TOKEN",
                    "messageKey", "upload_link.error_invalid_token",
                    "message", "This upload link is not valid."));
        }

        UploadLink link = linkOpt.get();
        if (isLapsed(link)) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("success", false,
                    "error", "LINK_EXPIRED",
                    "messageKey", "upload_link.error_expired",
                    "message", "This upload link has expired."));
        }

        boolean emailIssued = false;
        boolean smsIssued = false;

        if (!isBlank(link.getComplainantEmail())) {
            String code = otpService.generateSessionOtp(
                    link.getComplainantMobile(), token, CHANNEL_EMAIL, link.getComplainantEmail());
            emailIssued = tryDispatch(NotificationService.CHANNEL_EMAIL, link.getComplainantEmail(),
                    "Your document-upload verification code",
                    "Your email verification code for complaint " + link.getComplaintNumber()
                            + " is " + code + ".", link.getComplaintNumber()) == 1;
        }

        if (!isBlank(link.getComplainantMobile())) {
            String code = otpService.generateSessionOtp(
                    link.getComplainantMobile(), token, CHANNEL_SMS, link.getComplainantEmail());
            smsIssued = tryDispatch(NotificationService.CHANNEL_SMS, link.getComplainantMobile(),
                    "Verification code",
                    "Your mobile verification code for complaint " + link.getComplaintNumber()
                            + " is " + code + ".", link.getComplaintNumber()) == 1;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        // Which channels a code was issued for, so the page can render only the inputs that apply. NOT
        // whether delivery succeeded — no gateway is configured, so that cannot be claimed.
        response.put("emailOtpRequired", !isBlank(link.getComplainantEmail()));
        response.put("mobileOtpRequired", !isBlank(link.getComplainantMobile()));
        response.put("emailDispatched", emailIssued);
        response.put("mobileDispatched", smsIssued);
        response.put("messageKey", "upload_link.otp_sent");
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/upload-link/validate-otp — requires BOTH codes (UST599).
     *
     * <p>FIXES A CONTRACT MISMATCH THAT MADE THIS UNREACHABLE. The frontend has always posted
     * {@code {token, emailOtp, mobileOtp}} while this method read {@code token}, {@code otp} and
     * {@code mobile} — so {@code otp} and {@code mobile} were always null, the missing-fields guard fired
     * on every call, and the endpoint returned 400 unconditionally. The email OTP was not merely being
     * dropped; the whole path was dead. The request shape now matches what the client actually sends.
     *
     * <p>Both codes must verify. Verifying either one alone would defeat the point of a dual OTP, which
     * is to prove control of two separate channels.
     *
     * <p>Response uses {@code valid} — the field the client actually reads. It previously returned
     * {@code success}, so even a hypothetical pass would have left the page stuck.
     */
    @PostMapping("/validate-otp")
    public ResponseEntity<?> validateOtp(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String emailOtp = body.get("emailOtp");
        String mobileOtp = body.get("mobileOtp");

        if (isBlank(token)) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "MISSING_TOKEN",
                    "messageKey", "upload_link.error_missing_token",
                    "message", "An upload link token is required."));
        }

        Optional<UploadLink> linkOpt = uploadLinkRepository.findByToken(token);
        if (linkOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "INVALID_TOKEN",
                    "messageKey", "upload_link.error_invalid_token",
                    "message", "This upload link is not valid."));
        }

        UploadLink link = linkOpt.get();
        if (isLapsed(link)) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("valid", false,
                    "error", "LINK_EXPIRED",
                    "messageKey", "upload_link.error_expired",
                    "message", "This upload link has expired."));
        }

        boolean emailRequired = !isBlank(link.getComplainantEmail());
        boolean mobileRequired = !isBlank(link.getComplainantMobile());

        if ((emailRequired && isBlank(emailOtp)) || (mobileRequired && isBlank(mobileOtp))) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "MISSING_OTP",
                    "messageKey", "upload_link.error_both_otps_required",
                    "message", "Both the email and the mobile verification codes are required."));
        }

        // Each channel is verified independently, and BOTH must pass.
        if (emailRequired) {
            ResponseEntity<?> failure = rejectIfNotVerified(
                    otpService.verifySessionOtp(token, CHANNEL_EMAIL, emailOtp), "email");
            if (failure != null) {
                return failure;
            }
        }
        if (mobileRequired) {
            ResponseEntity<?> failure = rejectIfNotVerified(
                    otpService.verifySessionOtp(token, CHANNEL_SMS, mobileOtp), "mobile");
            if (failure != null) {
                return failure;
            }
        }

        link.setOtpVerifiedAt(LocalDateTime.now());
        uploadLinkRepository.save(link);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", true);
        response.put("complaintNumber", link.getComplaintNumber());
        response.put("messageKey", "upload_link.otp_verified");
        response.put("message", "Verified. You may now upload documents.");
        return ResponseEntity.ok(response);
    }

    /** Maps a failed verification to a response; null means the code verified. */
    private ResponseEntity<?> rejectIfNotVerified(OtpService.OtpVerificationResult result, String which) {
        return switch (result) {
            case SUCCESS -> null;
            case INVALID -> ResponseEntity.badRequest().body(Map.of("valid", false,
                    "error", "INVALID_OTP",
                    "messageKey", "upload_link.error_invalid_otp",
                    "message", "The " + which + " verification code is incorrect."));
            case EXPIRED_OR_NOT_FOUND -> ResponseEntity.badRequest().body(Map.of("valid", false,
                    "error", "OTP_EXPIRED",
                    "messageKey", "upload_link.error_otp_expired",
                    "message", "The " + which + " verification code has expired. Please request new codes."));
            case MAX_ATTEMPTS_EXCEEDED -> ResponseEntity.badRequest().body(Map.of("valid", false,
                    "error", "MAX_ATTEMPTS",
                    "messageKey", "upload_link.error_max_attempts",
                    "message", "Too many incorrect attempts. Please request new codes."));
        };
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
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "NO_FILES",
                    "messageKey", "upload_link.error_no_files",
                    "message", "At least one file must be provided."));
        }

        // OTP GATE. The upload endpoint previously performed no OTP check whatsoever, so while the
        // validate-otp path was broken shut, THIS path was wide open: anyone holding the token could
        // upload without any verification at all. The dual OTP is meaningless unless the endpoint it
        // guards actually requires it.
        if (link.getOtpVerifiedAt() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false,
                    "error", "OTP_NOT_VERIFIED",
                    "messageKey", "upload_link.error_otp_not_verified",
                    "message", "Verify the email and mobile codes before uploading documents."));
        }

        Optional<Complaint> complaintOpt =
                complaintRepository.findByComplaintNumber(link.getComplaintNumber());
        if (complaintOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "COMPLAINT_NOT_FOUND",
                    "messageKey", "upload_link.error_complaint_missing",
                    "message", "The complaint for this link no longer exists."));
        }
        Complaint complaint = complaintOpt.get();

        // FILES ARE NOW ACTUALLY STORED. This was a "// TODO: Integrate with cms-storage-service" that
        // logged each filename and then set documentsSubmitted = true — so the flag claimed a submission,
        // the officer was notified that documents had arrived, closure was blocked pending them, and NOT
        // ONE BYTE was kept. A complainant's evidence was discarded while every downstream signal said it
        // had been received.
        //
        // Validation is deliberately the SAME path every other upload uses (FileStorageService →
        // FileUploadValidator): per-file size, aggregate size, file count, extension allowlist and
        // magic-byte signature. This endpoint previously validated nothing at all, which made it the one
        // place an oversized or disguised file could enter the system.
        List<Map<String, Object>> stored = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            try {
                ComplaintAttachment saved = fileStorageService.handleSingleUpload(
                        file, complaint.getComplaintNumber(), complaint.getId(),
                        // UST589: recorded as the complainant's own submission, so the attachments list
                        // can distinguish it from an officer's upload.
                        "COMPLAINANT:" + link.getComplaintNumber(),
                        ComplaintAttachment.SOURCE_COMPLAINANT,
                        "COMPLAINANT_UPLOAD");
                stored.add(Map.of("id", saved.getId(), "name", String.valueOf(saved.getOriginalName())));
            } catch (FileUploadValidator.InvalidUploadException | IllegalArgumentException e) {
                // A rejection is reported, not thrown: rejecting the whole batch because one of five
                // files was too large would discard four valid documents and give the complainant no way
                // to tell which failed.
                rejected.add(Map.of("name", String.valueOf(file.getOriginalFilename()),
                        "reason", String.valueOf(e.getMessage())));
                log.warn("Upload rejected for complaint {}: {}", link.getComplaintNumber(), e.getMessage());
            } catch (IOException e) {
                log.error("Storage failure for complaint {}: {}", link.getComplaintNumber(), e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "success", false, "error", "STORAGE_FAILURE",
                        "messageKey", "upload_link.error_storage_failed",
                        "message", "The documents could not be saved. Please try again."));
            }
        }

        // documentsSubmitted reflects whether anything was ACTUALLY STORED, not merely that a request
        // arrived. Setting it on a batch where every file was rejected would tell the officer evidence
        // had been received and unblock nothing.
        if (stored.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "ALL_FILES_REJECTED",
                    "messageKey", "upload_link.error_all_rejected",
                    "message", "None of the files could be accepted.",
                    "rejected", rejected));
        }

        link.setDocumentsSubmitted(true);
        link.setDocumentsSubmittedAt(LocalDateTime.now());
        uploadLinkRepository.save(link);

        notificationService.raiseEvent(
                notificationConfig.documentsUploadedRecipients(),
                "DOCUMENTS_UPLOADED",
                "Documents uploaded by complainant",
                "The complainant has uploaded " + stored.size() + " document(s) for complaint "
                        + link.getComplaintNumber() + ".",
                link.getComplaintNumber(),
                "COMPLAINT",
                "/complaint/" + link.getComplaintNumber(),
                complaint);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("uploaded", stored.size());
        response.put("files", stored);
        response.put("rejected", rejected);
        response.put("messageKey", "upload_link.upload_success");
        response.put("message", "Documents uploaded successfully.");
        return ResponseEntity.ok(response);
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

    /**
     * A link is unusable once deactivated or past its expiry.
     *
     * <p>Both conditions are checked because the expiry sweep runs once daily at 08:00 — between the
     * moment a link lapses and the moment the job flips the flag, {@code active} is still true. Trusting
     * the flag alone would leave a dead link accepting uploads for up to a day.
     */
    private boolean isLapsed(UploadLink link) {
        return !link.isActive()
                || link.getExpiresAt() == null
                || link.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return isBlank(preferred) ? (isBlank(fallback) ? null : fallback.trim()) : preferred.trim();
    }
}
