package com.hrms.cms.config;

import com.hrms.cms.entity.EligibilityQuestionMaster;
import com.hrms.cms.repository.EligibilityQuestionMasterRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds ELIGIBILITY_QUESTION_MASTER with the RBIOS_2021 maintainability questions.
 *
 * The questions and their clause references were previously hardcoded in the Angular
 * file-complaint component, which meant a Scheme amendment required a frontend release — and had
 * already drifted (the component cited "Scheme, 2026" for clauses of the 2021 Scheme).
 */
@Component
@Order(7)
public class EligibilityQuestionMasterSeeder implements CommandLineRunner {

    private static final String SCHEME = "RBIOS_2021";
    private static final String SCHEME_NAME = "Reserve Bank - Integrated Ombudsman Scheme, 2021";

    private final EligibilityQuestionMasterRepository repo;

    public EligibilityQuestionMasterSeeder(EligibilityQuestionMasterRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int n = 0;

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("regulatedEntity")
                .applicableEntityType("ALL")
                .questionType("select")
                .questionText("Select Regulated Entity Name")
                .translationKey("eligibility.q_select_re")
                .build());

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("filedWithRE")
                .applicableEntityType("ALL")
                .questionType("radio")
                .questionText("Have you filed a written / electronic complaint with the {{reName}}?")
                .translationKey("eligibility.q_filed_with_re")
                .blockOn("no")
                .clauseReference("10(1)(j)")
                .blockMessage("in terms of clause {{clause}} of " + SCHEME_NAME + ", "
                        + "the complaint cannot be processed under the Scheme.")
                .blockMessageKey("eligibility.block_not_filed")
                .nonMaintainable(true)
                .build());

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("receivedReply")
                .applicableEntityType("ALL")
                .questionType("radio")
                .questionText("Have you received any reply from the Entity?")
                .translationKey("eligibility.q_received_reply")
                .nonMaintainable(true)
                .build());

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("sentReminder")
                .applicableEntityType("ALL")
                .questionType("radio")
                .questionText("Have you sent any reminder to the {{reName}}?")
                .translationKey("eligibility.q_sent_reminder")
                .nonMaintainable(true)
                .build());

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("isSubJudice")
                .applicableEntityType("RBIO")
                .questionType("radio")
                .questionText("Is the complaint relating to the same grievance which is already pending before any "
                        + "Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum (excluding "
                        + "criminal proceedings pending or decided before a Court/ Tribunal or any police "
                        + "investigation initiated in a criminal offence)?")
                .translationKey("eligibility.q_sub_judice")
                .blockOn("yes")
                .clauseReference("10(2)(b)(ii)")
                .blockMessage("As your complaint is sub-judice/under arbitration/already dealt with on merits by a "
                        + "Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under clause "
                        + "{{clause}} of the " + SCHEME_NAME + ".")
                .blockMessageKey("eligibility.block_sub_judice")
                .nonMaintainable(true)
                .simplifiedText("Have you already taken this exact problem to a court, arbitrator, or another "
                        + "official legal authority (excluding criminal cases or police investigations)?")
                .simplifiedTextKey("eligibility.q_sub_judice_simple")
                .build());

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("alreadySettled")
                .applicableEntityType("RBIO")
                .questionType("radio")
                .questionText("Is the complaint relating to the same grievance which is already settled or dealt "
                        + "before any Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum "
                        + "(excluding criminal proceedings pending or decided before a Court/ Tribunal or any police "
                        + "investigation initiated in a criminal offence)?")
                .translationKey("eligibility.q_already_settled")
                .blockOn("yes")
                .clauseReference("10(2)(b)(ii)")
                .blockMessage("As your complaint has already been settled or dealt with by a "
                        + "Court/Tribunal/Arbitrator/Authority, it will be closed as Non-Maintainable under the "
                        + SCHEME_NAME + ".")
                .blockMessageKey("eligibility.block_already_settled")
                .nonMaintainable(true)
                .simplifiedText("Has this exact problem already been resolved by a court, arbitrator, or another "
                        + "official legal authority (excluding criminal cases or police investigations)?")
                .simplifiedTextKey("eligibility.q_already_settled_simple")
                .build());

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("throughAdvocateEligibility")
                .applicableEntityType("RBIO")
                .questionType("radio")
                .questionText("Is your complaint being made through an advocate?")
                .translationKey("eligibility.q_through_advocate")
                .build());

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("pendingBeforeOmbudsman")
                .applicableEntityType("RBIO")
                .questionType("radio")
                .questionText("Is the complaint relating to the same grievance which is already pending before the "
                        + "Ombudsman?")
                .translationKey("eligibility.q_pending_ombudsman")
                .blockOn("yes")
                .clauseReference("10(2)(a)")
                .blockMessage("Your complaint is already pending before the Ombudsman on the same grievance. "
                        + "Duplicate complaints cannot be filed.")
                .blockMessageKey("eligibility.block_pending_ombudsman")
                .nonMaintainable(true)
                .build());

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("settledByOmbudsman")
                .applicableEntityType("RBIO")
                .questionType("radio")
                .questionText("Is the complaint relating to the same grievance which is already settled or dealt "
                        + "with on merits by the Ombudsman?")
                .translationKey("eligibility.q_settled_ombudsman")
                .blockOn("yes")
                .clauseReference("10(2)(b)(i)")
                .blockMessage("Your complaint has already been settled or dealt with on merits by the Ombudsman. "
                        + "You cannot file a fresh complaint on the same issue.")
                .blockMessageKey("eligibility.block_settled_ombudsman")
                .nonMaintainable(true)
                .build());

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("staffOfRE")
                .applicableEntityType("RBIO")
                .questionType("radio")
                .questionText("Is the Complainant a staff of the RE and complaint involves employer-employee "
                        + "relationship?")
                .translationKey("eligibility.q_staff_of_re")
                .blockOn("yes")
                .clauseReference("10(1)(g)")
                .blockMessage("As the complaint involves the employer-employee relationship with the Regulated "
                        + "Entity, it cannot be processed under the Integrated Ombudsman Scheme, 2021.")
                .blockMessageKey("eligibility.block_staff_of_re")
                .nonMaintainable(true)
                .build());

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("previouslyFiledWithCEPC")
                .applicableEntityType("CEPC")
                .questionType("radio")
                .questionText("Have you previously filed a complaint on the same subject matter with CEPC/RBI "
                        + "Ombudsman?")
                .translationKey("eligibility.q_previously_filed_cepc")
                .blockOn("yes")
                .clauseReference("10(2)(a)")
                .blockMessage("As your complaint on the same subject matter has already been filed with CEPC/RBI, "
                        + "it will be closed as Non-Maintainable under the " + SCHEME_NAME + ".")
                .blockMessageKey("eligibility.block_previously_filed_cepc")
                .nonMaintainable(true)
                .build());

        // BRD rows 20/21 contradict each other: row 20 says "Yes" to employment auto-closes, which
        // would make row 21 unreachable. Employment alone is not a bar under the Scheme — only a
        // grievance *arising from* the employer-employee relationship is. So 20 is a gate that merely
        // reveals 21, and 21 carries the block. Do not add blockOn to this row.
        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("employeeOfRE")
                .applicableEntityType("CEPC")
                .questionType("radio")
                .questionText("Are / were you an employee of the Regulated Entity against whom this complaint is "
                        + "being filed?")
                .translationKey("eligibility.q_employee_of_re")
                .nonMaintainable(true)
                .build());

        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("employerRelationship")
                .applicableEntityType("CEPC")
                .questionType("radio")
                .questionText("If Yes, Is your complaint involves the employee-employer relationship of the "
                        + "Regulated Entity?")
                .translationKey("eligibility.q_employer_relationship")
                .blockOn("yes")
                .clauseReference("10(1)(g)")
                .blockMessage("As your complaint involves the employee-employer relationship with the Regulated "
                        + "Entity, it cannot be processed under the Integrated Ombudsman Scheme, 2021.")
                .blockMessageKey("eligibility.block_employer_relationship")
                .nonMaintainable(true)
                .inlineSubQuestion(true)
                .build());

        // BRD row 15: the advocate follow-up. Previously hardcoded in the Angular template with no
        // master row and no translation key at all, so it rendered in English in all ten locales.
        seed(EligibilityQuestionMaster.builder()
                .questionNumber(++n)
                .questionKey("isComplainantSelf")
                .applicableEntityType("RBIO")
                .questionType("radio")
                .questionText("If Yes, then are you the Complainant?")
                .translationKey("eligibility.sub_are_you_complainant")
                .blockOn("no")
                // clauseReference deliberately left null: the Scheme clause barring an advocate-filed
                // complaint where the filer is not the complainant has NOT been verified. Do not guess
                // one — an unverified citation on a closure denies statutory recourse. Awaiting the
                // authoritative value from the business owner.
                .blockMessage("As per the Integrated Ombudsman Scheme, a complaint filed through an advocate must "
                        + "be filed by the complainant themselves. Since you are not the complainant, this "
                        + "complaint cannot be processed.")
                .blockMessageKey("eligibility.block_advocate_not_complainant")
                .nonMaintainable(true)
                .inlineSubQuestion(true)
                .build());
    }

    /** Insert-if-absent keyed on (schemeVersion, questionKey) so operator edits survive restarts. */
    private void seed(EligibilityQuestionMaster q) {
        q.setSchemeVersion(SCHEME);
        q.setActive(true);
        if (repo.findBySchemeVersionAndQuestionKey(SCHEME, q.getQuestionKey()).isEmpty()) {
            repo.save(q);
        }
    }
}
