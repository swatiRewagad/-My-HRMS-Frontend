package com.rbi.cms.eligibility.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbi.cms.common.enums.EligibilityOutcome;
import com.rbi.cms.eligibility.dto.*;
import com.rbi.cms.eligibility.entity.EligibilityAudit;
import com.rbi.cms.eligibility.entity.QuestionMaster;
import com.rbi.cms.eligibility.mapper.QuestionMapper;
import com.rbi.cms.eligibility.repository.EligibilityAuditRepository;
import com.rbi.cms.eligibility.repository.QuestionMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EligibilityService {

    private final QuestionMasterRepository questionRepository;
    private final EligibilityAuditRepository auditRepository;
    private final QuestionMapper questionMapper;
    private final KieContainer kieContainer;
    private final ObjectMapper objectMapper;

    public List<QuestionResponse> getActiveQuestions() {
        log.info("Fetching active eligibility questions");
        List<QuestionMaster> questions = questionRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        return questionMapper.toResponseList(questions);
    }

    public List<QuestionResponse> getQuestionsByCategory(String category) {
        log.info("Fetching eligibility questions for category: {}", category);
        List<QuestionMaster> questions = questionRepository.findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(category);
        return questionMapper.toResponseList(questions);
    }

    @Transactional
    public EligibilityCheckResponse evaluateEligibility(EligibilityCheckRequest request) {
        log.info("Evaluating eligibility for session: {}, channel: {}", request.getSessionId(), request.getChannel());

        EligibilityFact fact = buildFact(request.getAnswers());

        KieSession kieSession = kieContainer.newKieSession();
        try {
            kieSession.insert(fact);
            kieSession.fireAllRules();
        } finally {
            kieSession.dispose();
        }

        EligibilityCheckResponse response = buildResponse(fact);
        persistAudit(request, response);

        log.info("Eligibility outcome for session {}: {}", request.getSessionId(), response.getOutcome());
        return response;
    }

    private EligibilityFact buildFact(Map<String, String> answers) {
        return EligibilityFact.builder()
                .courtMatterPending(!parseBoolean(answers.get("NOT_IN_COURT")))
                .approachedBank(parseBoolean(answers.get("APPROACHED_BANK")))
                .waitingPeriodCompleted(parseBoolean(answers.get("DAYS_ELAPSED")))
                .duplicateComplaint(false)
                .jurisdictionCode(answers.getOrDefault("JURISDICTION", ""))
                .complaintCategory(answers.getOrDefault("COMPLAINT_CATEGORY", ""))
                .eligible(true)
                .build();
    }

    private boolean parseBoolean(String value) {
        if (value == null) return false;
        return "YES".equalsIgnoreCase(value) || "TRUE".equalsIgnoreCase(value) || "1".equals(value);
    }

    private EligibilityCheckResponse buildResponse(EligibilityFact fact) {
        EligibilityOutcome outcome = fact.isEligible() ? EligibilityOutcome.ELIGIBLE : EligibilityOutcome.NOT_ELIGIBLE;
        String nextAction = fact.isEligible() ? "PROCEED_TO_REGISTRATION" : "SHOW_ADVISORY";

        return EligibilityCheckResponse.builder()
                .outcome(outcome)
                .reasonCode(fact.getReasonCode())
                .reasonMessage(fact.getReasonMessage())
                .standardResponse(fact.isEligible() ? null : buildStandardResponse(fact.getReasonCode()))
                .nextAction(nextAction)
                .reference(fact.isEligible() ? null : buildReference(fact.getReasonCode()))
                .build();
    }

    private String buildStandardResponse(String reasonCode) {
        return switch (reasonCode) {
            case "COURT_MATTER_PENDING" ->
                    "Your complaint cannot be registered as the matter is currently sub-judice. " +
                            "Please approach the Banking Ombudsman after court proceedings are completed.";
            case "BANK_NOT_APPROACHED" ->
                    "Please first approach your bank/financial institution with your grievance. " +
                            "You may file a complaint with RBI if the issue is not resolved within 30 days.";
            case "WAITING_PERIOD_NOT_COMPLETED" ->
                    "The mandatory waiting period of 30 days has not been completed. " +
                            "Please wait for the bank to respond before filing a complaint.";
            case "DUPLICATE_COMPLAINT" ->
                    "A complaint with similar details is already registered. " +
                            "Please track your existing complaint using the reference number.";
            default -> "Your complaint cannot be registered at this time. Please review the eligibility criteria.";
        };
    }

    private String buildReference(String reasonCode) {
        return switch (reasonCode) {
            case "COURT_MATTER_PENDING" -> "RBI BO Regulation 2021, Section 8(1)(a)";
            case "BANK_NOT_APPROACHED" -> "RBI BO Regulation 2021, Section 7(1)";
            case "WAITING_PERIOD_NOT_COMPLETED" -> "RBI BO Regulation 2021, Section 7(2)";
            case "DUPLICATE_COMPLAINT" -> "RBI CMS Policy - Duplicate Prevention";
            default -> "RBI Integrated Ombudsman Scheme 2021";
        };
    }

    private void persistAudit(EligibilityCheckRequest request, EligibilityCheckResponse response) {
        String answersJson;
        try {
            answersJson = objectMapper.writeValueAsString(request.getAnswers());
        } catch (JsonProcessingException e) {
            answersJson = request.getAnswers().toString();
        }

        EligibilityAudit audit = EligibilityAudit.builder()
                .sessionId(request.getSessionId())
                .channel(request.getChannel())
                .answersJson(answersJson)
                .outcome(response.getOutcome())
                .reasonCode(response.getReasonCode())
                .reasonMessage(response.getReasonMessage())
                .standardResponse(response.getStandardResponse())
                .ipAddress(request.getIpAddress())
                .build();

        auditRepository.save(audit);
    }
}
