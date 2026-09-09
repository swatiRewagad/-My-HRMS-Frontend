package com.hrms.cms.controller;

import com.hrms.cms.entity.Bank;
import com.hrms.cms.entity.BankBranch;
import com.hrms.cms.entity.Pincode;
import com.hrms.cms.repository.BankBranchRepository;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.PincodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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
    public Map<String, Object> getBankBranches(@RequestParam String entityName, @RequestParam String pincode) {
        List<Bank> banks = bankRepository.findByNameFuzzyMatch(entityName.trim());
        Map<String, Object> response = new LinkedHashMap<>();
        if (banks.isEmpty()) {
            response.put("success", true);
            response.put("matchedBank", false);
            response.put("data", List.of());
            return response;
        }

        Bank bank = banks.get(0);
        List<BankBranch> branches = bankBranchRepository.findByBankIdAndPincode(bank.getId(), pincode);
        List<Map<String, Object>> data = branches.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ifsc", b.getIfsc());
            m.put("branchName", b.getBranchName());
            m.put("address", b.getAddress());
            m.put("city", b.getCity());
            m.put("district", b.getDistrict());
            m.put("state", b.getState());
            m.put("pincode", b.getPincode());
            return m;
        }).collect(Collectors.toList());

        response.put("success", true);
        response.put("matchedBank", true);
        response.put("bankName", bank.getName());
        response.put("data", data);
        return response;
    }

    @GetMapping("/pincode/{pincode}")
    public List<Map<String, Object>> lookupPincode(@PathVariable String pincode) {
        if (pincode == null || !pincode.matches("^\\d{6}$")) {
            return List.of(Map.of("Status", "Error", "Message", "Invalid pincode format. Must be 6 digits."));
        }

        List<Pincode> results = pincodeRepository.findByPincode(pincode);
        if (results.isEmpty()) {
            return List.of(Map.of("Status", "Error", "Message", "No records found for pincode " + pincode));
        }

        List<Map<String, Object>> postOffices = results.stream().map(p -> {
            Map<String, Object> po = new LinkedHashMap<>();
            po.put("Name", p.getOfficeName());
            po.put("District", p.getDistrict());
            po.put("State", p.getState());
            po.put("Region", p.getRegion());
            po.put("Division", p.getDivision());
            po.put("BranchType", p.getOfficeType());
            po.put("Pincode", p.getPincode());
            return po;
        }).collect(Collectors.toList());

        return List.of(Map.of(
            "Status", "Success",
            "Message", "Number of pincode(s) found: " + results.size(),
            "PostOffice", postOffices
        ));
    }

    @GetMapping("/districts")
    public Map<String, Object> getDistricts(@RequestParam String state) {
        List<String> districts = pincodeRepository.findDistinctDistrictsByState(state);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", districts);
        return response;
    }

    @GetMapping("/states")
    public Map<String, Object> getStates() {
        List<String> states = pincodeRepository.findDistinctStates();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", states);
        return response;
    }
}
