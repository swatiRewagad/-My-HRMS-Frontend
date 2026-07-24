package com.hrms.cms.service;

import com.hrms.cms.entity.AutoClosureQuestion;
import com.hrms.cms.repository.AutoClosureQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoClosureService {

    private final AutoClosureQuestionRepository questionRepository;

    public List<AutoClosureQuestion> getQuestions(String schemeVersion, String entityType) {
        List<AutoClosureQuestion> questions = questionRepository
                .findBySchemeVersionAndEntityTypeAndActiveTrueOrderByQuestionNumberAsc(schemeVersion, entityType);
        if (questions.isEmpty()) {
            return getDefaultQuestions(schemeVersion, entityType);
        }
        return questions;
    }

    public Map<String, Object> evaluateResponse(String schemeVersion, String entityType,
                                                  int questionNumber, String answer) {
        List<AutoClosureQuestion> questions = getQuestions(schemeVersion, entityType);
        AutoClosureQuestion q = questions.stream()
                .filter(qu -> qu.getQuestionNumber() == questionNumber)
                .findFirst().orElse(null);

        if (q == null) {
            return Map.of("outcome", "NEXT", "nextQuestion", questionNumber + 1);
        }

        String outcome = "YES".equalsIgnoreCase(answer) ? q.getOutcomeOnYes() : q.getOutcomeOnNo();
        String details = "YES".equalsIgnoreCase(answer) ? q.getOutcomeDetailsYes() : q.getOutcomeDetailsNo();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionNumber", questionNumber);
        result.put("answer", answer);
        result.put("outcome", outcome != null ? outcome : "NEXT");
        result.put("clauseReference", q.getClauseReference());
        result.put("details", details);

        if ("NEXT".equals(outcome) || outcome == null) {
            int nextQ = questionNumber + 1;
            if (q.isSkipIfPreviousYes() && "YES".equalsIgnoreCase(answer) && q.getSkipAfterQuestion() > 0) {
                nextQ = q.getSkipAfterQuestion() + 1;
            }
            result.put("nextQuestion", nextQ);
            result.put("isComplete", nextQ > questions.size());
        } else {
            result.put("nextQuestion", -1);
            result.put("isComplete", true);
        }

        return result;
    }

    public Map<String, Object> evaluateAllResponses(String schemeVersion, String entityType,
                                                     List<Map<String, String>> responses) {
        List<AutoClosureQuestion> questions = getQuestions(schemeVersion, entityType);
        String finalOutcome = "NEW_COMPLAINT";
        String clauseReference = "";
        boolean subJudice = false;

        for (Map<String, String> resp : responses) {
            int qNum = Integer.parseInt(resp.getOrDefault("questionNumber", "0"));
            String answer = resp.getOrDefault("answer", "NO");

            AutoClosureQuestion q = questions.stream()
                    .filter(qu -> qu.getQuestionNumber() == qNum)
                    .findFirst().orElse(null);
            if (q == null) continue;

            String outcome = "YES".equalsIgnoreCase(answer) ? q.getOutcomeOnYes() : q.getOutcomeOnNo();
            if (outcome != null && !"NEXT".equals(outcome)) {
                finalOutcome = outcome;
                clauseReference = q.getClauseReference();
                if ("SUB_JUDICE".equals(outcome)) {
                    subJudice = true;
                    finalOutcome = "NEW_COMPLAINT";
                }
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outcome", finalOutcome);
        result.put("clauseReference", clauseReference);
        result.put("subJudice", subJudice);
        result.put("generateComplaintNumber", !"CRPC_REJECTION".equals(finalOutcome) && !"NOT_A_COMPLAINT".equals(finalOutcome));
        result.put("createReRecord", "NEW_COMPLAINT".equals(finalOutcome) || "SENT_TO_OTHER_REG".equals(finalOutcome));
        return result;
    }

    private List<AutoClosureQuestion> getDefaultQuestions(String schemeVersion, String entityType) {
        List<AutoClosureQuestion> defaults = new ArrayList<>();

        if ("RBIOS_2026".equals(schemeVersion) && "RBIO".equals(entityType)) {
            defaults.add(buildQuestion(1, "Is the entity regulated by RBI?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(1)(a)"));
            defaults.add(buildQuestion(2, "Has the complainant approached the entity first?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(1)(b)"));
            defaults.add(buildQuestion(3, "Was the complaint filed within 1 year from the date of rejection/no reply?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(1)(c)"));
            defaults.add(buildQuestion(4, "Is the complaint NOT already dealt with by the Ombudsman?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(1)(d)"));
            defaults.add(buildQuestion(5, "Is the complaint NOT related to employer-employee dispute?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(1)(e)"));
            defaults.add(buildQuestion(6, "Is the complaint NOT pending/decided by any court/tribunal/arbitrator?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(1)(f)"));
            defaults.add(buildQuestion(7, "Does the complaint relate to grounds of complaint listed in schedule?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(1)(g)"));
            defaults.add(buildQuestion(8, "Is the complaint amount within ₹2 Crore?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(1)(h)"));
            defaults.add(buildQuestion(9, "Has the complainant NOT filed a complaint for the same subject matter?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(2)(a)"));
            defaults.add(buildQuestion(10, "Has the complainant NOT filed an appeal for the same subject?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(2)(b)"));
            defaults.add(buildQuestion(11, "Is the complaint NOT frivolous or vexatious?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(2)(c)"));
            defaults.add(buildQuestion(12, "Is the complaint NOT beyond the scope of the scheme?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(2)(d)"));
            defaults.add(buildQuestion(13, "Is there sufficient basis for the complaint?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(2)(e)"));
            defaults.add(buildQuestion(14, "Is the relief sought within the competence of the Ombudsman?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(2)(f)"));
            defaults.add(buildQuestion(15, "Is the matter currently sub-judice (pending before any court/forum)?", "NO", "SUB_JUDICE", "NEXT", "Clause 10(1)(f) Sub-Judice"));
            defaults.add(buildQuestion(16, "Has the complainant filed any case in any court/forum?", "NO", "SUB_JUDICE", "NEXT", "Clause 10(1)(f)"));
        } else if ("RBIOS_2026".equals(schemeVersion) && "CEPC".equals(entityType)) {
            defaults.add(buildQuestion(1, "Is the entity regulated by RBI?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(1)(a)"));
            defaults.add(buildQuestion(2, "Has the complainant NOT obtained First Resolution from the entity (FRC)?", "NO", "NEXT", "CRPC_REJECTION", "Clause FRC"));
            defaults.add(buildQuestion(3, "Is the complaint NOT already dealt with?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(1)(d)"));
            defaults.add(buildQuestion(4, "Is the complaint NOT related to employer-employee dispute?", "YES", "NEXT", "CRPC_REJECTION", "Clause 10(1)(e)"));
            defaults.add(buildQuestion(5, "Is the complaint directly addressed to RBI?", "YES", "NEXT", "CRPC_REJECTION", "Clause Direct"));
        } else {
            // RBIOS_2021 legacy 10-question
            defaults.add(buildQuestion(1, "Is the entity regulated by RBI?", "YES", "NEXT", "SENT_TO_OTHER_REG", "Clause 8(1)(a)"));
            defaults.add(buildQuestion(2, "Has the complainant approached the entity?", "YES", "NEXT", "CRPC_REJECTION", "Clause 8(1)(b)"));
            defaults.add(buildQuestion(3, "Filed within limitation period?", "YES", "NEXT", "CRPC_REJECTION", "Clause 8(1)(c)"));
            defaults.add(buildQuestion(4, "Not dealt with by Ombudsman?", "YES", "NEXT", "CRPC_REJECTION", "Clause 8(1)(d)"));
            defaults.add(buildQuestion(5, "Not employer-employee dispute?", "YES", "NEXT", "CRPC_REJECTION", "Clause 8(1)(e)"));
            defaults.add(buildQuestion(6, "Not sub-judice?", "YES", "NEXT", "CRPC_REJECTION", "Clause 8(1)(f)"));
            defaults.add(buildQuestion(7, "Relates to schedule grounds?", "YES", "NEXT", "CRPC_REJECTION", "Clause 8(1)(g)"));
            defaults.add(buildQuestion(8, "Amount within limit?", "YES", "NEXT", "CRPC_REJECTION", "Clause 8(1)(h)"));
            defaults.add(buildQuestion(9, "Not frivolous?", "YES", "NEXT", "CRPC_REJECTION", "Clause 8(2)"));
            defaults.add(buildQuestion(10, "Sufficient basis?", "YES", "NEXT", "CRPC_REJECTION", "Clause 8(3)"));
        }

        return defaults;
    }

    private AutoClosureQuestion buildQuestion(int num, String text, String defaultAns,
                                               String outcomeYes, String outcomeNo, String clause) {
        return AutoClosureQuestion.builder()
                .questionNumber(num)
                .questionText(text)
                .defaultAnswer(defaultAns)
                .outcomeOnYes(outcomeYes)
                .outcomeOnNo(outcomeNo)
                .clauseReference(clause)
                .active(true)
                .build();
    }
}
