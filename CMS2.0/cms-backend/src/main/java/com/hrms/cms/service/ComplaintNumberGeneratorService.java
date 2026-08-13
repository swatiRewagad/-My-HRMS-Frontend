package com.hrms.cms.service;

import com.hrms.cms.entity.ComplaintNumberSequence;
import com.hrms.cms.entity.OfficeCodeMaster;
import com.hrms.cms.entity.OfficeGlobalThresholdConfig;
import com.hrms.cms.entity.OfficeOverflowMapping;
import com.hrms.cms.entity.OmbudsmanOfficeMaster;
import com.hrms.cms.repository.ComplaintNumberSequenceRepository;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import com.hrms.cms.repository.OfficeGlobalThresholdConfigRepository;
import com.hrms.cms.repository.OfficeOverflowMappingRepository;
import com.hrms.cms.repository.OmbudsmanOfficeMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintNumberGeneratorService {

    private final OmbudsmanOfficeMasterRepository ombudsmanOfficeRepo;
    private final OfficeCodeMasterRepository officeCodeRepo;
    private final ComplaintNumberSequenceRepository sequenceRepo;
    private final OfficeOverflowMappingRepository overflowMappingRepo;
    private final OfficeGlobalThresholdConfigRepository thresholdConfigRepo;

    @Transactional
    public String generateComplaintNumber(String department, String complainantState, String complainantDistrict) {
        return generateComplaintNumber(department, complainantState, complainantDistrict, false);
    }

    @Transactional
    public String generateComplaintNumber(String department, String complainantState, String complainantDistrict, boolean isVernacularOrCrpc) {
        String targetOfficeName = resolveOfficeName(department, complainantState, complainantDistrict);
        String targetOfficeCode = resolveOfficeCode(targetOfficeName);

        // Apply threshold overflow logic (only for non-vernacular, non-CRPC complaints)
        String assignedOfficeCode;
        if (isVernacularOrCrpc) {
            // Vernacular/CRPC: assign directly without incrementing counter
            assignedOfficeCode = targetOfficeCode;
            log.info("Vernacular/CRPC complaint — assigned to target office {} without threshold impact", targetOfficeName);
        } else {
            assignedOfficeCode = applyThresholdOverflow(targetOfficeCode);
        }

        String financialYear = computeFinancialYear(LocalDate.now());
        int nextSequence = getNextSequence(assignedOfficeCode, financialYear);

        String complaintNumber = String.format("N%s%s%06d", financialYear, assignedOfficeCode, nextSequence);
        log.info("Generated complaint number: {} (target={}, assigned={}, FY={}, seq={})",
                complaintNumber, targetOfficeName, assignedOfficeCode, financialYear, nextSequence);
        return complaintNumber;
    }

    private String applyThresholdOverflow(String targetOfficeCode) {
        int globalThreshold = getGlobalThreshold();

        OfficeCodeMaster targetOffice = officeCodeRepo.findByOfficeCodeForUpdate(targetOfficeCode)
                .orElseThrow(() -> new RuntimeException("Office code not found: " + targetOfficeCode));

        // If target office threshold NOT met: assign to target, increment counter
        if (targetOffice.getCounter() < globalThreshold) {
            targetOffice.setCounter(targetOffice.getCounter() + 1);
            officeCodeRepo.save(targetOffice);
            log.info("Office {} counter incremented to {}/{}", targetOffice.getOfficeName(),
                    targetOffice.getCounter(), globalThreshold);
            return targetOfficeCode;
        }

        // Target threshold IS MET — try overflow offices
        Optional<OfficeOverflowMapping> mappingOpt = overflowMappingRepo.findByOfficeCodeAndIsActiveTrue(targetOfficeCode);
        if (mappingOpt.isEmpty()) {
            // No overflow mapping: reset and assign to target
            targetOffice.setCounter(1);
            officeCodeRepo.save(targetOffice);
            log.warn("No overflow mapping for office {}, resetting counter", targetOffice.getOfficeName());
            return targetOfficeCode;
        }

        OfficeOverflowMapping mapping = mappingOpt.get();

        // Try Priority 1 office
        String p1Code = resolveOfficeCode(mapping.getPriority1OfficeName());
        OfficeCodeMaster p1Office = officeCodeRepo.findByOfficeCodeForUpdate(p1Code).orElse(null);
        if (p1Office != null && p1Office.getCounter() < globalThreshold) {
            p1Office.setCounter(p1Office.getCounter() + 1);
            officeCodeRepo.save(p1Office);
            log.info("Overflow: {} threshold met, assigned to Priority1 {} (counter {}/{})",
                    targetOffice.getOfficeName(), p1Office.getOfficeName(), p1Office.getCounter(), globalThreshold);
            return p1Code;
        }

        // Try Priority 2 office
        String p2Code = resolveOfficeCode(mapping.getPriority2OfficeName());
        OfficeCodeMaster p2Office = officeCodeRepo.findByOfficeCodeForUpdate(p2Code).orElse(null);
        if (p2Office != null && p2Office.getCounter() < globalThreshold) {
            p2Office.setCounter(p2Office.getCounter() + 1);
            officeCodeRepo.save(p2Office);
            log.info("Overflow: {} and {} thresholds met, assigned to Priority2 {} (counter {}/{})",
                    targetOffice.getOfficeName(), mapping.getPriority1OfficeName(),
                    p2Office.getOfficeName(), p2Office.getCounter(), globalThreshold);
            return p2Code;
        }

        // ALL 3 offices met threshold — reset all 3 counters and restart from target
        log.info("All 3 offices ({}, {}, {}) met threshold — resetting counters",
                targetOffice.getOfficeName(), mapping.getPriority1OfficeName(), mapping.getPriority2OfficeName());

        officeCodeRepo.resetCounters(List.of(targetOfficeCode, p1Code, p2Code));

        // Re-fetch target after reset and assign
        targetOffice = officeCodeRepo.findByOfficeCodeForUpdate(targetOfficeCode).orElse(targetOffice);
        targetOffice.setCounter(1);
        officeCodeRepo.save(targetOffice);
        return targetOfficeCode;
    }

    private int getGlobalThreshold() {
        return thresholdConfigRepo.findById(1)
                .map(OfficeGlobalThresholdConfig::getThresholdValue)
                .orElse(2);
    }

    private String resolveOfficeName(String department, String state, String district) {
        if (state == null || state.isBlank()) {
            return getDefaultOffice(department);
        }

        // First try: search by district if available (handles split-jurisdiction cases like Mumbai-I/II, Chennai-I/II)
        if (district != null && !district.isBlank()) {
            List<OmbudsmanOfficeMaster> districtMatches = ombudsmanOfficeRepo.findByJurisdictionContainingState(district);
            if (districtMatches.size() == 1) {
                return districtMatches.get(0).getOfficeName();
            }
            if (districtMatches.size() > 1) {
                for (OmbudsmanOfficeMaster office : districtMatches) {
                    if (isDistrictIncluded(office.getJurisdiction(), district)) {
                        return office.getOfficeName();
                    }
                }
                return districtMatches.get(0).getOfficeName();
            }
        }

        // Second try: search by state name
        List<OmbudsmanOfficeMaster> offices = ombudsmanOfficeRepo.findByJurisdictionContainingState(state);

        if (offices.isEmpty()) {
            log.warn("No ombudsman office found for state '{}', using default for department '{}'", state, department);
            return getDefaultOffice(department);
        }

        if (offices.size() == 1) {
            return offices.get(0).getOfficeName();
        }

        // Multiple offices match for same state (e.g., UP → Dehradun, Kanpur, New Delhi-II)
        if (district != null && !district.isBlank()) {
            for (OmbudsmanOfficeMaster office : offices) {
                if (isDistrictIncluded(office.getJurisdiction(), district)) {
                    return office.getOfficeName();
                }
            }

            // District not explicitly mentioned — belongs to the catch-all office
            for (OmbudsmanOfficeMaster office : offices) {
                String jurisdiction = office.getJurisdiction().toLowerCase();
                String stateLower = state.toLowerCase();
                if (jurisdiction.contains(stateLower)) {
                    if (jurisdiction.contains("excluding")) {
                        if (!isDistrictExcluded(jurisdiction, district)) {
                            return office.getOfficeName();
                        }
                    }
                }
            }

            for (OmbudsmanOfficeMaster office : offices) {
                String jurisdiction = office.getJurisdiction().toLowerCase();
                if (jurisdiction.contains("viz.") && jurisdiction.contains(district.toLowerCase())) {
                    return office.getOfficeName();
                }
            }
        }

        return offices.get(0).getOfficeName();
    }

    private boolean isDistrictIncluded(String jurisdiction, String district) {
        String jLower = jurisdiction.toLowerCase();
        String dLower = district.toLowerCase();
        if (!jLower.contains(dLower)) return false;

        int districtPos = jLower.indexOf(dLower);
        int excludingPos = jLower.lastIndexOf("excluding", districtPos);
        int exceptPos = jLower.lastIndexOf("except", districtPos);
        int negationPos = Math.max(excludingPos, exceptPos);

        if (negationPos == -1) return true;

        int closingBracket = jLower.indexOf(")", negationPos);
        return closingBracket != -1 && closingBracket < districtPos;
    }

    private boolean isDistrictExcluded(String jurisdictionLower, String district) {
        String dLower = district.toLowerCase();
        int excludingPos = jurisdictionLower.indexOf("excluding");
        if (excludingPos == -1) {
            excludingPos = jurisdictionLower.indexOf("except");
        }
        if (excludingPos == -1) return false;

        String exclusionClause = jurisdictionLower.substring(excludingPos);
        return exclusionClause.contains(dLower);
    }

    private String resolveOfficeCode(String officeName) {
        Optional<OfficeCodeMaster> exact = officeCodeRepo.findByOfficeNameAndIsActiveTrue(officeName);
        if (exact.isPresent()) {
            return exact.get().getOfficeCode();
        }

        String normalized = officeName.replace("-", " ");
        exact = officeCodeRepo.findByOfficeNameAndIsActiveTrue(normalized);
        if (exact.isPresent()) {
            return exact.get().getOfficeCode();
        }

        log.warn("No office code found for '{}', defaulting to '014' (New Delhi I)", officeName);
        return "014";
    }

    private String getDefaultOffice(String department) {
        if ("CEPC".equalsIgnoreCase(department)) {
            return "New Delhi I";
        }
        return "New Delhi I";
    }

    String computeFinancialYear(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();

        if (month >= 4) {
            return String.valueOf(year) + String.valueOf((year + 1) % 100);
        } else {
            return String.valueOf(year - 1) + String.format("%02d", year % 100);
        }
    }

    private int getNextSequence(String officeCode, String financialYear) {
        Optional<ComplaintNumberSequence> existing =
                sequenceRepo.findByOfficeCodeAndFinancialYearForUpdate(officeCode, financialYear);

        if (existing.isPresent()) {
            ComplaintNumberSequence seq = existing.get();
            seq.setLastSequence(seq.getLastSequence() + 1);
            seq.setUpdatedAt(LocalDateTime.now());
            sequenceRepo.save(seq);
            return seq.getLastSequence();
        } else {
            ComplaintNumberSequence newSeq = ComplaintNumberSequence.builder()
                    .officeCode(officeCode)
                    .financialYear(financialYear)
                    .lastSequence(1)
                    .updatedAt(LocalDateTime.now())
                    .build();
            sequenceRepo.save(newSeq);
            return 1;
        }
    }
}
