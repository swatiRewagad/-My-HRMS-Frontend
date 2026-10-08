package com.hrms.cms.service;

import com.hrms.cms.dto.cepc.CepcContactPersonRequest;
import com.hrms.cms.entity.CepcContactPerson;
import com.hrms.cms.repository.CepcContactPersonRepository;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Contact Entity tab's contact persons: who at the entity this office has actually been dealing with.
 *
 * <p>Read and write for one new CEPC-owned table and nothing else. It does not touch the complaint, its
 * status, its workflow stage or its nodal officer record — recording a contact is a note, not a transition,
 * and the complaint write path is off limits for CEPC work. The complaint is loaded only to reject a
 * contact aimed at a complaint number that does not exist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CepcContactPersonService {

    /** Matches the date format the Contact Entity tab already renders for record dates. */
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CepcContactPersonRepository contactRepository;
    private final ComplaintRepository complaintRepository;

    /** The complaint number is not a known complaint. */
    public static class ComplaintNotFoundException extends RuntimeException {
        public ComplaintNotFoundException(String message) { super(message); }
    }

    /** The contact id is not a contact, or belongs to a different complaint. */
    public static class ContactNotFoundException extends RuntimeException {
        public ContactNotFoundException(String message) { super(message); }
    }

    /** The submitted form is not saveable. The message is shown verbatim on the form. */
    public static class InvalidContactException extends RuntimeException {
        public InvalidContactException(String message) { super(message); }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String complaintNumber) {
        return contactRepository.findByComplaintNumberOrderByCreatedAtAsc(complaintNumber)
                .stream().map(CepcContactPersonService::toDto).toList();
    }

    @Transactional
    public Map<String, Object> add(String complaintNumber, CepcContactPersonRequest request, String actor) {
        requireComplaint(complaintNumber);
        validate(request);

        CepcContactPerson contact = CepcContactPerson.builder()
                .complaintNumber(complaintNumber)
                .name(trim(request.getName()))
                .designation(trim(request.getDesignation()))
                .email(trim(request.getEmail()))
                .phone(trim(request.getPhone()))
                .remarks(trim(request.getRemarks()))
                .createdBy(actor)
                .lastModifiedBy(actor)
                .build();

        CepcContactPerson saved = contactRepository.save(contact);
        log.info("CEPC contact person {} added for complaint {} by {}", saved.getId(), complaintNumber, actor);
        return toDto(saved);
    }

    @Transactional
    public Map<String, Object> update(String complaintNumber, Long id, CepcContactPersonRequest request,
                                      String actor) {
        CepcContactPerson contact = contactRepository.findById(id)
                .orElseThrow(() -> new ContactNotFoundException("Contact person " + id + " was not found."));

        // The id alone would be enough to find the row, so without this the complaint number in the path
        // would be decoration and a caller could edit any complaint's contact through any complaint's URL.
        if (!contact.getComplaintNumber().equals(complaintNumber)) {
            throw new ContactNotFoundException(
                    "Contact person " + id + " does not belong to complaint " + complaintNumber + ".");
        }
        validate(request);

        contact.setName(trim(request.getName()));
        contact.setDesignation(trim(request.getDesignation()));
        contact.setEmail(trim(request.getEmail()));
        contact.setPhone(trim(request.getPhone()));
        contact.setRemarks(trim(request.getRemarks()));
        contact.setLastModifiedBy(actor);

        CepcContactPerson saved = contactRepository.save(contact);
        log.info("CEPC contact person {} updated for complaint {} by {}", id, complaintNumber, actor);
        return toDto(saved);
    }

    private void requireComplaint(String complaintNumber) {
        if (complaintRepository.findByComplaintNumber(complaintNumber).isEmpty()) {
            throw new ComplaintNotFoundException("Complaint " + complaintNumber + " was not found.");
        }
    }

    /**
     * Re-checks what the request DTO's annotations check, because {@code @Valid} is skippable and this is the
     * rule that must hold — see {@code CepcContactPersonRequest}.
     */
    private void validate(CepcContactPersonRequest request) {
        if (request == null || isBlank(request.getName())) {
            throw new InvalidContactException("Contact person name is required.");
        }
        String email = trim(request.getEmail());
        String phone = trim(request.getPhone());

        // A contact nobody can reach is the same defect as no contact at all. Either channel will do — the
        // officer may only have been given one — but not neither.
        if (isBlank(email) && isBlank(phone)) {
            throw new InvalidContactException("Enter an email address or a mobile number for the contact person.");
        }
        if (!isBlank(email) && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new InvalidContactException("Enter a valid email address.");
        }
        if (!isBlank(phone) && !phone.matches("^[6-9]\\d{9}$")) {
            throw new InvalidContactException("Enter a valid 10-digit Indian mobile number.");
        }
    }

    private static Map<String, Object> toDto(CepcContactPerson contact) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", contact.getId());
        out.put("complaintNumber", contact.getComplaintNumber());
        out.put("name", contact.getName());
        out.put("designation", contact.getDesignation());
        out.put("email", contact.getEmail());
        out.put("phone", contact.getPhone());
        out.put("remarks", contact.getRemarks());
        out.put("createdBy", contact.getCreatedBy());
        out.put("lastModifiedBy", contact.getLastModifiedBy());
        out.put("createdAt", format(contact.getCreatedAt()));
        out.put("lastModifiedAt", format(contact.getLastModifiedAt()));
        return out;
    }

    private static String format(LocalDateTime at) {
        return at == null ? null : at.format(DISPLAY);
    }

    /** Empty stays empty rather than becoming {@code ""}, so a cleared field reads as absent on the list. */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
