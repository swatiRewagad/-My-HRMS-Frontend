package com.hrms.cms.service;

import com.hrms.cms.entity.ComplaintNumberSequence;
import com.hrms.cms.entity.OfficeCodeMaster;
import com.hrms.cms.entity.OmbudsmanOfficeMaster;
import com.hrms.cms.repository.ComplaintNumberSequenceRepository;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
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

    @Transactional
    public String generateComplaintNumber(String department, String complainantState, String complainantDistrict) {
        String officeName = resolveOfficeName(department, complainantState, complainantDistrict);
        String officeCode = resolveOfficeCode(officeName);
        String financialYear = computeFinancialYear(LocalDate.now());
        int nextSequence = getNextSequence(officeCode, financialYear);

        String complaintNumber = String.format("N%s%s%06d", financialYear, officeCode, nextSequence);
        log.info("Generated complaint number: {} (office={}, FY={}, seq={})", complaintNumber, officeName, financialYear, nextSequence);
        return complaintNumber;
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
                // Multiple offices mention this district — pick the one that includes it (not excludes it)
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
        // Use district to narrow down
        if (district != null && !district.isBlank()) {
            // Check if district is explicitly listed in any office's jurisdiction
            for (OmbudsmanOfficeMaster office : offices) {
                if (isDistrictIncluded(office.getJurisdiction(), district)) {
                    return office.getOfficeName();
                }
            }

            // District not explicitly mentioned — it belongs to the "catch-all" office for that state.
            // The catch-all is the one that says "<State> (excluding ...)" where the district is NOT in the exclusion list.
            for (OmbudsmanOfficeMaster office : offices) {
                String jurisdiction = office.getJurisdiction().toLowerCase();
                String stateLower = state.toLowerCase();
                if (jurisdiction.contains(stateLower)) {
                    // Check if this is the broad/catch-all office (has "excluding" clause for specific districts)
                    if (jurisdiction.contains("excluding")) {
                        // Verify our district is NOT in the exclusion list
                        if (!isDistrictExcluded(jurisdiction, district)) {
                            return office.getOfficeName();
                        }
                    }
                }
            }

            // If no catch-all found, look for office that lists specific districts ("viz.") and includes ours
            for (OmbudsmanOfficeMaster office : offices) {
                String jurisdiction = office.getJurisdiction().toLowerCase();
                if (jurisdiction.contains("viz.") && jurisdiction.contains(district.toLowerCase())) {
                    return office.getOfficeName();
                }
            }
        }

        // Default to first match
        return offices.get(0).getOfficeName();
    }

    private boolean isDistrictIncluded(String jurisdiction, String district) {
        String jLower = jurisdiction.toLowerCase();
        String dLower = district.toLowerCase();
        if (!jLower.contains(dLower)) return false;

        int districtPos = jLower.indexOf(dLower);
        // Check it's not within an "excluding" or "except" clause
        int excludingPos = jLower.lastIndexOf("excluding", districtPos);
        int exceptPos = jLower.lastIndexOf("except", districtPos);
        int negationPos = Math.max(excludingPos, exceptPos);

        if (negationPos == -1) return true;

        // Check if the closing bracket of the exclusion clause is before the district mention
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
        // Normalize office name for matching (e.g., "Chennai-I" -> "Chennai")
        // OFFICE_CODE_MASTER may have simplified names
        Optional<OfficeCodeMaster> exact = officeCodeRepo.findByOfficeNameAndIsActiveTrue(officeName);
        if (exact.isPresent()) {
            return exact.get().getOfficeCode();
        }

        // Try base name (e.g., "Mumbai-I" -> "Mumbai I")
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
            // April onwards: current year to next year
            return String.valueOf(year) + String.valueOf((year + 1) % 100);
        } else {
            // Jan-March: previous year to current year
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
