package com.hrms.cms.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/eligibility")
@RequiredArgsConstructor
public class EligibilityWizardController {

    @Value("${cms.mre.re-window-days:30}")
    private int reWindowDays;

    @Value("${cms.mre.filing-deadline-days:90}")
    private int filingDeadlineDays;

    @PostMapping("/wizard-check")
    public ResponseEntity<?> checkWizardEligibility(@RequestBody Map<String, String> answers) {
        String entityType = answers.getOrDefault("entityType", "");
        String complainedToRE = answers.getOrDefault("complainedToRE", "");
        String reComplaintDate = answers.getOrDefault("reComplaintDate", "");
        String reResponse = answers.getOrDefault("reRespondedSatisfactorily", "");

        Map<String, Object> result = new HashMap<>();

        if ("NO".equals(complainedToRE)) {
            result.put("eligible", false);
            result.put("outcome", "RE_FIRST");
            result.put("message", "You must first file a complaint with your bank/financial institution and wait for their response (up to " + reWindowDays + " days) before approaching RBI.");
            result.put("reWindowDays", reWindowDays);
            result.put("filingDeadlineDays", filingDeadlineDays);
            return ResponseEntity.ok(result);
        }

        if ("RESOLVED".equals(reResponse)) {
            result.put("eligible", false);
            result.put("outcome", "RANT_GATE");
            result.put("message", "Since the institution has already resolved your issue, RBI Ombudsman may not be able to take further action. If you believe the resolution is inadequate, you may still proceed.");
            return ResponseEntity.ok(result);
        }

        if (reComplaintDate != null && !reComplaintDate.isEmpty()) {
            try {
                LocalDate complaintDate = LocalDate.parse(reComplaintDate);
                LocalDate today = LocalDate.now();
                long daysSince = ChronoUnit.DAYS.between(complaintDate, today);

                if (daysSince < reWindowDays && "NO_REPLY".equals(reResponse)) {
                    LocalDate windowOpen = complaintDate.plusDays(reWindowDays);
                    result.put("eligible", false);
                    result.put("outcome", "TOO_EARLY");
                    result.put("message", "Please wait until " + windowOpen + " (" + reWindowDays + " days from your complaint to the entity) before filing with RBI Ombudsman.");
                    result.put("daysRemaining", reWindowDays - daysSince);
                    result.put("windowOpenDate", windowOpen.toString());
                    return ResponseEntity.ok(result);
                }

                long limitDays = 365L;
                if (daysSince > limitDays) {
                    LocalDate deadline = complaintDate.plusDays(limitDays);
                    result.put("eligible", false);
                    result.put("outcome", "TOO_LATE");
                    result.put("message", "Your complaint to the entity was filed over 1 year ago. The filing window under the Scheme has expired. You may still proceed but your complaint may be closed as time-barred.");
                    result.put("filingDeadlineDate", deadline.toString());
                    return ResponseEntity.ok(result);
                }
            } catch (Exception ignored) {
            }
        }

        result.put("eligible", true);
        result.put("outcome", "READY");
        result.put("message", "You are eligible to file a complaint with the RBI Ombudsman under the Integrated Ombudsman Scheme, 2021.");
        result.put("compensationBand", "Up to ₹20 lakh (consequential loss) + ₹1 lakh (mental agony)");
        return ResponseEntity.ok(result);
    }
}
