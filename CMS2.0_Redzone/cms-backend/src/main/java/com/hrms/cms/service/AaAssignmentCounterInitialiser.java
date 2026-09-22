package com.hrms.cms.service;

import com.hrms.cms.entity.AaAssignmentCounter;
import com.hrms.cms.repository.AaAssignmentCounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a role group's round-robin pointer row in its OWN transaction.
 *
 * A separate bean with a public method, deliberately. This started life as a package-private method on
 * the engine annotated {@code @Transactional(REQUIRES_NEW)} and called through {@code this} — which does
 * not work twice over: Spring's proxy-based transaction advice is bypassed entirely on self-invocation,
 * and its attribute source ignores non-public methods anyway. The insert therefore joined the caller's
 * transaction, so on a first-ever assignment two concurrent callers would each read their own uncommitted
 * row, both proceed, and one would die on the unique constraint at flush — losing an assignment and
 * marking the transaction rollback-only far from the cause.
 *
 * Being a distinct bean, the proxy applies and the insert really does commit independently.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaAssignmentCounterInitialiser {

    private final AaAssignmentCounterRepository counterRepository;

    /**
     * Ensures a pointer row exists for {@code roleGroup}, committing before returning.
     *
     * Safe to call concurrently: the loser of the race finds the winner's committed row, and a unique
     * constraint violation is treated as success because the row it wanted now exists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(String roleGroup) {
        if (counterRepository.findByRoleGroup(roleGroup).isPresent()) {
            return;
        }
        try {
            counterRepository.saveAndFlush(AaAssignmentCounter.builder()
                    .roleGroup(roleGroup)
                    .lastAssignedIndex(0)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // A concurrent caller won. Its row is the one we wanted, so this is not a failure.
            // saveAndFlush rather than save so the violation surfaces here instead of at commit.
            log.debug("Assignment pointer for {} was created concurrently", roleGroup);
        }
    }
}
