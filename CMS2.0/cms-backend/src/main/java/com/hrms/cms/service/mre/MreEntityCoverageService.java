package com.hrms.cms.service.mre;

import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MreEntityCoverageService {

    private final RegulatedEntityRepository regulatedEntityRepo;

    @Cacheable(value = "mre-rules", key = "'entity-covered-' + #entityCode")
    public boolean isEntityCovered(String entityCode, String entityType) {
        if (entityCode == null || entityCode.isBlank()) return false;
        return regulatedEntityRepo.existsByNameNormalizedContainingIgnoreCase(entityCode)
                || regulatedEntityRepo.existsByNameContainingIgnoreCase(entityCode);
    }
}
