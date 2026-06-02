package com.rbi.cms.ingestion.email.service;

import com.rbi.cms.ingestion.email.entity.DeoUser;
import com.rbi.cms.ingestion.email.repository.DeoUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoundRobinAssignmentService {

    private final DeoUserRepository deoUserRepository;
    private final AtomicInteger nextPointer = new AtomicInteger(0);

    @Transactional
    public String assignNextDeo() {
        List<DeoUser> eligible = deoUserRepository.findEligibleDeos();

        if (eligible.isEmpty()) {
            log.warn("No eligible DEOs available for assignment. Queue refresh triggered.");
            return null;
        }

        int pointer = nextPointer.getAndUpdate(i -> (i + 1) % eligible.size());
        int index = pointer % eligible.size();

        DeoUser selected = eligible.get(index);
        selected.setCurrentAssignedCount(selected.getCurrentAssignedCount() + 1);
        deoUserRepository.save(selected);

        log.info("Round-robin assigned to DEO: {} (count: {}/{})",
                selected.getUserId(), selected.getCurrentAssignedCount(), selected.getMaxThreshold());

        return selected.getUserId();
    }

    @Transactional
    public void decrementCount(String userId) {
        if (userId == null) return;
        deoUserRepository.findByUserId(userId).ifPresent(deo -> {
            deo.setCurrentAssignedCount(Math.max(0, deo.getCurrentAssignedCount() - 1));
            deoUserRepository.save(deo);
        });
    }

    @Transactional
    public void incrementCount(String userId) {
        if (userId == null) return;
        deoUserRepository.findByUserId(userId).ifPresent(deo -> {
            deo.setCurrentAssignedCount(deo.getCurrentAssignedCount() + 1);
            deoUserRepository.save(deo);
        });
    }

    public void resetPointer() {
        nextPointer.set(0);
        log.info("Round-robin pointer reset");
    }
}
