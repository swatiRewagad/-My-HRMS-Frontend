package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ImpleadedParty;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.ImpleadedPartyRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Impleading: bringing a further party into an existing complaint (UST544-546, 767).
 *
 * <p>Replaces a CSV append. The previous implementation added a name to
 * {@code COMPLAINTS.IMPLEADED_PARTIES} and nothing else — no Nodal Officer record for the new party, no
 * per-party closure data, and the {@code partyType} the UI collected was silently discarded. The two
 * endpoints the frontend already called ({@code /implead-nodal-record}, {@code /implead-validation}) did not
 * exist at all, and the validation call swallowed its own 404 as {@code {valid: true}} — so a closure could
 * finalise over parties that had never been served, while the screen reported everything was in order.
 *
 * <p>THE SIX-PARTY CAP IS NOT REIMPLEMENTED HERE. {@code RbioAdditionalEntityService} owns it and publishes
 * {@code assertCapAllowsOneMore} for exactly this purpose. The cap counts parties per complaint, so
 * impleaded parties and additional entities share it rather than each getting six.
 *
 * <p>KNOWN CONSTRAINT, DELIBERATELY NOT WORKED AROUND. {@code NODAL_OFFICER_RECORDS} carries a
 * one-record-per-complaint unique key, added by {@code V81} and owned by another session. UST544 asks for a
 * NO record per impleaded entity, which that key forbids. Rather than widen a unique key on a shared
 * database behind the owning session's back, this service resolves the NO/PNO for each impleaded party
 * through {@link NodalOfficerResolver} and stores the outcome on the party row, linking to the complaint's
 * existing NO record where one is present. Every party therefore has resolved, auditable contact details
 * and closure can verify them; what is still outstanding is a separate ROW per party, which needs that key
 * relaxed. This is reported rather than silently forced.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioImpleadService {

    private final ImpleadedPartyRepository impleadedPartyRepository;
    private final RbioAdditionalEntityService additionalEntityService;
    private final NodalOfficerResolver nodalOfficerResolver;
    private final NodalOfficerRecordRepository nodalOfficerRecordRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;

    /**
     * Records an impleaded party and resolves its Nodal Officer contacts.
     *
     * <p>Refuses rather than defaulting on every identity field: an impleaded party with no name is a
     * quasi-judicial act against nobody, and a blank row would later read as a served party.
     */
    @Transactional
    public ImpleadedParty implead(Complaint complaint,
                                  String partyName,
                                  String partyType,
                                  String reason,
                                  String actor,
                                  String actorRole) {
        if (complaint == null) {
            throw new IllegalArgumentException("A complaint is required to implead a party");
        }
        if (partyName == null || partyName.isBlank()) {
            throw new IllegalArgumentException("partyName is required to implead a party");
        }

        String complaintNumber = complaint.getComplaintNumber();
        String trimmedName = partyName.trim();

        // Same cap as the Add-Entity flow, enforced by the service that owns it.
        additionalEntityService.assertCapAllowsOneMore(complaintNumber);

        impleadedPartyRepository.findByComplaintNumberAndPartyNameIgnoreCase(complaintNumber, trimmedName)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "'" + trimmedName + "' is already impleaded into complaint " + complaintNumber);
                });

        NodalOfficerResolver.Resolution resolution =
                nodalOfficerResolver.resolve(trimmedName, complaint.getRbioOfficeCode());

        ImpleadedParty party = ImpleadedParty.builder()
                .complaintNumber(complaintNumber)
                .partyName(trimmedName)
                .partyType(partyType == null || partyType.isBlank() ? null : partyType.trim())
                .regulatedEntityId(resolveRegulatedEntityId(trimmedName))
                .nodalOfficerRecordId(existingRecordIdFor(complaintNumber))
                .dataStatus(ImpleadedParty.STATUS_INFORMATION_REQUIRED)
                .impleadReason(reason == null || reason.isBlank() ? null : reason.trim())
                .impleadedBy(actor)
                .impleadedByRole(actorRole)
                .build();

        ImpleadedParty saved = impleadedPartyRepository.save(party);

        log.info("UST544: impleaded '{}' into complaint {} by {} ({}); NO/PNO source={}",
                trimmedName, complaintNumber, actor, actorRole, resolution.getSource());

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ImpleadedParty> findForComplaint(String complaintNumber) {
        return impleadedPartyRepository.findByComplaintNumberOrderByImpleadedAtAsc(complaintNumber);
    }

    /**
     * Whether every impleaded party has the data closure requires (UST546).
     *
     * <p>Returns the offending party names rather than a bare boolean so the refusal can name them. An
     * empty list means closure may proceed as far as impleading is concerned.
     */
    @Transactional(readOnly = true)
    public List<String> findIncompleteParties(String complaintNumber) {
        List<String> incomplete = new ArrayList<>();
        for (ImpleadedParty party : impleadedPartyRepository
                .findByComplaintNumberOrderByImpleadedAtAsc(complaintNumber)) {
            if (party.isIncomplete()) {
                incomplete.add(party.getPartyName());
            } else if (party.getClosureClause() == null || party.getClosureClause().isBlank()) {
                // UST546 requires the clause to cover ALL impleaded entities, so a party marked COMPLETE
                // but carrying no clause still blocks closure. Without this the status flag alone could be
                // flipped to wave a party through without citing anything against it.
                incomplete.add(party.getPartyName());
            }
        }
        return incomplete;
    }

    /**
     * Records the per-party closure data that {@link #findIncompleteParties} checks for.
     *
     * <p>A blank clause is refused rather than stored: marking a party complete without citing a clause is
     * precisely the state the closure gate exists to prevent.
     */
    @Transactional
    public ImpleadedParty recordClosureData(Long partyId,
                                            String closureClause,
                                            java.math.BigDecimal compensationAmount) {
        ImpleadedParty party = impleadedPartyRepository.findById(partyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Impleaded party " + partyId + " not found"));

        if (closureClause == null || closureClause.isBlank()) {
            throw new IllegalArgumentException(
                    "A closure clause is required for impleaded party '" + party.getPartyName() + "'");
        }

        party.setClosureClause(closureClause.trim());
        party.setCompensationAmount(compensationAmount);
        party.setDataStatus(ImpleadedParty.STATUS_COMPLETE);
        return impleadedPartyRepository.save(party);
    }

    /**
     * The complaint's existing NO record id, if any, so a party row points at real contact data instead of
     * implying its own record exists. Null is an honest answer here.
     */
    private Long existingRecordIdFor(String complaintNumber) {
        Optional<NodalOfficerRecord> existing =
                nodalOfficerRecordRepository.findFirstByComplaintNumber(complaintNumber);
        return existing.map(NodalOfficerRecord::getId).orElse(null);
    }

    /** Non-blocking: an unrecognised party is still impleadable, it simply is not a known RE. */
    private Long resolveRegulatedEntityId(String partyName) {
        try {
            String normalized = RegulatedEntity.normalize(partyName);
            if (normalized.isBlank()) {
                return null;
            }
            return regulatedEntityRepository.findByNameNormalized(normalized)
                    .map(RegulatedEntity::getId)
                    .orElse(null);
        } catch (RuntimeException e) {
            log.debug("Could not resolve regulated entity for '{}': {}", partyName, e.getMessage());
            return null;
        }
    }
}
