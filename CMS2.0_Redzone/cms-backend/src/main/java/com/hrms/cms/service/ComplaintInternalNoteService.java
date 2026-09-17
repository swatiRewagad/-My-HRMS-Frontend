package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintInternalNote;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.repository.ComplaintInternalNoteRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import com.hrms.cms.security.RequestIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Entity-side internal notes (UST860).
 *
 * Notes are visible only to RE-side roles within the owning entity — never to CEPC/RBIOS users
 * and never to the complainant. Every read and write path here refuses an RBI caller outright,
 * so the restriction is a server-side control rather than a hidden UI element.
 *
 * The author may edit their own note for a configurable window (default 5 minutes), after which
 * it locks. The lock instant is stamped at insert and checked server-side.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintInternalNoteService {

    private static final String CFG_EDIT_WINDOW = "cms.query.internal_note_edit_window_minutes";

    private final ComplaintInternalNoteRepository noteRepository;
    private final ComplaintRepository complaintRepository;
    private final SystemConfigRepository systemConfigRepository;

    @Transactional
    public ComplaintInternalNote addNote(String complaintNumber, String entityCode,
                                         RequestIdentity identity, String body) {
        requireEntitySide(identity);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Note body is required");
        }

        Complaint complaint = requireComplaint(complaintNumber, entityCode);
        LocalDateTime now = LocalDateTime.now();

        ComplaintInternalNote note = ComplaintInternalNote.builder()
                .complaintId(complaint.getId())
                .entityCode(complaint.getEntityCode())
                .body(body.trim())
                .authorUserId(identity.getUserId())
                .authorName(identity.getDisplayName())
                .authorRole(identity.getPrimaryRole())
                .createdAt(now)
                .editLockedAt(now.plusMinutes(editWindowMinutes()))
                .editCount(0)
                .build();

        return noteRepository.save(note);
    }

    /**
     * All entity-side roles in the owning entity can read the notes — they are shared within the
     * entity, not private to their author.
     */
    @Transactional(readOnly = true)
    public List<ComplaintInternalNote> getNotes(String complaintNumber, String entityCode,
                                                RequestIdentity identity) {
        requireEntitySide(identity);
        Complaint complaint = requireComplaint(complaintNumber, entityCode);
        return noteRepository.findByComplaintIdAndEntityCodeOrderByCreatedAtDesc(
                complaint.getId(), complaint.getEntityCode());
    }

    /** Only the original author may edit, and only inside the window. */
    @Transactional
    public ComplaintInternalNote editNote(Long noteId, String entityCode,
                                          RequestIdentity identity, String body) {
        requireEntitySide(identity);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Note body is required");
        }

        ComplaintInternalNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new NoSuchElementException("Note not found: " + noteId));

        if (entityCode != null && !entityCode.equals(note.getEntityCode())) {
            throw new SecurityException("Access denied: note does not belong to entity " + entityCode);
        }
        if (!identity.getUserId().equals(note.getAuthorUserId())) {
            throw new SecurityException("Only the author may edit an internal note");
        }
        if (!note.isEditable(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "This note is locked — the edit window closed at " + note.getEditLockedAt());
        }

        note.setBody(body.trim());
        note.setUpdatedAt(LocalDateTime.now());
        note.setEditCount(note.getEditCount() + 1);
        return noteRepository.save(note);
    }

    /**
     * Guards exports bound for CEPC/RBIOS (UST860). Callers that build an outbound package invoke
     * this so the block is enforced on the server rather than by omitting a checkbox.
     */
    public void assertNotesExcludedFromExternalExport(boolean includeInternalNotes, String audience) {
        if (includeInternalNotes && audience != null && !"RE".equalsIgnoreCase(audience)) {
            throw new IllegalArgumentException(
                    "Internal notes are entity-only and cannot be included in an export shared with "
                            + audience);
        }
    }

    public int editWindowMinutes() {
        return systemConfigRepository.findByConfigKey(CFG_EDIT_WINDOW)
                .map(SystemConfig::getConfigValue)
                .map(v -> {
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException e) {
                        log.warn("Config {} is not a number: {} — using 5", CFG_EDIT_WINDOW, v);
                        return 5;
                    }
                })
                .orElse(5);
    }

    private void requireEntitySide(RequestIdentity identity) {
        if (identity == null || !identity.isRe()) {
            throw new SecurityException("Internal notes are visible only to entity-side users");
        }
    }

    private Complaint requireComplaint(String complaintNumber, String entityCode) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new NoSuchElementException("Complaint not found: " + complaintNumber));
        if (entityCode != null && !entityCode.equals(complaint.getEntityCode())) {
            throw new SecurityException("Access denied: complaint does not belong to entity " + entityCode);
        }
        return complaint;
    }
}
