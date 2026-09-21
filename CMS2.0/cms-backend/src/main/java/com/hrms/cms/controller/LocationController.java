package com.hrms.cms.controller;

import com.hrms.cms.dto.location.BankBranchLookupResponse;
import com.hrms.cms.dto.location.PostOfficeResponse;
import com.hrms.cms.entity.Bank;
import com.hrms.cms.entity.Pincode;
import com.hrms.cms.repository.BankBranchRepository;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.PincodeRepository;
import com.rbi.cms.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/location")
@RequiredArgsConstructor
public class LocationController {

    private final PincodeRepository pincodeRepository;
    private final BankRepository bankRepository;
    private final BankBranchRepository bankBranchRepository;

    // Real bank branches (sourced from the official IFSC registry), scoped to a specific bank +
    // pincode — distinct from /pincode/{pincode} below, which only has generic post-office data
    // and was being mislabeled as "branch name" for every entity, not just actual banks.
    @GetMapping("/bank-branches")
    public ResponseEntity<ApiResponse<BankBranchLookupResponse>> getBankBranches(
            @RequestParam String entityName, @RequestParam String pincode) {
        List<Bank> banks = bankRepository.findByNameFuzzyMatch(entityName.trim());
        if (banks.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(
                    BankBranchLookupResponse.builder().matchedBank(false).build(),
                    "No regulated entity matched \"" + entityName.trim() + "\""));
        }

        Bank bank = banks.get(0);
        List<BankBranchLookupResponse.Branch> branches =
                bankBranchRepository.findByBankIdAndPincode(bank.getId(), pincode).stream()
                        .map(b -> BankBranchLookupResponse.Branch.builder()
                                .ifsc(b.getIfsc())
                                .branchName(b.getBranchName())
                                .address(b.getAddress())
                                .city(b.getCity())
                                .district(b.getDistrict())
                                .state(b.getState())
                                .pincode(b.getPincode())
                                .build())
                        .toList();

        return ResponseEntity.ok(ApiResponse.success(BankBranchLookupResponse.builder()
                .matchedBank(true)
                .bankName(bank.getName())
                .branches(branches)
                .build(), branches.size() + " branch(es) found"));
    }

    /**
     * Post offices serving a pincode, newest-first as the table holds them. An unknown pincode is an
     * empty list rather than an error: the forms fall back to a bundled offline table, and a 404 here
     * would be retried twice by the client's error interceptor for what is a routine typo.
     */
    @GetMapping("/pincode/{pincode}")
    public ResponseEntity<ApiResponse<List<PostOfficeResponse>>> lookupPincode(@PathVariable String pincode) {
        if (pincode == null || !pincode.matches("^\\d{6}$")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid pincode format. Must be 6 digits."));
        }

        List<Pincode> results = pincodeRepository.findByPincode(pincode);
        if (results.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(
                    List.of(), "No records found for pincode " + pincode));
        }

        List<PostOfficeResponse> postOffices = results.stream()
                .map(p -> PostOfficeResponse.builder()
                        .name(p.getOfficeName())
                        .district(p.getDistrict())
                        .state(p.getState())
                        .region(p.getRegion())
                        .division(p.getDivision())
                        .branchType(p.getOfficeType())
                        .pincode(p.getPincode())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.success(
                postOffices, "Number of pincode(s) found: " + results.size()));
    }

    @GetMapping("/districts")
    public ResponseEntity<ApiResponse<List<String>>> getDistricts(@RequestParam String state) {
        return ResponseEntity.ok(ApiResponse.success(
                pincodeRepository.findDistinctDistrictsByState(state), "OK"));
    }

    @GetMapping("/states")
    public ResponseEntity<ApiResponse<List<String>>> getStates() {
        return ResponseEntity.ok(ApiResponse.success(pincodeRepository.findDistinctStates(), "OK"));
    }
}
