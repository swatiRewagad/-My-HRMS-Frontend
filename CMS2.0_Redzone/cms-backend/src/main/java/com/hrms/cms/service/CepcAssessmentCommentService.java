package com.hrms.cms.service;

import com.hrms.cms.dto.cepc.CepcAssessmentCommentRequest;
import com.hrms.cms.entity.CepcAssessmentComment;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.repository.CepcAssessmentCommentRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The CEPC "Comments" box (Assessment / Forward / Final Decision tabs) and the nodal record's own
 * "To NO" / "To PNO" threads.
 *
 * <p>Read and write for one new CEPC-owned table and nothing else, matching {@link CepcContactPersonService}:
 * it does not touch the complaint, its status or its workflow stage — recording a comment is a note, not a
 * transition, and the complaint write path is off limits for CEPC work. The complaint (or nodal record) is
 * loaded only to reject a comment aimed at one that does not exist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CepcAssessmentCommentService {

    private static final Set<String> VALID_TARGETS = Set.of("NO", "PNO");

    private final CepcAssessmentCommentRepository commentRepository;
    private final ComplaintRepository complaintRepository;
    private final NodalOfficerRecordRepository nodalRecordRepository;

    public static class ComplaintNotFoundException extends RuntimeException {
        public ComplaintNotFoundException(String message) { super(message); }
    }

    public static class RecordNotFoundException extends RuntimeException {
        public RecordNotFoundException(String message) { super(message); }
    }

    public static class InvalidCommentException extends RuntimeException {
        public InvalidCommentException(String message) { super(message); }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForComplaint(String complaintNumber) {
        return commentRepository
                .findByComplaintNumberAndNodalRecordNumberIsNullOrderByCreatedAtAsc(complaintNumber)
                .stream().map(CepcAssessmentCommentService::toDto).toList();
    }

    @Transactional
    public Map<String, Object> addForComplaint(String complaintNumber, CepcAssessmentCommentRequest request) {
        requireComplaint(complaintNumber);
        requireText(request);

        CepcAssessmentComment comment = CepcAssessmentComment.builder()
                .complaintNumber(complaintNumber)
                .text(request.getText().strip())
                .author(request.getAuthor().strip())
                .initials(blankToNull(request.getInitials()))
                .role(blankToNull(request.getRole()))
                .color(blankToNull(request.getColor()))
                .build();

        CepcAssessmentComment saved = commentRepository.save(comment);
        log.info("CEPC comment {} added for complaint {} by {}", saved.getId(), complaintNumber, saved.getAuthor());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForNodalRecord(String recordNumber) {
        return commentRepository.findByNodalRecordNumberOrderByCreatedAtDesc(recordNumber)
                .stream().map(CepcAssessmentCommentService::toDto).toList();
    }

    @Transactional
    public Map<String, Object> addForNodalRecord(String recordNumber, CepcAssessmentCommentRequest request) {
        NodalOfficerRecord record = nodalRecordRepository.findByRecordNumber(recordNumber)
                .orElseThrow(() -> new RecordNotFoundException("No nodal officer record found for " + recordNumber + "."));
        requireText(request);
        String target = request.getTarget() == null ? null : request.getTarget().strip().toUpperCase();
        if (target == null || !VALID_TARGETS.contains(target)) {
            throw new InvalidCommentException("target must be NO or PNO.");
        }

        String complaintNumber = blankToNull(request.getComplaintNumber());
        CepcAssessmentComment comment = CepcAssessmentComment.builder()
                .complaintNumber(complaintNumber != null ? complaintNumber.strip() : record.getComplaintNumber())
                .nodalRecordNumber(recordNumber)
                .text(request.getText().strip())
                .author(request.getAuthor().strip())
                .initials(blankToNull(request.getInitials()))
                .target(target)
                .color(blankToNull(request.getColor()))
                .build();

        CepcAssessmentComment saved = commentRepository.save(comment);
        log.info("CEPC nodal comment {} added for record {} by {}", saved.getId(), recordNumber, saved.getAuthor());
        return toDto(saved);
    }

    private void requireComplaint(String complaintNumber) {
        if (complaintRepository.findByComplaintNumber(complaintNumber).isEmpty()) {
            throw new ComplaintNotFoundException("Complaint " + complaintNumber + " was not found.");
        }
    }

    /**
     * Re-checks what the request DTO's annotations check, because {@code @Valid} is skippable and this is
     * the rule that must hold — see {@code CepcContactPersonService} for the same pattern.
     */
    private void requireText(CepcAssessmentCommentRequest request) {
        if (request == null || isBlank(request.getText())) {
            throw new InvalidCommentException("Comment text is required.");
        }
        if (isBlank(request.getAuthor())) {
            throw new InvalidCommentException("Comment author is required.");
        }
    }

    private static Map<String, Object> toDto(CepcAssessmentComment comment) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", comment.getId());
        out.put("author", comment.getAuthor());
        out.put("target", comment.getTarget());
        out.put("complaintNumber", comment.getComplaintNumber());
        out.put("initials", comment.getInitials());
        out.put("text", comment.getText());
        out.put("color", comment.getColor());
        out.put("role", comment.getRole());
        out.put("noRecordNumber", comment.getNodalRecordNumber());
        out.put("createdAt", comment.getCreatedAt() == null ? null : comment.getCreatedAt().toString());
        return out;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
