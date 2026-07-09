package com.hrms.cms.controller;

import com.hrms.cms.entity.Pincode;
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
