package com.hrms.cms.service;

import com.hrms.cms.entity.DraftIdSequence;
import com.hrms.cms.repository.DraftIdSequenceRepository;
import com.hrms.cms.repository.EmailDraftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Generates the plain 6-digit "Complaint Id" shown for CRPC email/portal drafts,
 * backed by a single DB-locked counter row (DRAFT_ID_SEQUENCE) so concurrent
 * draft creations never race on the same number the way draftRepository.count()+1 did.
 */
@Service
@RequiredArgsConstructor
public class DraftIdGeneratorService {

    private static final int SEQUENCE_ROW_ID = 1;

    private final DraftIdSequenceRepository sequenceRepo;
    private final EmailDraftRepository draftRepository;

    @Transactional
    public String generateDraftId() {
        DraftIdSequence seq = sequenceRepo.findByIdForUpdate(SEQUENCE_ROW_ID)
                .orElseGet(() -> DraftIdSequence.builder()
                        .id(SEQUENCE_ROW_ID)
                        .lastSequence((int) draftRepository.count())
                        .updatedAt(LocalDateTime.now())
                        .build());

        seq.setLastSequence(seq.getLastSequence() + 1);
        sequenceRepo.save(seq);

        return String.format("%06d", seq.getLastSequence());
    }
}
