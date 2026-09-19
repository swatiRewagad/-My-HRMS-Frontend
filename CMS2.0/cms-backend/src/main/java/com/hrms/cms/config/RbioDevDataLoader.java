package com.hrms.cms.config;

import com.hrms.cms.entity.*;
import com.hrms.cms.repository.*;
import com.hrms.cms.service.NodalOfficerRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Seeds a small set of fully populated RBIO complaints so every tab on the complaint details screen
 * has something to show, and one complaint per RBIO dashboard tab so the tab counters are non-zero.
 *
 * <p>This exists because {@code DemoDataSeeder} writes to COMPLAINTS and nothing else. A complaint
 * with no COMPLAINT_RBIO_FORM_DATA, COMPLAINT_ELIGIBILITY_ANSWERS or COMPLAINT_ADDITIONAL_DETAILS row
 * renders every panel on the details screen blank, which is indistinguishable from the screen being
 * broken. Verifying a tab works means having a row behind it.
 *
 * <p><b>The dashboard grid needs one extra step.</b> It does not read this database — it queries
 * OpenSearch through cms-search-service, and cms-backend has no indexing path of its own. After
 * startup, with cms-search-service and OpenSearch up, index what this class wrote:
 * <pre>
 * curl -X POST http://localhost:8091/cms-search/api/v1/search/complaints/reindex/allcomplaints
 * curl -X POST http://localhost:8091/cms-search/api/v1/search/complaints/reindex/allnodalofficers
 * </pre>
 * Both pull from cms-backend's own stream endpoints, so the database is the source of truth either
 * way. Without the reindex the details screen works fully and the dashboard grid stays empty.
 *
 * <p>The dashboard also scopes every search to the caller's own department and regional office, with
 * no role exemption, so a complaint whose {@code regionalOffice} does not equal the signed-in
 * officer's Keycloak {@code regionalOffice} attribute is invisible on the dashboard no matter what
 * else is right. That value is per-developer, hence {@code cms.dev-data.regional-office}.
 */
@Slf4j
@Component
@Profile("dev-local")
@Order(20)
@RequiredArgsConstructor
public class RbioDevDataLoader implements CommandLineRunner {

    private static final String NUMBER_PREFIX = "N2526DEV";
    private static final String DEPARTMENT = "RBIO";

    /** The complainant is deliberately the same across all seeded complaints: the "past complaints"
     *  panel looks the complainant up by email and phone, so sharing one gives every complaint a
     *  populated history instead of leaving the panel empty on all of them. */
    private static final String COMPLAINANT_NAME = "Dev Complainant";
    private static final String COMPLAINANT_EMAIL = "dev.complainant@example.com";
    private static final String COMPLAINANT_PHONE = "9820011223";
    private static final String CMS_EMAIL = "complaints@cms.rbi.org.in";

    private final ComplaintRepository complaintRepository;
    private final ComplaintRbioFormDataRepository formDataRepository;
    private final ComplaintEligibilityAnswerRepository eligibilityRepository;
    private final ComplaintAdditionalDetailRepository additionalDetailRepository;
    private final ConciliationMeetingRepository conciliationMeetingRepository;
    private final ComplaintCommentRepository commentRepository;
    private final ComplaintTimelineRepository timelineRepository;
    private final ReResponseTrackerRepository trackerRepository;
    private final SimulatedEmailRepository simulatedEmailRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final NodalOfficerRecordService nodalOfficerRecordService;
    private final NodalOfficerRecordRepository nodalOfficerRecordRepository;

    /** The signed-in officer these complaints are assigned to. Drives the dashboard's "Assigned to me"
     *  and "Sent back to me" tabs, which filter on assignedOfficer. Defaults to the officer
     *  DataInitializer already assigns its own RBIO complaints to. */
    @Value("${cms.dev-data.officer:rbio_mum1}")
    private String officer;

    /** Must match the officer's Keycloak regionalOffice attribute or the dashboard returns nothing.
     *  Office names come from OFFICE_CODE_MASTER, seeded by DataInitializer. */
    @Value("${cms.dev-data.regional-office:Mumbai-I}")
    private String regionalOffice;

    /**
     * One scenario per dashboard tab. {@code status} uses the UPPERCASE ComplaintStatus constant
     * names: the search service term-filters on those and the details component's status gates
     * compare against them, whereas the older seeders write lower snake_case, which neither reads.
     */
    private record Scenario(String suffix, String status, String subject, String maintainability,
                           boolean withConciliation, boolean forwardedToRe, boolean reResponded) { }

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("000001", "NEW_COMPLAINT",
                    "Unauthorised UPI debit of Rs 48,500 not reversed by the bank",
                    "MAINTAINABLE", true, false, false),
            new Scenario("000002", "DRAFT",
                    "Home loan foreclosure charges levied despite RBI circular",
                    null, false, false, false),
            new Scenario("000003", "MEETING_SCHEDULED",
                    "Credit card annual fee reversed only partially after assurance",
                    "MAINTAINABLE", true, false, false),
            new Scenario("000004", "SENT_BACK_TO_DO",
                    "Cheque returned unpaid though sufficient balance was available",
                    "MAINTAINABLE", true, false, false),
            new Scenario("000005", "INFORMATION_REQUIRED",
                    "Pension arrears not credited for four consecutive months",
                    "MAINTAINABLE", true, true, false),
            new Scenario("000006", "SENT_TO_RBI",
                    "ATM cash not dispensed, account debited Rs 10,000",
                    "MAINTAINABLE", true, true, true),
            new Scenario("000007", "COMPLAINT_WITHDRAWN",
                    "Mis-selling of insurance bundled with a fixed deposit",
                    "NOT_MAINTAINABLE", false, false, false));

    @Override
    public void run(String... args) {
        String firstNumber = NUMBER_PREFIX + SCENARIOS.get(0).suffix();
        if (complaintRepository.findByComplaintNumber(firstNumber).isPresent()) {
            log.info("RBIO dev data already present ({} exists) — skipping", firstNumber);
            return;
        }

        List<RegulatedEntity> entities = rbioEntitiesWithContactDetails();
        if (entities.isEmpty()) {
            log.warn("No regulated entities found, so RBIO dev complaints would have no entity to "
                    + "point at and the nodal officer tab would be blank. Skipping. Check that "
                    + "DataInitializer ran.");
            return;
        }

        Long categoryId = categoryRepository.findAll().stream()
                .map(ComplaintCategory::getId).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);

        for (int i = 0; i < SCENARIOS.size(); i++) {
            Scenario scenario = SCENARIOS.get(i);
            RegulatedEntity entity = entities.get(i % entities.size());
            seed(scenario, entity, categoryId);
        }

        log.info("Seeded {} RBIO dev complaints ({}*) for officer '{}' at regional office '{}'. "
                        + "The details screen works now; run the cms-search-service reindex to populate "
                        + "the dashboard grid.",
                SCENARIOS.size(), NUMBER_PREFIX, officer, regionalOffice);
    }

    /**
     * DataInitializer seeds regulated entities with a name and type only, so their nodal officer and
     * principal nodal officer contact details are null. NodalOfficerRecordService snapshots those
     * onto the record, which means the nodal officer tab shows blank names for every complaint until
     * they are filled in. Only nulls are written, so an entity an operator has already completed is
     * left alone.
     */
    private List<RegulatedEntity> rbioEntitiesWithContactDetails() {
        List<RegulatedEntity> entities = regulatedEntityRepository.findAll().stream()
                .filter(e -> DEPARTMENT.equals(e.getDepartment()))
                .sorted(Comparator.comparing(RegulatedEntity::getId))
                .limit(4)
                .toList();

        for (RegulatedEntity entity : entities) {
            String slug = slug(entity.getName());
            if (entity.getNodalOfficerName() == null) {
                entity.setNodalOfficerName("Nodal Officer — " + entity.getName());
            }
            if (entity.getNodalOfficerEmail() == null) {
                entity.setNodalOfficerEmail("nodal." + slug + "@example.com");
            }
            if (entity.getNodalOfficerPhone() == null) {
                entity.setNodalOfficerPhone("9000100200");
            }
            if (entity.getNodalOfficerDesignation() == null) {
                entity.setNodalOfficerDesignation("Deputy General Manager");
            }
            if (entity.getPnoName() == null) {
                entity.setPnoName("Principal Nodal Officer — " + entity.getName());
            }
            if (entity.getPnoEmail() == null) {
                entity.setPnoEmail("pno." + slug + "@example.com");
            }
            if (entity.getPnoPhone() == null) {
                entity.setPnoPhone("9000300400");
            }
        }
        return regulatedEntityRepository.saveAll(entities);
    }

    private void seed(Scenario scenario, RegulatedEntity entity, Long categoryId) {
        String complaintNumber = NUMBER_PREFIX + scenario.suffix();
        Complaint complaint = saveComplaint(scenario, entity, categoryId, complaintNumber);

        saveFormData(complaint, entity);
        saveEligibilityAnswers(complaint, entity);
        saveAdditionalDetails(complaint);
        saveTimeline(complaint, scenario);
        saveComments(complaintNumber);
        saveEmails(complaint, entity);

        if (scenario.withConciliation()) {
            saveConciliationMeeting(complaint, scenario);
        }

        NodalOfficerRecord record = nodalOfficerRecordService.createForComplaint(complaint);
        if (record != null) {
            saveNodalComments(complaintNumber, record.getRecordNumber());
            if (scenario.forwardedToRe()) {
                recordAssessment(record);
                saveTracker(complaint, entity, scenario.reResponded());
            }
        }
    }

    private Complaint saveComplaint(Scenario scenario, RegulatedEntity entity, Long categoryId,
                                    String complaintNumber) {
        Complaint complaint = new Complaint();
        complaint.setComplaintNumber(complaintNumber);
        complaint.setComplainantName(COMPLAINANT_NAME);
        complaint.setComplainantEmail(COMPLAINANT_EMAIL);
        complaint.setComplainantPhone(COMPLAINANT_PHONE);
        complaint.setComplainantAddress("14, Marine Lines, Mumbai");
        complaint.setComplainantState("Maharashtra");
        complaint.setComplainantDistrict("Mumbai");
        complaint.setComplainantPincode("400020");

        complaint.setRegulatedEntityId(entity.getId());
        complaint.setEntityName(entity.getName());
        complaint.setEntityType(entity.getEntityType());
        complaint.setEntityState("Maharashtra");
        complaint.setEntityDistrict("Mumbai");
        complaint.setEntityCity("Mumbai");
        complaint.setEntityBranchName("Fort Branch");
        complaint.setEntityPincode("400001");

        complaint.setCategoryId(categoryId);
        complaint.setSubject(scenario.subject());
        complaint.setDescription("Seeded by RbioDevDataLoader for the dev-local profile. " + scenario.subject()
                + " The complainant approached the regulated entity first and remains dissatisfied "
                + "with the reply received.");
        complaint.setReliefSought("Reversal of the disputed amount and compensation for the delay.");
        complaint.setAmountInvolved(new BigDecimal("48500.00"));

        complaint.setStatus(scenario.status());
        complaint.setPriority("MEDIUM");
        complaint.setFilingType("PORTAL");
        complaint.setDepartment(DEPARTMENT);
        complaint.setAssignedRole("RBIO_DO");
        complaint.setAssignedOfficer(officer);
        complaint.setAssignedOfficerName("Dev RBIO Officer");
        complaint.setRegionalOffice(regionalOffice);
        complaint.setCreatedBy(officer);
        complaint.setEntityCode(entity.getEntityType());

        complaint.setMaintainabilityDetermination(scenario.maintainability());
        if (scenario.maintainability() != null) {
            complaint.setMaintainabilityDeterminedBy(officer);
            complaint.setMaintainabilityDeterminedAt(LocalDateTime.now().minusDays(3));
        }

        complaint.setPriorReComplaint(Boolean.TRUE);
        complaint.setReComplaintDate(LocalDate.now().minusDays(60));
        complaint.setReComplaintReference("RE/GRV/2026/" + scenario.suffix());
        complaint.setReRepliedAndDissatisfied(Boolean.TRUE);
        complaint.setSchemeVersion("RB-IOS 2021");
        complaint.setSlaDeadline(LocalDateTime.now().plusDays(25));
        complaint.setSlaPriority("MEDIUM");

        // Not @Builder.Default on the entity, so a builder would leave these null and the insert
        // would fail on their NOT NULL constraints. Set explicitly rather than relying on the
        // field initialisers, which only apply to the no-args constructor.
        complaint.setIsRead(Boolean.FALSE);
        complaint.setHasAttachment(Boolean.FALSE);

        return complaintRepository.save(complaint);
    }

    private void saveFormData(Complaint complaint, RegulatedEntity entity) {
        formDataRepository.save(ComplaintRbioFormData.builder()
                .complaintId(complaint.getId())
                .receiptDate(LocalDate.now().minusDays(7))
                .modeOfReceipt("PORTAL")
                .comments("Complaint received through the public portal and triaged as maintainable.")
                .complaintCpgram("NO")
                .moduleName("RBIO")
                .entityCountry("India")
                .branchCenterName("Fort Branch, Mumbai")
                .otherEntityName(entity.getName())
                .registrationWithRbiDate(LocalDate.now().minusYears(12))
                .complaintRegistrationDateValid("YES")
                .dateOfFilingComplaint(LocalDate.now().minusDays(7))
                .legalCaseFiled("NO")
                .preEnquiryReceived("NO")
                .highPriorityComplaint("NO")
                .loanDisposalAmount(new BigDecimal("48500.00"))
                .vernacularLanguage("Marathi")
                .additionalComments("Supporting documents were furnished along with the complaint.")
                .complaintRegardingPension("NO")
                .atmCreditDebitCard("YES")
                .schemeFlag("RB-IOS 2021")
                .groundsFlag("DEFICIENCY_IN_SERVICE")
                .freeMarkedComplaint("NO")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void saveEligibilityAnswers(Complaint complaint, RegulatedEntity entity) {
        eligibilityRepository.save(ComplaintEligibilityAnswer.builder()
                .complaintId(complaint.getId())
                .regulatedEntityId(entity.getId())
                .filedWithRe("YES")
                .receivedReply("YES")
                .sentReminder("YES")
                .isSubJudice("NO")
                .alreadySettled("NO")
                .throughAdvocate("NO")
                .pendingBeforeOmbudsman("NO")
                .settledByOmbudsman("NO")
                .staffOfRe("NO")
                .previouslyFiledWithCepc("NO")
                .employeeOfRe("NO")
                .employerRelationship("NO")
                .entityRegulatedByRbi("YES")
                .complaintNotDirectlyAddressedToOmbudsman("YES")
                .complaintNotRegisteredWithEntity("NO")
                .frivolousVexatiousThreatening("NO")
                .sameGrievancePendingBeforeCourt("NO")
                .sameGrievanceSettledBeforeCourt("NO")
                .complainantIsAdvocate("NO")
                .complaintAgainstManagement("NO")
                .complaintFiledWithCepcOrRbi("NO")
                .disputeBetweenRes("NO")
                .completeInformationUnavailable("NO")
                .proposedComplaintType("DEFICIENCY_IN_SERVICE")
                .firstFiledWithReDate(LocalDate.now().minusDays(60))
                .answeredAt(LocalDateTime.now())
                .build());
    }

    private void saveAdditionalDetails(Complaint complaint) {
        additionalDetailRepository.save(ComplaintAdditionalDetail.builder()
                .complaintId(complaint.getId())
                .age(41)
                .gender("MALE")
                .complainantCategory("INDIVIDUAL")
                .isComplainantSelf("YES")
                .hasAccountWithRe("YES")
                .accountType("Savings Account")
                .savingsAccountNumber("30124578963")
                .atmDebitCardNumber("XXXXXXXXXXXX4471")
                .isCreditCardComplaint("NO")
                .isWalletComplaint("NO")
                .isBusinessCorrespondent("NO")
                .transactionRefNumber("UPI/2026/0448/778812")
                .disputeDate(LocalDate.now().minusDays(75))
                .compensationSought(new BigDecimal("55000.00"))
                .receivedReplyFromEntity("YES")
                .replyDate(LocalDate.now().minusDays(40))
                .reminderDate(LocalDate.now().minusDays(50))
                .subCategory1("Digital transactions")
                .subCategory2("Unauthorised electronic transaction")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void saveTimeline(Complaint complaint, Scenario scenario) {
        LocalDateTime now = LocalDateTime.now();
        timelineRepository.save(ComplaintTimeline.builder()
                .complaintId(complaint.getId())
                .action("COMPLAINT_REGISTERED")
                .performedBy(COMPLAINANT_NAME)
                .remarks("Complaint filed through the public portal.")
                .toStatus("NEW_COMPLAINT")
                .performedAt(now.minusDays(7))
                .build());

        timelineRepository.save(ComplaintTimeline.builder()
                .complaintId(complaint.getId())
                .action("ASSIGNED")
                .performedBy("system")
                .remarks("Assigned to " + officer + " at " + regionalOffice + ".")
                .fromStatus("NEW_COMPLAINT")
                .toStatus("NEW_COMPLAINT")
                .performedAt(now.minusDays(6))
                .build());

        timelineRepository.save(ComplaintTimeline.builder()
                .complaintId(complaint.getId())
                .action("STATUS_CHANGED")
                .performedBy(officer)
                .remarks("Moved to " + scenario.status() + " by the handling officer.")
                .fromStatus("NEW_COMPLAINT")
                .toStatus(scenario.status())
                .performedAt(now.minusDays(2))
                .build());
    }

    private void saveComments(String complaintNumber) {
        commentRepository.save(ComplaintComment.builder()
                .complaintNumber(complaintNumber)
                .author("Dev RBIO Officer")
                .initials("DO")
                .role("RBIO_DO")
                .color("#2563eb")
                .text("Reviewed the bank's reply. The chargeback was not attempted within the "
                        + "prescribed window, so the deficiency appears made out.")
                .createdAt(LocalDateTime.now().minusDays(3))
                .build());

        commentRepository.save(ComplaintComment.builder()
                .complaintNumber(complaintNumber)
                .author("Dev RBIO Reviewer")
                .initials("RV")
                .role("RBIO_REVIEWER")
                .color("#16a34a")
                .text("Agreed. Please obtain the transaction logs from the regulated entity before "
                        + "proceeding to conciliation.")
                .createdAt(LocalDateTime.now().minusDays(2))
                .build());
    }

    private void saveNodalComments(String complaintNumber, String recordNumber) {
        commentRepository.save(ComplaintComment.builder()
                .complaintNumber(complaintNumber)
                .noRecordNumber(recordNumber)
                .target("NO")
                .author("Dev RBIO Officer")
                .initials("DO")
                .role("RBIO_DO")
                .color("#2563eb")
                .text("Nodal officer has been asked to furnish the transaction logs and the "
                        + "chargeback trail.")
                .createdAt(LocalDateTime.now().minusDays(2))
                .build());

        commentRepository.save(ComplaintComment.builder()
                .complaintNumber(complaintNumber)
                .noRecordNumber(recordNumber)
                .target("PNO")
                .author("Dev RBIO Officer")
                .initials("DO")
                .role("RBIO_DO")
                .color("#2563eb")
                .text("Escalated to the principal nodal officer as no reply was received within "
                        + "the agreed timeline.")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build());
    }

    private void saveEmails(Complaint complaint, RegulatedEntity entity) {
        String threadId = "THR-DEV-" + complaint.getComplaintNumber();
        LocalDateTime now = LocalDateTime.now();

        simulatedEmailRepository.save(SimulatedEmail.builder()
                .messageId("MSG-DEV-" + complaint.getComplaintNumber() + "-1")
                .threadId(threadId)
                .fromEmail(COMPLAINANT_EMAIL)
                .toEmail(CMS_EMAIL)
                .subject(complaint.getSubject())
                .body("Dear Sir/Madam,\n\n" + complaint.getSubject() + ". I had raised this with "
                        + entity.getName() + " and the reply received is not satisfactory.\n\n"
                        + "Regards,\n" + COMPLAINANT_NAME)
                .direction("INBOUND")
                .status("PROCESSED")
                .complaintId(complaint.getId())
                .complaintNumber(complaint.getComplaintNumber())
                .sentAt(now.minusDays(7))
                .receivedAt(now.minusDays(7))
                .processedAt(now.minusDays(7))
                .build());

        simulatedEmailRepository.save(SimulatedEmail.builder()
                .messageId("MSG-DEV-" + complaint.getComplaintNumber() + "-2")
                .threadId(threadId)
                .fromEmail(CMS_EMAIL)
                .toEmail(COMPLAINANT_EMAIL)
                .subject("Re: " + complaint.getSubject() + " | Complaint #" + complaint.getComplaintNumber())
                .body("Dear " + COMPLAINANT_NAME + ",\n\nYour complaint has been registered under "
                        + complaint.getComplaintNumber() + " and assigned for examination.\n\n"
                        + "Regards,\nOffice of the RBI Ombudsman, " + regionalOffice)
                .direction("OUTBOUND")
                .status("SENT")
                .complaintId(complaint.getId())
                .complaintNumber(complaint.getComplaintNumber())
                .sentAt(now.minusDays(7).plusHours(2))
                .receivedAt(now.minusDays(7).plusHours(2))
                .build());

        simulatedEmailRepository.save(SimulatedEmail.builder()
                .messageId("MSG-DEV-" + complaint.getComplaintNumber() + "-3")
                .threadId(threadId)
                .fromEmail(CMS_EMAIL)
                .toEmail(entity.getNodalOfficerEmail())
                .subject("Comments called for | Complaint #" + complaint.getComplaintNumber())
                .body("Dear Nodal Officer,\n\nKindly furnish your comments along with the "
                        + "transaction logs in respect of the above complaint.\n\n"
                        + "Regards,\nOffice of the RBI Ombudsman, " + regionalOffice)
                .direction("OUTBOUND")
                .status("SENT")
                .complaintId(complaint.getId())
                .complaintNumber(complaint.getComplaintNumber())
                .sentAt(now.minusDays(4))
                .receivedAt(now.minusDays(4))
                .build());
    }

    private void saveConciliationMeeting(Complaint complaint, Scenario scenario) {
        boolean scheduled = "MEETING_SCHEDULED".equals(scenario.status());
        conciliationMeetingRepository.save(ConciliationMeeting.builder()
                .complaintId(complaint.getId())
                .meetingStatus(scheduled ? "SCHEDULED" : "COMPLETED")
                .meetingDate(scheduled ? LocalDate.now().plusDays(5) : LocalDate.now().minusDays(5))
                .meetingTime("11:30")
                .acceptedByComplainant("YES")
                .acceptedByEntity(scheduled ? "NO" : "YES")
                .conductedThroughVc("YES")
                .meetingComments(scheduled
                        ? "Conciliation meeting scheduled over video conference. Notices issued to both parties."
                        : "Both parties attended. The regulated entity agreed to re-examine the chargeback.")
                .comments("Seeded by RbioDevDataLoader.")
                .createdBy(officer)
                .createdAt(LocalDateTime.now().minusDays(6))
                .build());
    }

    /**
     * Fills in the assessment panel the "Send to RE" action writes, so reopening one of these records
     * shows a populated form rather than an empty one. The amounts sit inside the Ombudsman Scheme
     * caps that RbioCompensationService enforces on the real endpoint.
     */
    private void recordAssessment(NodalOfficerRecord record) {
        record.setStatus("ADVISORY_ISSUED");
        record.setAdvisoryComplianceDate(LocalDate.now().plusDays(20));
        record.setDisputeAmount(new BigDecimal("48500.00"));
        record.setCompensationLoss(new BigDecimal("48500.00"));
        record.setCompensationMental(new BigDecimal("10000.00"));
        record.setNotice131ComplyDate(LocalDate.now().plusDays(15));
        record.setForwardedToReAt(LocalDateTime.now().minusDays(4));
        // createForComplaint's transaction has already committed by the time we get here, so the
        // record is detached and these setters alone would be discarded.
        nodalOfficerRecordRepository.save(record);
    }

    private void saveTracker(Complaint complaint, RegulatedEntity entity, boolean responded) {
        LocalDateTime forwardedAt = LocalDateTime.now().minusDays(4);
        trackerRepository.save(ReResponseTracker.builder()
                .complaintId(complaint.getId())
                .regulatedEntityId(entity.getId())
                .forwardedAt(forwardedAt)
                .windowDays(30)
                .windowExpiresAt(forwardedAt.plusDays(30))
                .breached(false)
                .respondedAt(responded ? LocalDateTime.now().minusDays(1) : null)
                .responseText(responded
                        ? "The disputed transaction has been re-examined and a provisional credit "
                        + "has been afforded to the complainant pending final resolution."
                        : null)
                .notes("Seeded by RbioDevDataLoader.")
                .createdAt(forwardedAt)
                .build());
    }

    private static String slug(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "");
        return slug.length() > 20 ? slug.substring(0, 20) : slug;
    }
}
