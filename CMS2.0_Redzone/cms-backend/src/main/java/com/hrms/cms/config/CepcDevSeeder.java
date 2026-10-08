package com.hrms.cms.config;

import com.hrms.cms.entity.AaOfficerPool;
import com.hrms.cms.entity.Bank;
import com.hrms.cms.entity.CepcComplaintAssessment;
import com.hrms.cms.entity.CepcConciliationMeeting;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.entity.ComplaintCategory;
import com.hrms.cms.entity.ComplaintComment;
import com.hrms.cms.entity.ComplaintEligibilityAnswer;
import com.hrms.cms.entity.ComplaintReadState;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.DraftStatus;
import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.entity.EntityOfficeNodalOfficer;
import com.hrms.cms.entity.InterOfficeTransfer;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.OfficeAssignmentStrategy;
import com.hrms.cms.entity.OfficeCodeMaster;
import com.hrms.cms.entity.OfficeThresholdConfig;
import com.hrms.cms.entity.RbiDepartmentMaster;
import com.hrms.cms.entity.ReActivityStatus;
import com.hrms.cms.entity.ReResponseTracker;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.entity.RegulatoryBodyMaster;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.entity.StaffDraft;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.entity.TimelineEventSource;
import com.hrms.cms.repository.AaOfficerPoolRepository;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.CepcComplaintAssessmentRepository;
import com.hrms.cms.repository.CepcConciliationMeetingRepository;
import com.hrms.cms.repository.ComplaintAttachmentRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintCommentRepository;
import com.hrms.cms.repository.ComplaintEligibilityAnswerRepository;
import com.hrms.cms.repository.ComplaintReadStateRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.repository.EmailDraftRepository;
import com.hrms.cms.repository.EntityOfficeNodalOfficerRepository;
import com.hrms.cms.repository.InterOfficeTransferRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import com.hrms.cms.repository.OfficeAssignmentStrategyRepository;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import com.hrms.cms.repository.OfficeThresholdConfigRepository;
import com.hrms.cms.repository.RbiDepartmentMasterRepository;
import com.hrms.cms.repository.ReResponseTrackerRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.hrms.cms.repository.RegulatoryBodyMasterRepository;
import com.hrms.cms.repository.SimulatedEmailRepository;
import com.hrms.cms.repository.StaffDraftRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import com.hrms.cms.service.CepcStatus;
import com.hrms.cms.service.ComplaintEmailService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Local CEPC dashboard data: fifty-five complaints across six complainants.
 *
 * <p><b>Statuses are drawn only from the twenty-one {@code com.rbi.cms.common.enums.ComplaintStatus} values
 * CEPC actually uses</b> (see {@link CepcStatus}), each represented at least once below. Officers are drawn
 * only from {@link #OFFICER_ROWS}, the fixed roster this seeder writes into {@code wf_officer_pool}, so
 * every {@code assignedOfficer} below resolves to a real, pickable row in the assignment dialogs.
 *
 * <p>Read state is seeded for {@link #ME} on a subset and for nobody else, which is what demonstrates that
 * {@code COMPLAINT_READ_STATE} is per user: the same grid shows a different unread count to {@link #ME} than
 * to anyone else.
 *
 * <p><b>This seeder deletes before it writes, on every boot, unconditionally.</b> Every CEPC complaint this
 * seeder previously wrote (identified by {@link #NUMBER_PREFIX}) is purged and rewritten from {@link #SPECS}
 * below regardless of whether the database already looks "seeded", so a running dev-local database never
 * drifts from the data below just because the row count happened not to change. A CEPC complaint whose
 * status falls outside the {@link CepcStatus} vocabulary is purged the same way, wherever it came from. Both
 * kinds of purge take their child rows with them. Confined to {@code dev-local}.
 */
@Component
@Profile("dev-local")
@Order(70)
@RequiredArgsConstructor
@Slf4j
public class CepcDevSeeder implements CommandLineRunner {

    /**
     * The officer the dashboard is meant to be viewed as — "assigned to me" and "pending with me" mean this.
     *
     * <p><b>This must be a username someone can actually sign in as.</b> {@code CepcEditPolicy.canEdit}
     * compares the caller against {@code Complaint.assignedOfficer}, so seeding an owner that exists in no
     * realm makes every seeded complaint read-only for every real login: the summary answers
     * {@code canEdit:false}, the detail view turns on {@code isReadOnlyViewer}, and each write path either
     * greys out or answers 403. It presents as features being "broken" — the Conciliation tab accepting a
     * meeting and then doing nothing on Confirm — when the policy is working exactly as intended and simply
     * has nobody to say yes to. Earlier this named {@code cepc.officer1}, which is in no realm.
     *
     * <p>Override with {@code -Dcms.dev.cepc.owner=<username>} if this realm names its dealing officer
     * differently; a Spring property cannot reach the static {@link #SPECS} table this feeds.
     */
    public static final String ME = System.getProperty("cms.dev.cepc.owner", "cepc_do_user1");

    /** A second officer, so the scope filters have something to exclude. */
    public static final String OTHER = "cepc_reviewer_user1";

    /** The office every seeded complaint is filed against, so the office-scoped pickers have a match. */
    public static final String OFFICE_CODE = "BLR";

    /** The two DEOs the seeded intake drafts are split between, so {@code currentLoad} is not uniform. */
    private static final String DEO_ONE = "cepc.deo1";
    private static final String DEO_TWO = "cepc.deo2";

    /**
     * The seeded complaint numbers' prefix, in the shape
     * {@link com.hrms.cms.service.ComplaintNumberGeneratorService} produces: {@code N} + financial year +
     * office code + a six-digit sequence.
     *
     * <p><b>No slashes.</b> They were {@code CEPC/DEV/2025/0001} until it became clear that the detail
     * view addresses a complaint as a single path segment — {@code /api/v1/complaints/{n}/comments},
     * {@code /emails}, {@code /emails/{id}/retry} — and interpolates the number unencoded. A slash split
     * the segment and every one of those routes 404'd, while {@code %2F} is rejected by Tomcat as a 400.
     * Real complaint numbers have never contained a slash, so seed data that did was testing a shape the
     * application never produces.
     */
    private static final String NUMBER_PREFIX = "N202526" + OFFICE_CODE;

    private static String complaintNumber(int seq) {
        return String.format("%s%06d", NUMBER_PREFIX, seq);
    }

    private static final String FIRST_NUMBER = complaintNumber(1);

    private final ComplaintRepository complaintRepository;
    private final NodalOfficerRecordRepository nodalOfficerRecordRepository;
    private final ComplaintAttachmentRepository attachmentRepository;
    private final ComplaintReadStateRepository readStateRepository;
    private final StaffDraftRepository staffDraftRepository;
    private final EmailDraftRepository draftRepository;
    private final RegulatedEntityRepository regulatedEntityRepo;
    private final EntityOfficeNodalOfficerRepository nodalOfficerRepo;
    private final BankRepository bankRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final OfficeCodeMasterRepository officeCodeRepository;
    private final AaOfficerPoolRepository officerPoolRepository;
    private final RegulatoryBodyMasterRepository regulatoryBodyRepository;
    private final RbiDepartmentMasterRepository departmentRepository;
    private final OfficeThresholdConfigRepository thresholdRepository;
    private final OfficeAssignmentStrategyRepository strategyRepository;
    private final CepcComplaintAssessmentRepository assessmentRepository;
    private final ComplaintEligibilityAnswerRepository eligibilityRepository;
    private final CepcConciliationMeetingRepository meetingRepository;
    private final ComplaintCommentRepository commentRepository;
    private final ComplaintTimelineRepository timelineRepository;
    private final ReResponseTrackerRepository reTrackerRepository;
    private final SimulatedEmailRepository emailRepository;
    private final InterOfficeTransferRepository transferRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final EntityManager entityManager;

    /** One complainant: reused across three complaints so past-complaint lookups have something to find. */
    private record Person(String name, String email, String phone, String state, String district) {
    }

    private static final List<Person> PEOPLE = List.of(
            new Person("Nagaraju ", "raju.kumar@example.com", "9840011001", "Karnataka", "Bengaluru Urban"),
            new Person("Bharagav Avvula", "bharagav.reddy@example.com", "9840011002", "Telangana", "Hyderabad"),
            new Person("Arpitha Narayana", "arpitha.shetty@example.com", "9840011003", "Karnataka", "Udupi"),
            new Person("Javeed Ahmed", "javeed.ahmed@example.com", "9840011004", "Maharashtra", "Pune"),
            new Person("Sudhi panigrahi", "sudhi.menon@example.com", "9840011005", "Kerala", "Ernakulam"),
            new Person("Naresh", "naresh.babu@example.com", "9840011006", "Tamil Nadu", "Chennai"));

    /** One {@code wf_officer_pool} row this seeder writes with a fixed id, matching a given roster exactly. */
    private record OfficerRow(int id, String userId, String displayName, String roleGroup, int maxWorkload) {
    }

    /**
     * The CEPC complaint-assignment roster, verbatim (ids, display names and all) from the roster this
     * seeder was handed. {@link #ME} and {@link #OTHER} above must name one of these user ids.
     */
    private static final List<OfficerRow> OFFICER_ROWS = List.of(
            new OfficerRow(20, "cepc_do_user1", "CEPC DO User 1", "CEPC_DO", 40),
            new OfficerRow(21, "cepc_do_user3", "CEPC DO User 3", "CEPC_DO", 40),
            new OfficerRow(22, "cepc_do_user2", "CEPC DO User 2", "CEPC_DO", 40),
            new OfficerRow(23, "cepc_reviewer_user1", "CEPC REVIWER User 1", "CEPC_REVIEWER", 40),
            new OfficerRow(24, "cepc_reviewer_user2", "CEPC REVIWER User 2", "CEPC_REVIEWER", 40),
            new OfficerRow(25, "cepc_incharge_user2", "CEPC INCHARGE User 2", "CEPC_INCHARGE", 40),
            new OfficerRow(26, "cepc_incharge_user1", "CEPC INCHARGE User 1", "CEPC_INCHARGE", 40),
            new OfficerRow(27, "cepc_closing_authority_user1", "CEPC CA Uaer 1", "CEPC_CLOSING_AUTHORITY", 40),
            new OfficerRow(28, "cepc_closing_authority_user2", "CEPC CA User 2", "CEPC_CLOSING_AUTHORITY", 40));

    /** Ad-hoc usernames earlier revisions of this seeder wrote, purged so they cannot linger alongside
     *  {@link #OFFICER_ROWS}. */
    private static final List<String> LEGACY_OFFICER_USER_IDS = List.of(
            "cepc.officer1", "cepc.officer2", "cepc.reviewer1", "cepc.reviewer2",
            "cepc.incharge1", "cepc.closing1", "cepc.contact1");

    /**
     * One complaint's distinguishing fields.
     *
     * @param person    index into {@link #PEOPLE}
     * @param slaDays   the SLA deadline as days from today — negative is breached, and the 0-15 / 16-30
     *                  windows are what the two SLA KPI rows count
     * @param ageDays   how long ago the complaint was filed
     */
    private record Spec(int person, String status, String stage, String officer, String role,
                        ReActivityStatus reActivity, int slaDays, int ageDays, String priority,
                        String filingType, String subject) {
    }

    /**
     * The fifty-five complaints.
     *
     * <p><b>Statuses are the canonical {@link CepcStatus} names</b> — one of the twenty-one CEPC statuses,
     * with every one represented at least once. {@code priority} is a {@code Priority} enum key (LOW,
     * MEDIUM, HIGH, CRITICAL) and {@code filingType} a {@code FilingType} enum key (PORTAL, EMAIL, LETTER).
     * {@code role} is always one of the four roles {@link #OFFICER_ROWS} actually staffs
     * (CEPC_DO/CEPC_REVIEWER/CEPC_INCHARGE/CEPC_CLOSING_AUTHORITY) except for the handful marked {@code "RE"}
     * — a marker meaning "with the regulated entity", never a real officer-pool role, matching how the
     * 13(1)-notice ladder has always been represented here.
     *
     * <p><b>The first eighteen must not be reordered or removed.</b> {@code applyDecision},
     * {@code seedEligibilityAnswers}, {@code seedConciliation}, {@code seedPendingTransfer},
     * {@code seedComments} and {@code seedEmails} address complaints by position, so moving one silently
     * attaches its conciliation meeting or its email thread to a different complaint. New rows append.
     */
    private static final List<Spec> SPECS = List.of(
            // #1  New + SLA breached. Feeds: New Complaint, Pending with Me, SLA Breached.
            new Spec(0, CepcStatus.NEW_COMPLAINT, "NEW", ME, "CEPC_DO", null, -5, 40, "HIGH", "PORTAL",
                    "Unauthorised debit of Rs 24,500 from savings account"),
            // #2  Meeting Scheduled. The status carries it, not the stage: SCHEDULE_MEETING still writes only
            //     the stage, so a stage-keyed row would never reach the status-keyed tab.
            new Spec(0, CepcStatus.MEETING_SCHEDULED, "MEETING_SCHEDULED", ME, "CEPC_DO", null, 20, 25, "MEDIUM",
                    "EMAIL", "Conciliation sought on wrongly levied locker charges"),
            // #3  Meeting Scheduled, another officer's — so "Pending with Me" is narrower than the tab.
            new Spec(1, CepcStatus.MEETING_SCHEDULED, "MEETING_SCHEDULED", OTHER, "CEPC_DO", null, 25, 22, "MEDIUM",
                    "PORTAL", "Delay in closure of home loan and release of title documents"),
            // #4  Sent Back to Me. Both halves of that predicate are true here.
            new Spec(1, CepcStatus.SENT_BACK_TO_DO, "SENT_BACK_TO_DO", ME, "CEPC_DO", null, 5, 30, "HIGH", "PORTAL",
                    "Credit card annual fee reversed short of the promised amount"),
            // #5  Forwarded to RE, entity has not answered. Feeds Sent to RE and Pending with RE.
            new Spec(2, CepcStatus.NEW_COMPLAINT, "FORWARDED_TO_RE", ME, "RE", ReActivityStatus.NOT_OPENED, 10, 18,
                    "MEDIUM", "LETTER", "UPI transfer credited to the wrong beneficiary"),
            // #6  Forwarded to RE, entity HAS answered — so it leaves Sent to RE and enters Response from RE.
            new Spec(2, CepcStatus.NEW_COMPLAINT, "FORWARDED_TO_RE", ME, "RE", ReActivityStatus.RESPONSE_SUBMITTED,
                    12, 20, "LOW", "PORTAL", "Interest on recurring deposit computed at the wrong rate"),
            // #7  Forwarded to RE, mid-ladder — still "pending with RE", which is why that predicate cannot
            //     simply test for NOT_OPENED.
            new Spec(3, CepcStatus.NEW_COMPLAINT, "FORWARDED_TO_RE", OTHER, "RE", ReActivityStatus.DOCUMENTS_UPLOADED,
                    18, 28, "MEDIUM", "EMAIL", "ATM cash not dispensed but account debited twice"),
            // #8  Sent to reviewer + SLA breached.
            new Spec(3, CepcStatus.SENT_TO_REVIEWER, "PENDING_REVIEW", OTHER, "CEPC_REVIEWER", null, -2, 45, "HIGH",
                    "PORTAL", "Mis-selling of a unit linked insurance policy at the branch"),
            // #9  Assigned but untouched, still New Complaint.
            new Spec(4, CepcStatus.NEW_COMPLAINT, "NEW", ME, "CEPC_DO", null, 3, 8, "MEDIUM", "EMAIL",
                    "Cheque returned unpaid despite sufficient balance"),
            // #10 Information Required.
            new Spec(4, CepcStatus.INFORMATION_REQUIRED, "INFO_REQUESTED", ME, "CEPC_DO", null, 22, 15, "LOW",
                    "PORTAL", "Statement of account not provided for the disputed period"),
            // #11 Closed — terminal, so every pendingOnly KPI must exclude it. applyDecision keys on this seq.
            new Spec(5, CepcStatus.COMPLAINT_CLOSED, "CLOSED", OTHER, "CEPC_CLOSING_AUTHORITY", null, 28, 90,
                    "MEDIUM", "PORTAL", "Failure to honour the sanctioned overdraft limit"),
            // #12 Withdrawn — terminal, and shows under the All tab. applyDecision keys on this seq.
            new Spec(5, CepcStatus.COMPLAINT_WITHDRAWN, "WITHDRAWN", ME, "CEPC_DO", null, -1, 60, "LOW", "EMAIL",
                    "Duplicate NEFT debit, since resolved directly with the branch"),
            // #13 Sent to another RBI department.
            new Spec(0, CepcStatus.SENT_TO_OTHER_DEPARTMENTS, "FORWARDED_TO_CONTACT", ME, "CEPC_DO",
                    null, 8, 12, "MEDIUM", "PORTAL", "Pension credit stopped without intimation"),
            // #14 Forwarded externally.
            new Spec(1, CepcStatus.SENT_TO_OTHER_REGULATED_BODIES, "FORWARDED_EXTERNALLY", OTHER, "CEPC_DO", null,
                    19, 26, "LOW", "LETTER",
                    "Grievance relating to an insurance product, outside the Scheme"),
            // #15 Sent to in-charge + SLA breached.
            new Spec(2, CepcStatus.SENT_TO_INCHARGE, "ESCALATED", ME, "CEPC_INCHARGE", null, -8, 55, "HIGH", "EMAIL",
                    "No response from the entity after two reminders"),
            // #16 Pending Office Head Approval — an inter-office transfer awaiting the head's approval.
            new Spec(3, CepcStatus.PENDING_OFFICE_HEAD_APPROVAL, "PENDING_INCHARGE", OTHER, "CEPC_INCHARGE", null,
                    14, 35, "MEDIUM", "PORTAL", "Award compliance not confirmed by the entity"),
            // #17 Settled, closure letter pending. applyDecision keys on this seq.
            new Spec(4, CepcStatus.COMPLAINT_SETTLED, "AWAITING_CLOSURE", OTHER, "CEPC_CLOSING_AUTHORITY", null,
                    24, 50, "LOW", "PORTAL", "Compensation credited, closure letter pending"),
            // #18 Under examination + SLA breached, to keep that count above one.
            new Spec(5, CepcStatus.NEW_COMPLAINT, "UNDER_EXAMINATION", ME, "CEPC_DO", null, -3, 33, "HIGH", "EMAIL",
                    "Excess foreclosure charges recovered on a personal loan"),

            // ═══ #19-#50: volume across the same six complainants ═══
            // Free to reorder among themselves — nothing addresses these by position.

            // The two send-back rungs #4 does not cover, so all three send-back statuses return rows.
            new Spec(0, CepcStatus.SENT_BACK_TO_REVIEWER, "SENT_BACK_TO_REVIEWER", OTHER, "CEPC_REVIEWER", null, 6,
                    29, "MEDIUM", "PORTAL", "Reassessment sought on disputed mortgage prepayment penalty"),
            new Spec(1, CepcStatus.SENT_BACK_TO_INCHARGE, "SENT_BACK_TO_INCHARGE", OTHER, "CEPC_INCHARGE", null, 9,
                    31, "HIGH", "EMAIL", "Closure note returned for a fuller finding on the entity's reply"),

            // The three outbound-transfer stages the Forward tab offers.
            new Spec(2, CepcStatus.SENT_TO_OTHER_DEPARTMENTS, "FORWARDED_OTHER_RBI_DEPT", OTHER, "CEPC_DO", null,
                    17, 24, "LOW", "PORTAL", "Foreign exchange remittance query for the FED desk"),
            new Spec(3, CepcStatus.SENT_TO_OTHER_REGULATED_BODIES, "FORWARDED_REGULATORY_BODY", OTHER, "CEPC_DO",
                    null, 21, 27, "MEDIUM", "EMAIL", "Mutual fund redemption delay referable to SEBI"),
            new Spec(4, CepcStatus.SENT_TO_OTHER_OFFICE, "SENT_TO_OTHER_OFFICE", OTHER, "CEPC_DO", null, 16, 23,
                    "MEDIUM", "PORTAL", "Branch falls under the Chennai office's jurisdiction"),
            new Spec(5, CepcStatus.SENT_TO_OTHER_OFFICE, "FORWARDED_OTHER_OFFICE", OTHER, "CEPC_DO", null, 26, 34,
                    "LOW", "LETTER", "Cause of action arose within the Mumbai office's area"),

            // Reopened: the status the Reopened Complaints filter selects on, now that it keys on status
            // rather than on the REOPENED stage.
            new Spec(0, CepcStatus.COMPLAINT_REOPEN, "REOPENED", ME, "CEPC_DO", null, 11, 70, "HIGH", "PORTAL",
                    "Complaint reopened after the entity reversed its earlier undertaking"),
            new Spec(1, CepcStatus.COMPLAINT_REOPEN, "REOPENED", OTHER, "CEPC_DO", null, 13, 65, "MEDIUM", "EMAIL",
                    "Reopened: compensation credited short of the agreed sum"),

            // More meetings, so the tab and its KPI card are not carried by two rows.
            new Spec(2, CepcStatus.MEETING_SCHEDULED, "MEETING_SCHEDULED", ME, "CEPC_DO", null, 7, 19, "HIGH",
                    "PORTAL", "Conciliation called on a disputed guarantee invocation"),
            new Spec(3, CepcStatus.MEETING_SCHEDULED, "MEETING_SCHEDULED", OTHER, "CEPC_DO", null, 27, 21, "LOW",
                    "EMAIL", "Joint meeting sought on a long-pending KYC freeze"),

            // More RE traffic across the activity ladder, feeding Sent to RE / Response from RE both ways.
            new Spec(4, CepcStatus.NEW_COMPLAINT, "FORWARDED_TO_RE", ME, "RE", ReActivityStatus.NOT_OPENED, -4, 36,
                    "HIGH", "EMAIL", "13(1) notice unacknowledged by the nodal officer"),
            new Spec(5, CepcStatus.NEW_COMPLAINT, "FORWARDED_TO_RE", ME, "RE", ReActivityStatus.RESPONSE_SUBMITTED,
                    15, 17, "MEDIUM", "PORTAL", "Entity replied on the disputed merchant chargeback"),

            // Intake depth: New Complaint and the Complaint Assigned To Me scope.
            new Spec(2, CepcStatus.NEW_COMPLAINT, "NEW", ME, "CEPC_DO", null, 4, 3, "MEDIUM", "PORTAL",
                    "Savings account debited for an SMS alert charge never opted into"),
            new Spec(3, CepcStatus.NEW_COMPLAINT, "NEW", ME, "CEPC_DO", null, 2, 2, "HIGH", "EMAIL",
                    "Fixed deposit prematurely closed without the depositor's consent"),
            new Spec(5, CepcStatus.NEW_COMPLAINT, "NEW", ME, "CEPC_DO", null, 1, 6, "HIGH", "EMAIL",
                    "Standing instruction for an insurance premium not executed"),
            new Spec(0, CepcStatus.NEW_COMPLAINT, "NEW", OTHER, "CEPC_DO", null, 8, 9, "MEDIUM", "PORTAL",
                    "Locker rent debited twice in the same quarter"),

            // Examination depth.
            new Spec(2, CepcStatus.NEW_COMPLAINT, "UNDER_EXAMINATION", ME, "CEPC_DO", null, 19, 14, "MEDIUM", "EMAIL",
                    "Education loan moratorium interest charged contrary to the sanction"),
            new Spec(3, CepcStatus.NEW_COMPLAINT, "UNDER_EXAMINATION", OTHER, "CEPC_DO", null, -7, 41, "HIGH",
                    "PORTAL", "Deceased claim settlement pending beyond the stipulated period"),
            new Spec(4, CepcStatus.INFORMATION_REQUIRED, "INFO_REQUESTED", ME, "CEPC_DO", null, 12, 16, "MEDIUM",
                    "EMAIL", "Transaction proof awaited from the complainant"),
            new Spec(5, CepcStatus.INFORMATION_REQUIRED, "AWAITING_INFO", OTHER, "CEPC_DO", null, 28, 20, "LOW",
                    "PORTAL", "Bank statement for the disputed window yet to be produced"),

            // Review and approval depth — the two rungs the forwarding ladder passes through.
            new Spec(0, CepcStatus.SENT_TO_REVIEWER, "REVIEWER_REVIEW", OTHER, "CEPC_REVIEWER", null, 10, 43,
                    "MEDIUM", "PORTAL", "Draft finding on a disputed interest reset placed for review"),
            new Spec(1, CepcStatus.SENT_TO_REVIEWER, "PENDING_REVIEW", OTHER, "CEPC_REVIEWER", null, 20, 39, "LOW",
                    "EMAIL", "Assessment of a failed IMPS credit placed for review"),
            new Spec(4, CepcStatus.SENT_TO_REVIEWER, "REVIEWER_REVIEW", ME, "CEPC_REVIEWER", null, 26, 37, "MEDIUM",
                    "PORTAL", "Finding on an unauthorised card-not-present debit placed for review"),
            new Spec(2, CepcStatus.PENDING_OFFICE_HEAD_APPROVAL, "INCHARGE_REVIEW", OTHER, "CEPC_INCHARGE", null,
                    -9, 47, "HIGH", "PORTAL", "Award proposal awaiting the office head's approval"),
            new Spec(3, CepcStatus.PENDING_OFFICE_HEAD_APPROVAL, "PENDING_INCHARGE", OTHER, "CEPC_INCHARGE", null,
                    18, 44, "MEDIUM", "EMAIL", "Transfer to another office awaiting the CRPC head's approval"),

            // Settled and closing depth — Advisory Complied, Sent to Closing Authority and Mark for Closure.
            new Spec(5, CepcStatus.ADVISORY_COMPLIED, "ADVISORY_ISSUED", OTHER, "CEPC_CLOSING_AUTHORITY", null, 25,
                    48, "LOW", "EMAIL", "Advisory issued to the entity and confirmed complied with"),
            new Spec(0, CepcStatus.SENT_TO_CLOSING_AUTHORITY, "AWARD_PASSED", OTHER, "CEPC_CLOSING_AUTHORITY", null,
                    14, 58, "HIGH", "PORTAL", "Award passed; implementation date awaited from the entity"),

            // In-charge depth.
            new Spec(1, CepcStatus.SENT_TO_INCHARGE, "ESCALATED", OTHER, "CEPC_INCHARGE", null, -11, 62, "HIGH",
                    "PORTAL", "Entity unresponsive after the second reminder and a phone call"),

            // Terminal depth. Kept a minority of the set so the pendingOnly KPIs stay meaningful.
            new Spec(2, CepcStatus.COMPLAINT_CLOSED, "CLOSED", OTHER, "CEPC_CLOSING_AUTHORITY", null, 30, 95, "LOW",
                    "EMAIL", "Disputed ATM debit reversed; complaint closed as resolved"),
            new Spec(3, CepcStatus.COMPLAINT_CLOSED, "CLOSED", ME, "CEPC_CLOSING_AUTHORITY", null, 30, 84, "MEDIUM",
                    "PORTAL", "Loan account closure certificate issued; complaint closed"),
            new Spec(5, CepcStatus.COMPLAINT_WITHDRAWN, "WITHDRAWN", ME, "CEPC_DO", null, -2, 68, "LOW",
                    "PORTAL", "Complainant withdrew after the entity settled directly"),
            new Spec(0, CepcStatus.COMPLAINT_WITHDRAWN, "WITHDRAWN", OTHER, "CEPC_DO", null, 5, 73, "MEDIUM",
                    "EMAIL", "Withdrawn in writing; matter resolved bilaterally"),

            // ═══ #51-#55: the remaining statuses, which nothing else in this list carries ═══

            // Draft: part-filled complaints the officer has not submitted. The Draft tab reads these, not
            // STAFF_DRAFT — a staff draft has no complaint row, so it could never carry a status at all.
            new Spec(1, CepcStatus.DRAFT, "DRAFT", ME, "CEPC_DO", null, 30, 1, "LOW", "PORTAL",
                    "Draft: disputed processing fee on a two-wheeler loan, details to be confirmed"),
            new Spec(4, CepcStatus.DRAFT, "DRAFT", OTHER, "CEPC_DO", null, 30, 2, "MEDIUM", "EMAIL",
                    "Draft: NACH mandate cancelled without notice, complainant's bank details awaited"),

            // Sent to RBI: the contact person has answered back to RBI.
            new Spec(2, CepcStatus.SENT_TO_RBI, "EXAMINATION", ME, "CEPC_DO", null, 9, 13, "HIGH", "PORTAL",
                    "Contact person's reply received on the disputed forex markup"),
            new Spec(5, CepcStatus.SENT_TO_RBI, "EXAMINATION", OTHER, "CEPC_DO", null, 23, 26, "MEDIUM", "EMAIL",
                    "Contact person confirmed the entity's position on the failed card refund"),

            // Rejected: the third terminal status, with no tab of its own (like Closed and Withdrawn). Here
            // so the pendingOnly KPIs have something to exclude beyond closed and withdrawn — with no such
            // row, an exclusion that had silently dropped out of the predicate would still pass every count
            // check.
            new Spec(3, CepcStatus.COMPLAINT_REJECTED, "CLOSED", ME, "CEPC_DO", null, 30, 77, "LOW", "PORTAL",
                    "Rejected as outside the Scheme: the subject matter is sub judice"));

    /** Complaints (1-based) that {@link #ME} has opened, so the grid shows read and unread rows of each kind. */
    private static final List<Integer> READ_BY_ME =
            List.of(1, 3, 5, 8, 11, 13, 19, 22, 25, 28, 31, 34, 37, 40, 43, 46, 49, 51, 53);

    /** Complaints (1-based) WITHOUT an attachment — what the {@code withoutAttachments} toggle must find. */
    private static final List<Integer> NO_ATTACHMENT =
            List.of(2, 6, 9, 12, 15, 17, 20, 24, 27, 30, 33, 36, 39, 42, 45, 48, 50, 52, 54);

    private static final List<String> DOCUMENT_TYPES =
            List.of("COMPLAINT_LETTER", "BANK_STATEMENT", "IDENTITY_PROOF", "ENTITY_REPLY", "ANNEXURE");

    @Override
    @Transactional
    public void run(String... args) {
        // Ahead of the complaint guard, and guarded row by row themselves. These are reference tables the
        // Forward tab's three destination dropdowns read, and they are independent of whether the complaints
        // are already there — putting them after the guard would mean a database seeded before they existed
        // never got them, which is exactly the state that left the tab with three empty dropdowns and every
        // forward failing master validation.
        seedForwardTargets();
        // Same argument, and it has already bitten once: a database seeded before the DEO pool rows were
        // written here would never receive them, which is precisely why the Email Communication tab's
        // assignee list came back empty on a database that had complaints in it. Both methods skip rows
        // they already find, so running them on every boot adds nothing twice.
        seedOffices();
        seedOfficerPool();
        seedEntityContacts();
        seedIntakeDrafts();
        seedAllowedEmailDomains();

        // Any CEPC complaint carrying a status outside the vocabulary is unreachable: no dashboard filter
        // selects it and no chip styles it, so it sits in the table contributing nothing but a wrong total.
        // Dropped on every boot rather than only at first seed, since one can arrive at any time.
        purge(cepcComplaints().stream()
                .filter(c -> c.getStatus() == null
                        || !CepcStatus.everySpelling().contains(c.getStatus().toLowerCase(Locale.ROOT)))
                .toList(), "an unrecognised status");

        // Unconditional, on every boot: this seeder's own previous output (identified by NUMBER_PREFIX) is
        // always purged and rewritten from SPECS below, rather than only when SPECS grows. Keying the guard
        // on "does the last row already exist" left a running database on stale data whenever a revision
        // changed content without changing SPECS.size() — exactly the case here.
        purge(cepcComplaints().stream()
                .filter(c -> c.getComplaintNumber() != null
                        && c.getComplaintNumber().startsWith(NUMBER_PREFIX))
                .toList(), "a full reseed of this seeder's own data");

        List<Bank> banks = bankRepository.findAll();
        List<ComplaintCategory> categories = categoryRepository.findByParentIdIsNullOrderBySortOrder();
        LocalDateTime now = LocalDateTime.now();

        List<Complaint> saved = new ArrayList<>();
        for (int i = 0; i < SPECS.size(); i++) {
            saved.add(complaintRepository.save(complaint(SPECS.get(i), i + 1, banks, categories, now)));
        }

        seedNodalRecords(saved, banks, now);
        seedAttachments(saved, now);
        seedReadState(saved, now);
        seedStaffDrafts(now);

        // The six detail-view tabs. Everything above this line populates the DASHBOARD; a complaint that
        // opens with six empty tabs is not distinguishable from six broken endpoints, which is the state
        // the detail view was in.
        seedAssessments(saved, banks, now);
        seedEligibilityAnswers(saved);
        seedConciliation(saved, now);
        seedPendingTransfer(saved, now);
        seedReResponseTrackers(saved, banks, now);
        seedComments(saved, now);
        seedEmails(saved, banks, now);
        seedTimeline(saved, now);

        log.info("CEPC dev data seeded: {} complaints for {} complainants, owner={}",
                saved.size(), PEOPLE.size(), ME);
    }

    private List<Complaint> cepcComplaints() {
        return entityManager
                .createQuery("SELECT c FROM Complaint c WHERE c.department = 'CEPC'", Complaint.class)
                .getResultList();
    }

    /** Child tables holding a {@code complaintNumber}; none of them declare a real foreign key. */
    private static final List<String> CHILDREN_BY_NUMBER = List.of(
            "NodalOfficerRecord", "CepcComplaintAssessment", "ComplaintEligibilityAnswer",
            "CepcConciliationMeeting", "SimulatedEmail", "InterOfficeTransfer");

    /**
     * Child tables holding a {@code complaintId} instead. {@code ComplaintComment} moved here from
     * {@code CHILDREN_BY_NUMBER}: the threaded entity (theirs, from the merge) is keyed by
     * {@code complaintId} and has no {@code complaintNumber} column at all, so the old by-number
     * delete would fail JPQL property resolution at runtime.
     */
    private static final List<String> CHILDREN_BY_ID = List.of(
            "ComplaintAttachment", "ComplaintReadState", "ComplaintTimeline", "ReResponseTracker",
            "ComplaintComment");

    /**
     * Deletes complaints and everything hanging off them.
     *
     * <p>The children must go first and must go explicitly: none of these tables declares a real foreign key,
     * so deleting the parent alone leaves rows keyed to an id that no longer exists. Those orphans are not
     * inert — the detail view looks its tabs up by {@code complaintNumber}, and a recycled number would show
     * one complaint the assessment and email thread of another.
     *
     * <p>The entity names are compile-time constants, so the concatenation below cannot carry anything but
     * them.
     */
    private void purge(List<Complaint> doomed, String why) {
        if (doomed.isEmpty()) {
            return;
        }
        List<String> numbers = doomed.stream()
                .map(Complaint::getComplaintNumber).filter(Objects::nonNull).toList();
        List<Long> ids = doomed.stream().map(Complaint::getId).filter(Objects::nonNull).toList();

        if (!numbers.isEmpty()) {
            for (String child : CHILDREN_BY_NUMBER) {
                entityManager.createQuery(
                                "DELETE FROM " + child + " x WHERE x.complaintNumber IN :numbers")
                        .setParameter("numbers", numbers)
                        .executeUpdate();
            }
        }
        if (!ids.isEmpty()) {
            for (String child : CHILDREN_BY_ID) {
                entityManager.createQuery("DELETE FROM " + child + " x WHERE x.complaintId IN :ids")
                        .setParameter("ids", ids)
                        .executeUpdate();
            }
        }
        complaintRepository.deleteAll(doomed);
        // Bulk deletes bypass the persistence context, so without this the rows just removed are still
        // cached and the reseed below would collide with them on the unique complaint number.
        entityManager.flush();
        entityManager.clear();
        log.info("CEPC dev data: removed {} complaints with {} and their child rows", doomed.size(), why);
    }

    private Complaint complaint(Spec s, int seq, List<Bank> banks, List<ComplaintCategory> categories,
                                LocalDateTime now) {
        Person p = PEOPLE.get(s.person());
        LocalDateTime filed = now.minusDays(s.ageDays());

        // The dates are set explicitly and survive because Complaint's @PrePersist assigns only when the
        // field is still null. It used to overwrite unconditionally, which silently flattened every
        // backdated seed to "now" and left the SLA windows and date ordering untestable.
        return Complaint.builder()
                .complaintNumber(complaintNumber(seq))
                .complainantName(p.name())
                .complainantEmail(p.email())
                .complainantPhone(p.phone())
                .complainantState(p.state())
                .complainantDistrict(p.district())
                .complainantAddress("12, MG Road, " + p.district())
                .bankId(pick(banks, seq) == null ? null : pick(banks, seq).getId())
                .bankBranch(p.district() + " Main Branch")
                .categoryId(pick(categories, seq) == null ? null : pick(categories, seq).getId())
                .subject(s.subject())
                .description(s.subject() + ". Raised with the entity first; the reply received was not "
                        + "satisfactory, hence this complaint.")
                .reliefSought("Reversal of the disputed amount with applicable interest.")
                .status(s.status())
                .priority(s.priority())
                .filingType(s.filingType())
                .department("CEPC")
                // Must be OFFICE_CODE, the same value seedOfficerPool writes on every pool row. Every CEPC
                // listing is scoped by department AND the caller's own office, which officeOf() reads from
                // that pool row — a seed under any other office code leaves the dashboard empty.
                .regionalOffice(OFFICE_CODE)
                .assignedOfficer(s.officer())
                .assignedRole(s.role())
                .workflowStage(s.stage())
                .entityCode(entityName(banks, seq))
                .reActivityStatus(s.reActivity())
                .reActivityChangedAt(s.reActivity() == null ? null : filed.plusDays(2))
                .reResponseDeadline(s.reActivity() == null ? null : filed.plusDays(30).toLocalDate())
                .slaDeadline(now.plusDays(s.slaDays()))
                .slaPriority(s.priority().toUpperCase(java.util.Locale.ROOT))
                .createdAt(filed)
                .updatedAt(filed.plusDays(1))
                .filedAt(filed)
                .lastStatusChangeDate(filed.plusDays(1))
                .build();
    }

    /**
     * A contact record per complaint, so the grid's Nodal Officer and Principal Nodal Officer columns are
     * populated on every row rather than on the handful that happen to have one.
     *
     * <p>{@code recordNumber} is mandatory in practice even though the column is nullable:
     * {@code CepcNodalRecordService.forwardToRe} resolves its target with
     * {@code findByRecordNumber(recordNumber)}, and the Contact Entity tab's nested detail view and the
     * per-record comment routes key on the same value. It was never set here, so every seeded record
     * carried a null and {@code POST /nodal-records/{rn}/forward-to-re} could not address any of them —
     * the tab listed rows whose primary action was unreachable.
     */
    private void seedNodalRecords(List<Complaint> complaints, List<Bank> banks, LocalDateTime now) {
        for (int i = 0; i < complaints.size(); i++) {
            Complaint c = complaints.get(i);
            int seq = i + 1;
            String bank = entityName(banks, seq);
            String slug = slug(bank);
            // The award and advisory dates belong only on complaints that have reached a decision; on the
            // rest they would assert a determination that has not been made.
            boolean decided = seq == 11 || seq == 12 || seq == 17;
            boolean forwardedToRe = "RE".equals(c.getAssignedRole());

            nodalOfficerRecordRepository.save(NodalOfficerRecord.builder()
                    .complaintNumber(c.getComplaintNumber())
                    .recordNumber(recordNumber(c.getComplaintNumber()))
                    .entityCode(c.getEntityCode())
                    .entityName(bank)
                    .nodalOfficerName("NO " + PEOPLE.get(seq % PEOPLE.size()).district())
                    .pnoName("PNO " + PEOPLE.get((seq + 1) % PEOPLE.size()).district())
                    .designation("Assistant General Manager")
                    .email("nodal" + seq + "@" + slug + ".example.in")
                    .phone("0802200" + String.format("%04d", seq))
                    .pnoEmail("pno.record" + seq + "@" + slug + ".example.in")
                    .pnoMobile("0802201" + String.format("%04d", seq))
                    .processingOffice("Bengaluru")
                    .designatedOffice("Bengaluru Regional Office")
                    .reResponseDeadline(now.plusDays(21).toLocalDate())
                    .deadlineCommunication("INFORMATION_REQUEST")
                    .assignedTo(c.getAssignedOfficer())
                    .slaDays(30)
                    .moduleName(MODULES.get(seq % MODULES.size()))
                    .atmComplaint(seq % 4 == 0 ? "Y" : "N")
                    .disputeAmount(disputedAmount(seq))
                    .compensationLoss(decided ? new BigDecimal("4500.00") : null)
                    .compensationMental(decided ? new BigDecimal("1000.00") : null)
                    .advisoryComplianceDate(decided ? now.minusDays(4).toLocalDate() : null)
                    .awardImplementationDate(seq == 17 ? now.plusDays(15).toLocalDate() : null)
                    .awardAcceptanceDate(seq == 17 ? now.minusDays(2).toLocalDate() : null)
                    .notice131ComplyDate(forwardedToRe ? now.plusDays(14).toLocalDate() : null)
                    .forwardedToReAt(forwardedToRe ? now.minusDays(6) : null)
                    .build());
        }
    }

    /** The address {@code forward-to-re} and the nodal-record comment routes use for a record. */
    private static String recordNumber(String complaintNumber) {
        return "NOR-" + complaintNumber;
    }

    /** The Contact Entity and Summary tabs' Module field, which had no data source at all. */
    private static final List<String> MODULES =
            List.of("Deposit Accounts", "Cards", "Digital Payments", "Loans and Advances", "Pension");

    /** A stable disputed amount per complaint, so the money fields differ row to row. */
    private static BigDecimal disputedAmount(int seq) {
        return BigDecimal.valueOf(5_000L + (long) seq * 1_750L);
    }

    private void seedAttachments(List<Complaint> complaints, LocalDateTime now) {
        for (int i = 0; i < complaints.size(); i++) {
            int seq = i + 1;
            if (NO_ATTACHMENT.contains(seq)) {
                continue;
            }
            Complaint c = complaints.get(i);
            String type = DOCUMENT_TYPES.get(i % DOCUMENT_TYPES.size());
            attachmentRepository.save(ComplaintAttachment.builder()
                    .complaintId(c.getId())
                    .fileName("dev-" + seq + "-" + type.toLowerCase(java.util.Locale.ROOT) + ".pdf")
                    .originalName(type.charAt(0) + type.substring(1).toLowerCase(java.util.Locale.ROOT)
                            .replace('_', ' ') + ".pdf")
                    .contentType("application/pdf")
                    .fileSize(48_000L + seq * 1_024L)
                    .storagePath("dev-local/cepc/" + c.getComplaintNumber().replace('/', '_') + "/" + seq + ".pdf")
                    .documentType(type)
                    .source("SEED")
                    .uploadedBy(c.getAssignedOfficer())
                    .uploadedAt(now.minusDays(2))
                    .build());
        }
    }

    private void seedReadState(List<Complaint> complaints, LocalDateTime now) {
        for (Integer seq : READ_BY_ME) {
            readStateRepository.save(ComplaintReadState.builder()
                    .complaintId(complaints.get(seq - 1).getId())
                    .userId(ME)
                    .firstReadAt(now.minusDays(3))
                    .lastReadAt(now.minusDays(1))
                    .build());
        }
    }

    /**
     * Two half-finished intake forms for the staff-draft resume screen.
     *
     * <p>The displayable columns come out of {@code FORM_DATA_JSON}, not out of columns of their own — a
     * staff draft is a snapshot of a part-filled form, so it has no complainant or entity column to read.
     *
     * <p>Not the source of the dashboard's Draft tab: that tab lists complaints in status {@code DRAFT}, and a
     * staff draft has no complaint row at all, so it could never carry a status to be selected on.
     */
    private void seedStaffDrafts(LocalDateTime now) {
        // Guarded per row like the reference-table seeders: the reseed above purges seed COMPLAINTS, but a
        // staff draft is keyed on (milestone, owner, complaint number) and survives it. Unguarded, the
        // ASSESSMENT row breaks the boot on UK_STAFF_DRAFT_OWNER_MILESTONE, and the REGISTER row — whose
        // NULL complaint number the unique key treats as distinct — quietly adds a duplicate every reseed.
        if (staffDraftRepository.findFirstByMilestoneAndOwnerUserIdAndComplaintNumberIsNull(
                StaffDraft.Milestone.REGISTER.name(), ME).isEmpty()) {
            staffDraftRepository.save(StaffDraft.builder()
                .milestone(StaffDraft.Milestone.REGISTER.name())
                .ownerUserId(ME)
                .draftStatus(StaffDraft.STATUS_IN_PROGRESS)
                .formDataJson("""
                        {"complainantName":"Nagaraju","filingType":"PORTAL",
                         "entityName":"State Bank of India","complaintCategory":"Loans and advances",
                         "priority":"HIGH","subject":"Foreclosure charges dispute, details pending"}""")
                .createdAt(now.minusDays(4))
                .updatedAt(now.minusDays(1))
                .build());
        }

        if (staffDraftRepository.findByMilestoneAndOwnerUserIdAndComplaintNumber(
                StaffDraft.Milestone.ASSESSMENT.name(), ME, complaintNumber(9)).isEmpty()) {
            staffDraftRepository.save(StaffDraft.builder()
                .milestone(StaffDraft.Milestone.ASSESSMENT.name())
                .ownerUserId(ME)
                .complaintNumber(complaintNumber(9))
                .draftStatus(StaffDraft.STATUS_DRAFT)
                .formDataJson("""
                        {"complainantName":"Sudhi panigrahi","filingType":"EMAIL",
                         "entityName":"Canara Bank","complaintCategory":"Deposit accounts",
                         "priority":"MEDIUM","subject":"Assessment notes not yet complete"}""")
                .createdAt(now.minusDays(2))
                .updatedAt(now.minusHours(6))
                .build());
        }
    }

    /** Offices of type CEPC, keyed by code, for the office picker and the office-scoped assignment filter. */
    private static final List<String[]> OFFICES = List.of(
            new String[]{OFFICE_CODE, "Bengaluru"},
            new String[]{"HYD", "Hyderabad"},
            new String[]{"MAA", "Chennai-I"},
            new String[]{"BOM", "Mumbai-I"},
            new String[]{"DEL", "New Delhi-I"});

    /**
     * The office dropdowns' data source.
     *
     * <p>{@code OFFICE_CODE_MASTER} is empty on a fresh database, so {@code /api/v1/keycloak/offices}
     * answered with an empty array and the Forward tab offered no destination at all. Seeded with
     * {@code officeType='CEPC'} because that is what the CEPC branch of the transfer form filters on.
     */
    /**
     * The three destination masters the Forward tab reads, none of which had a dev row.
     *
     * <p>Each of the tab's three branches validates its destination against one of these and refuses the
     * forward when it is not found, so with the masters empty the dropdowns rendered blank and every forward
     * failed — the tab could be opened but not used.
     *
     * <p>The capacity rows matter for the same reason and are less obvious. {@code claimCapacity} is a
     * conditional UPDATE, so an office with NO threshold row updates zero rows and is read as being AT
     * capacity: correct to fail closed on an office that has declared none, but it meant the CRPC Head
     * could never approve an office transfer here. {@code MAA} is seeded already full, so the
     * at-capacity refusal can be seen as well as the successful path.
     */
    private void seedForwardTargets() {
        record Body(String code, String name, String email, String jurisdiction) {}
        List<Body> bodies = List.of(
                new Body("SEBI", "Securities and Exchange Board of India", "grievance@sebi.gov.in",
                        "Securities and capital markets"),
                new Body("IRDAI", "Insurance Regulatory and Development Authority of India",
                        "complaints@irdai.gov.in", "Insurance"),
                new Body("PFRDA", "Pension Fund Regulatory and Development Authority",
                        "grievance@pfrda.org.in", "Pensions"),
                new Body("NHB", "National Housing Bank", "grievance@nhb.org.in", "Housing finance"));
        for (Body b : bodies) {
            if (regulatoryBodyRepository.findByBodyCodeIgnoreCase(b.code()).isPresent()) {
                continue;
            }
            regulatoryBodyRepository.save(RegulatoryBodyMaster.builder()
                    .bodyCode(b.code())
                    .bodyName(b.name())
                    .contactEmail(b.email())
                    .emailVerified("Y")
                    .jurisdiction(b.jurisdiction())
                    .isActive("Y")
                    .createdBy("dev-seed")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        record Dept(String code, String name, String email, String roleGroup, int order) {}
        List<Dept> departments = List.of(
                new Dept("DPSS", "Department of Payment and Settlement Systems", "dpss@rbi.org.in",
                        "CEPC_DO", 1),
                new Dept("DOR", "Department of Regulation", "dor@rbi.org.in", "CEPC_DO", 2),
                new Dept("DOS", "Department of Supervision", "dos@rbi.org.in", "CEPC_DO", 3),
                new Dept("FIDD", "Financial Inclusion and Development Department", "fidd@rbi.org.in",
                        "CEPC_DO", 4));
        for (Dept d : departments) {
            if (departmentRepository.findByDeptCodeIgnoreCase(d.code()).isPresent()) {
                continue;
            }
            departmentRepository.save(RbiDepartmentMaster.builder()
                    .deptCode(d.code())
                    .deptName(d.name())
                    .contactEmail(d.email())
                    .assignRoleGroup(d.roleGroup())
                    .isActive("Y")
                    .displayOrder(d.order())
                    .createdBy("dev-seed")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        record Capacity(String officeId, String officeName, int max, int used, int order) {}
        List<Capacity> capacities = List.of(
                new Capacity("BLR", "Bengaluru", 200, 18, 1),
                new Capacity("HYD", "Hyderabad", 200, 40, 2),
                new Capacity("BOM", "Mumbai-I", 200, 75, 3),
                new Capacity("DEL", "New Delhi-I", 200, 60, 4),
                new Capacity("MAA", "Chennai-I", 50, 50, 5));
        for (Capacity c : capacities) {
            if (thresholdRepository.findByOfficeId(c.officeId()).isPresent()) {
                continue;
            }
            thresholdRepository.save(OfficeThresholdConfig.builder()
                    .officeId(c.officeId())
                    .officeName(c.officeName())
                    .department("CEPC")
                    .maxThreshold(c.max())
                    .currentCount(c.used())
                    .overflowSequenceOrder(c.order())
                    .active(true)
                    .updatedBy("dev-seed")
                    .updatedAt(LocalDateTime.now())
                    .build());
        }

        // An office with no strategy row rotates among RBIO_OFFICER, which is the right default for an
        // Ombudsman office and wrong for every office here: this seed's roster holds CEPC ranks only, so the
        // rotation found nobody and an approved transfer landed on a complaint with NO owner — the Head's
        // decision succeeded and the file then belonged to no one.
        for (Capacity c : capacities) {
            if (strategyRepository.findByOfficeId(c.officeId()).isPresent()) {
                continue;
            }
            strategyRepository.save(OfficeAssignmentStrategy.builder()
                    .officeId(c.officeId())
                    .strategy(OfficeAssignmentStrategy.ROUND_ROBIN)
                    .roleGroup("CEPC_DO")
                    .updatedBy("dev-seed")
                    .reason("Dev seed: rotate incoming transfers among the office's CEPC dealing officers")
                    .build());
        }
    }

    private void seedOffices() {
        for (String[] office : OFFICES) {
            if (officeCodeRepository.findByOfficeCodeAndIsActiveTrue(office[0]).isPresent()) {
                continue;
            }
            officeCodeRepository.save(OfficeCodeMaster.builder()
                    .officeCode(office[0])
                    .officeName(office[1])
                    .officeType("CEPC")
                    .isActive(true)
                    .build());
        }
    }

    /**
     * The CEPC complaint-assignment roster ({@link #OFFICER_ROWS}) plus the DEO pool, which is what makes
     * availability, next-assignee and the Email Communication tab's assignee list answer.
     *
     * <p>{@code wf_officer_pool} is the exclusion list {@code DurableRoundRobinAssigner} consults, and it
     * is empty on a fresh database — so with Keycloak unreachable in dev-local every assignment dialog
     * opened with nobody in it.
     *
     * <p>The roster half is deleted and reinserted by native SQL, with the given ids, on every boot — both
     * this seeder's own earlier ad-hoc usernames ({@link #LEGACY_OFFICER_USER_IDS}) and this roster's own
     * rows, so a running database always holds exactly {@link #OFFICER_ROWS} rather than accumulating
     * whichever shape an earlier revision left. {@code wf_officer_pool.id} is IDENTITY-generated, so a JPA
     * insert cannot honour the specific ids the roster was handed over with — hence native SQL rather than
     * {@code AaOfficerPoolRepository} for this half.
     *
     * <p><b>{@code regional_office} is {@link #OFFICE_CODE}, not the {@code 'Bangalore'} the roster was
     * handed over with.</b> {@code CepcComplaintSearchService} scopes every CEPC listing on
     * {@code regionalOffice = }<i>the caller's pool office</i>, and each seeded complaint carries
     * {@code OFFICE_CODE} ("BLR"), so the literal spelling matched no complaint and left the dashboard
     * empty for all nine of these officers.
     */
    private void seedOfficerPool() {
        List<String> obsoleteUserIds = new ArrayList<>(LEGACY_OFFICER_USER_IDS);
        OFFICER_ROWS.forEach(row -> obsoleteUserIds.add(row.userId()));
        entityManager.createNativeQuery("DELETE FROM wf_officer_pool WHERE user_id IN :userIds")
                .setParameter("userIds", obsoleteUserIds)
                .executeUpdate();
        for (OfficerRow row : OFFICER_ROWS) {
            entityManager.createNativeQuery(
                            "INSERT INTO wf_officer_pool "
                                    + "(id, is_active, current_workload, display_name, max_workload, "
                                    + "is_on_leave, regional_office, role_group, skill_languages, user_id) "
                                    + "VALUES (:id, 1, 0, :displayName, :maxWorkload, 0, :office, "
                                    + ":roleGroup, NULL, :userId)")
                    .setParameter("office", OFFICE_CODE)
                    .setParameter("id", row.id())
                    .setParameter("displayName", row.displayName())
                    .setParameter("maxWorkload", row.maxWorkload())
                    .setParameter("roleGroup", row.roleGroup())
                    .setParameter("userId", row.userId())
                    .executeUpdate();
        }

        // The DEO pool: a separate feature (the Email Communication tab's intake assignment), untouched by
        // the roster above. Insert-if-absent, unlike the roster: these rows are not part of the given
        // roster and would duplicate on every boot without a guard. Thresholds deliberately differ from
        // each other and from the frontend's own fallback of 20, so a load bar drawn against the wrong
        // denominator is visible rather than coincidentally correct; the offices differ so the dialog's
        // office filter has something to exclude; and cepc.deo3 is on leave because an all-available roster
        // cannot show that leave is honoured rather than merely stored.
        record PoolRow(String userId, String displayName, String roleGroup, String office,
                       int threshold, boolean onLeave, int load) {}
        List<PoolRow> deoRows = List.of(
                new PoolRow(DEO_ONE, "Kavya Shetty", "DEO", OFFICE_CODE, 15, false, 0),
                new PoolRow(DEO_TWO, "Imran Khan", "DEO", OFFICE_CODE, 25, false, 0),
                new PoolRow("cepc.deo3", "Priya Bhat", "DEO", "HYD", 12, true, 0),
                new PoolRow("cepc.deo4", "Arun Kulkarni", "DEO", "BOM", 30, false, 0));

        for (PoolRow row : deoRows) {
            if (officerPoolRepository.findByUserIdAndRoleGroup(row.userId(), row.roleGroup()).isPresent()) {
                continue;
            }
            officerPoolRepository.save(AaOfficerPool.builder()
                    .userId(row.userId())
                    .displayName(row.displayName())
                    .roleGroup(row.roleGroup())
                    .regionalOffice(row.office())
                    .active(true)
                    .onLeave(row.onLeave())
                    .maxWorkload(row.threshold())
                    .build());

            // current_workload is mapped insertable=false because cms-workflow-service owns that counter,
            // so JPA cannot write it and a native statement is the only way to give the roster a spread of
            // loads. Lower-case unquoted table name: the MySQL container runs lower_case_table_names=0.
            entityManager.createNativeQuery(
                            "UPDATE wf_officer_pool SET current_workload = :load "
                                    + "WHERE user_id = :userId AND role_group = :roleGroup")
                    .setParameter("load", row.load())
                    .setParameter("userId", row.userId())
                    .setParameter("roleGroup", row.roleGroup())
                    .executeUpdate();
        }
    }

    /**
     * NO/PNO contacts for the entities the seeded complaints name, which the Summary tab's entity panel and
     * the Contact Entity tab both read.
     *
     * <p>{@code REGULATED_ENTITIES} ships 145 rows and not one of them carries a contact, and
     * {@code ENTITY_OFFICE_NODAL_OFFICER} is empty — so every contact field on both tabs was null and the
     * screen was indistinguishable from one whose join had broken. Three of the twelve seeded banks had no
     * entity-master row at all, which also left the entity picker unable to resolve them.
     *
     * <p>The two sources are seeded with DIFFERENT values on purpose. The roster is meant to win field by
     * field over the master, and identical values everywhere would make a broken precedence rule look
     * correct. Three states are therefore represented: most entities have a full roster row, the fourth has
     * a roster row carrying ONLY the PNO — so its nodal officer must still come from the master — and the
     * last has no roster row at all, which is the {@code contactSource: ENTITY_MASTER} fallback.
     *
     * <p>The entity list is DERIVED from the same {@link #entityName} the complaints are built with rather
     * than written out here, so it cannot drift from the entities the seeded complaints actually name — and
     * the three states are chosen by position for the same reason, a named bank being one bank-master edit
     * away from silently not existing.
     */
    private void seedEntityContacts() {
        List<Bank> banks = bankRepository.findAll();
        List<String> names = new ArrayList<>(new LinkedHashSet<>(
                java.util.stream.IntStream.rangeClosed(1, SPECS.size())
                        .mapToObj(seq -> entityName(banks, seq))
                        .toList()));

        for (String bank : names) {
            String normalized = RegulatedEntity.normalize(bank);
            RegulatedEntity entity = regulatedEntityRepo.findByNameNormalized(normalized).orElse(null);
            if (entity == null) {
                entity = RegulatedEntity.builder()
                        .name(bank)
                        .nameNormalized(normalized)
                        .department("RBIO")
                        .entityType("Private Sector Bank")
                        .status("active")
                        .portalEnabled(true)
                        .build();
            } else if (entity.getNodalOfficerName() != null) {
                continue;
            }
            String slug = slug(bank);
            entity.setNodalOfficerName("Master NO " + bank);
            entity.setNodalOfficerDesignation("Deputy General Manager");
            entity.setNodalOfficerEmail("no." + slug + "@" + slug + ".example.in");
            entity.setNodalOfficerPhone("0805550" + (100 + slug.length()));
            entity.setPnoName("Master PNO " + bank);
            entity.setPnoEmail("pno." + slug + "@" + slug + ".example.in");
            entity.setPnoPhone("0805551" + (100 + slug.length()));
            regulatedEntityRepo.save(entity);
        }

        for (int i = 0; i < names.size(); i++) {
            String bank = names.get(i);
            if (i == names.size() - 1) {
                continue;
            }
            String normalized = RegulatedEntity.normalize(bank);
            if (nodalOfficerRepo.findFirstByEntityNameNormalizedAndProcessingOfficeIsNullAndActiveTrue(
                    normalized).isPresent()) {
                continue;
            }
            String slug = slug(bank);
            boolean pnoOnly = i == 3;
            nodalOfficerRepo.save(EntityOfficeNodalOfficer.builder()
                    .entityName(bank)
                    .entityNameNormalized(normalized)
                    .nodalOfficerName(pnoOnly ? null : "Roster NO " + bank)
                    .nodalOfficerDesignation(pnoOnly ? null : "Chief Nodal Officer")
                    .nodalOfficerEmail(pnoOnly ? null : "roster.no." + slug + "@" + slug + ".example.in")
                    .nodalOfficerPhone(pnoOnly ? null : "0805552" + (100 + slug.length()))
                    .pnoName("Roster PNO " + bank)
                    .pnoEmail("roster.pno." + slug + "@" + slug + ".example.in")
                    .pnoPhone("0805553" + (100 + slug.length()))
                    .active(true)
                    .createdBy("cepc-dev-seeder")
                    .build());
        }
    }

    /** A stable, address-safe token for a bank name, so the seeded emails are readable and deterministic. */
    private static String slug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    /**
     * Intake drafts assigned to the seeded DEOs, which is where the roster's {@code currentLoad} comes from.
     *
     * <p>{@code /email-syndication/deo} counts OPEN {@code EMAIL_DRAFTS} rows per assignee, so with the
     * table empty every DEO reports load 0 — and a load bar that is uniformly empty cannot distinguish a
     * working count from the structurally-zero one this endpoint used to return. The split is deliberately
     * uneven (three against one) so the automatic pick, which sorts on remaining headroom, has a single
     * unambiguous answer that alphabetical order would get wrong.
     *
     * <p>One CONVERTED row is included: it is work that has already left the queue, so a count that includes
     * it is the bug where a DEO's load can only ever rise.
     */
    private void seedIntakeDrafts() {
        record DraftRow(String draftId, String assignee, String subject, DraftStatus status) {}

        List<DraftRow> rows = List.of(
                new DraftRow("DRF-DEV-0001", DEO_ONE, "Unauthorised UPI debit - request for reversal",
                        DraftStatus.ASSIGNED),
                new DraftRow("DRF-DEV-0002", DEO_ONE, "Credit card annual fee reversal not honoured",
                        DraftStatus.IN_PROGRESS),
                new DraftRow("DRF-DEV-0003", DEO_ONE, "ATM cash not dispensed, account debited",
                        DraftStatus.PENDING_MANUAL_ENTRY),
                new DraftRow("DRF-DEV-0004", DEO_TWO, "Home loan EMI debited twice in one month",
                        DraftStatus.SENT_TO_REVIEWER),
                new DraftRow("DRF-DEV-0005", DEO_TWO, "Locker rent charged after closure",
                        DraftStatus.CONVERTED));

        LocalDateTime now = LocalDateTime.now();
        int day = 1;
        for (DraftRow row : rows) {
            if (draftRepository.findByDraftId(row.draftId()).isPresent()) {
                continue;
            }
            draftRepository.save(EmailDraft.builder()
                    .draftId(row.draftId())
                    .messageId("mail-intake-dev-" + row.draftId())
                    .threadId("THREAD-DEV-" + row.draftId())
                    .senderEmail("complainant" + day + "@example.com")
                    .toRecipients("cepc.intake@rbi.org.in")
                    .subject(row.subject())
                    .body(row.subject() + ". Received by email and awaiting data entry.")
                    .complainantName(PEOPLE.get(day % PEOPLE.size()).name())
                    .modeOfReceipt("EMAIL")
                    .status(row.status().name())
                    .assignedTo(row.assignee())
                    .targetOffice(OFFICE_CODE)
                    .receivedAt(now.minusDays(day))
                    .createdAt(now.minusDays(day))
                    .updatedAt(now.minusDays(day))
                    .build());
            day++;
        }
    }

    /**
     * The outbound recipient allowlist, widened to the entity domains this seeder itself creates.
     *
     * <p>Sits with the other reference seeds, ahead of the complaint guard, for the reason given there: a
     * database seeded before this method existed must still receive the row.
     *
     * <p><b>Without it the Email Communication tab cannot send to an entity at all.</b>
     * {@code SYSTEM_CONFIG} is empty on a fresh database, so
     * {@link ComplaintEmailService#allowedDomains()} falls back to {@code DEFAULT_ALLOWED_DOMAINS} —
     * {@code rbi.org.in} and {@code rbi.gov.in} — and {@code isAllowedDomain} is an exact per-domain
     * suffix match. Every nodal officer this seeder writes is at {@code <entity>.example.in}, so every
     * send to one was refused and stored {@code FAILED} with a masked address. That is the guard working
     * as designed against a configuration that had never been supplied.
     *
     * <p>The domain list is DERIVED from the same {@link #entityName}/{@link #slug} pair the contacts are
     * built from rather than written out here, so it cannot drift from the addresses actually seeded — the
     * argument {@link #seedEntityContacts()} already makes for the entity list itself. The RBI defaults are
     * kept because internal mail to the regulator must keep working.
     */
    private void seedAllowedEmailDomains() {
        if (systemConfigRepository.findByConfigKey(
                ComplaintEmailService.KEY_ALLOWED_RECIPIENT_DOMAINS).isPresent()) {
            return;
        }
        List<Bank> banks = bankRepository.findAll();
        LinkedHashSet<String> domains = new LinkedHashSet<>(List.of("rbi.org.in", "rbi.gov.in"));
        for (int seq = 1; seq <= SPECS.size(); seq++) {
            domains.add(slug(entityName(banks, seq)) + ".example.in");
        }
        systemConfigRepository.save(SystemConfig.builder()
                .configKey(ComplaintEmailService.KEY_ALLOWED_RECIPIENT_DOMAINS)
                .configValue(String.join(",", domains))
                .description("Dev-local: RBI plus the seeded entity domains, so the Email Communication "
                        + "tab can actually reach a nodal officer.")
                .updatedBy("cepc-dev-seeder")
                .updatedAt(LocalDateTime.now())
                .build());
    }

    /**
     * The Summary tab's assessment block, for every complaint.
     *
     * <p>These ~45 fields live in {@code CEPC_COMPLAINT_ASSESSMENT} rather than on {@code COMPLAINTS}
     * because {@code Complaint} carries an {@code @Version}, and an officer editing the summary must not
     * collide with a concurrent workflow transition on the same row. Seeding all of them — not a subset —
     * is deliberate: an empty assessment row and a broken join render identically, so a partially seeded
     * set would leave the read path unverifiable on exactly the complaints that had no row.
     *
     * <p>{@code moduleName}, {@code entityBranchCategory}, {@code branchCenterName} and
     * {@code atmCreditDebitCard} are the four the Contact Entity list showed blank: they have no column on
     * {@code Complaint} and no master table behind them, so this is their only source.
     */
    private void seedAssessments(List<Complaint> complaints, List<Bank> banks, LocalDateTime now) {
        for (int i = 0; i < complaints.size(); i++) {
            Complaint c = complaints.get(i);
            int seq = i + 1;
            String bank = entityName(banks, seq);
            Person p = PEOPLE.get(SPECS.get(i).person());
            RegulatedEntity entity =
                    regulatedEntityRepo.findByNameNormalized(RegulatedEntity.normalize(bank)).orElse(null);

            CepcComplaintAssessment a = CepcComplaintAssessment.builder()
                    .complaintNumber(c.getComplaintNumber())
                    .officerComments("Entity reply examined against the Scheme; grounds recorded below.")
                    .complaintCpgram(seq % 6 == 0)
                    .cpgramNumber(seq % 6 == 0 ? "CPGRAM/2026/" + String.format("%05d", seq) : null)
                    .regulatedEntityId(entity == null ? null : entity.getId())
                    .entityName(bank)
                    .moduleName(MODULES.get(seq % MODULES.size()))
                    .entityCategory("Scheduled Commercial Bank")
                    .bsrCode(String.format("%07d", 1_000_000 + seq))
                    .entityPincode(String.format("5600%02d", seq))
                    .entityCountry("India")
                    .entityState(p.state())
                    .entityDistrict(p.district())
                    .entityCity(p.district())
                    .entityBranchName(p.district() + " Main Branch")
                    .entityBranchCategory(seq % 3 == 0 ? "Metro" : seq % 3 == 1 ? "Urban" : "Semi-Urban")
                    .branchCenterName(p.district() + " Centre")
                    .entityAddress("Ground Floor, 12 MG Road, " + p.district() + ", " + p.state())
                    .registrationWithRbiDate(LocalDate.of(1995, 4, 1))
                    .complaintCategoryText(MODULES.get(seq % MODULES.size()))
                    .complaintSubCategory1(seq % 2 == 0 ? "Unauthorised transaction" : "Service deficiency")
                    .complaintSubCategory2(seq % 2 == 0 ? "Electronic banking" : "Branch service")
                    .complaintRegistrationDateValid(true)
                    .dateOfFilingComplaint(c.getFiledAt().toLocalDate())
                    .reminderSent(seq % 4 == 0)
                    .disputedAmount(disputedAmount(seq))
                    // 0/1 rather than a boolean: this is the Yes/No radio pair the client serialises as an
                    // integer, and the column type follows the wire format rather than correcting it.
                    .compensationSought(seq % 3 == 0 ? 1 : 0)
                    .legalCaseFiled(seq % 9 == 0)
                    .preEnquiryReceived(seq % 5 == 0)
                    .highPriorityComplaint("HIGH".equals(c.getPriority()))
                    .loanDisposalAmount(seq % 4 == 0 ? new BigDecimal("125000.00") : null)
                    .additionalComments("Seeded assessment for dev-local.")
                    .vernacularLanguage(seq % 7 == 0 ? "Kannada" : "English")
                    .complaintRegardingPension("Pension".equals(MODULES.get(seq % MODULES.size())))
                    .complaintAgainstBusinessCorrespondent(seq % 8 == 0)
                    .atmCreditDebitCard(seq % 4 == 0)
                    .schemeFlag("RB-IOS 2021")
                    .groundsFlag(seq % 2 == 0 ? "CLAUSE_8" : "CLAUSE_10")
                    .rboCgpcOld("CGPC/" + OFFICE_CODE)
                    .freeMarkedComplaint(seq % 10 == 0)
                    .replyWithin30Days(seq % 3 == 0 ? "NO" : "YES")
                    .proposedComplaintType("MAINTAINABLE")
                    .lastModifiedBy(c.getAssignedOfficer())
                    .createdAt(c.getFiledAt().plusDays(1))
                    .lastModifiedAt(now.minusDays(2))
                    .build();

            applyDecision(a, seq, now);
            assessmentRepository.save(a);
        }
    }

    /**
     * The Final Decision tab's narrative fields, on the three complaints that have reached one.
     *
     * <p>Keyed to the statuses {@link #SPECS} actually assigns rather than to fixed positions: #11 is the
     * closed one, #12 the withdrawn one and #17 the one awaiting closure. Putting a decision on a complaint
     * still under examination would seed a determination that the workflow has not made, and the tab would
     * then show a closure clause on a file nobody has closed.
     */
    private void applyDecision(CepcComplaintAssessment a, int seq, LocalDateTime now) {
        switch (seq) {
            case 11 -> {
                a.setFinalDecisionAction("CLOSE");
                a.setClosureClauseDraft("CLAUSE_8_1_A");
                a.setClosureClauseDescription("Complaint resolved by the entity to the satisfaction of "
                        + "the complainant; no further intervention called for.");
                a.setGistOfCase("Overdraft limit was restored and the excess interest reversed after the "
                        + "office took up the matter with the nodal officer.");
                a.setGistOfCaseRegional("ದೂರು ಬಗೆಹರಿಸಲಾಗಿದೆ.");
                a.setSpeakingOrderGenerated(true);
                a.setSpeakingOrderContent("Having considered the submissions of both parties, the "
                        + "complaint is closed as resolved.");
                a.setComplaintStatusOnPortal("CLOSED");
                a.setCompensationLoss(new BigDecimal("4500.00"));
                a.setCompensationMental(new BigDecimal("1000.00"));
                a.setAdvisoryComplianceDate(now.minusDays(4).toLocalDate());
                a.setCrpcProposedAction("CLOSE");
                a.setProposedClause("CLAUSE_8_1_A");
            }
            case 12 -> {
                a.setFinalDecisionAction("WITHDRAW");
                a.setRejectWithdrawSettleSubAction("WITHDRAWN_BY_COMPLAINANT");
                a.setRejectWithdrawSettleReason("The duplicate NEFT debit was reversed by the branch and "
                        + "the complainant asked in writing for the complaint to be withdrawn.");
                a.setComplaintStatusOnPortal("WITHDRAWN");
                a.setCrpcProposedAction("WITHDRAW");
            }
            case 17 -> {
                a.setFinalDecisionAction("AWARD");
                a.setProposedClause("CLAUSE_10_2");
                a.setCrpcProposedAction("AWARD");
                a.setGistOfCase("Compensation was credited; the closure letter is pending signature.");
                a.setSystemicIssue("Delay between compensation credit and issue of the closure letter.");
                a.setAwardAcceptanceDate(now.minusDays(2).toLocalDate());
                a.setAwardImplementationDate(now.plusDays(15).toLocalDate());
                a.setCompensationLoss(new BigDecimal("12000.00"));
                a.setCompensationMental(new BigDecimal("2500.00"));
                a.setComplaintStatusOnPortal("AWAITING_CLOSURE");
            }
            default -> {
                // No decision recorded: the rest are still in the workflow.
            }
        }
    }

    /**
     * The Summary tab's maintainability panel on six complaints, so the read path is exercised both ways.
     *
     * <p>A subset rather than all of them on purpose: the block is emitted for every key whether or not a row
     * exists, so the ones WITHOUT answers are what shows that an unanswered question reads back as null
     * instead of silently defaulting to No — a false negative on maintainability is how a complaint gets
     * wrongly rejected.
     */
    private void seedEligibilityAnswers(List<Complaint> complaints) {
        List<Integer> answered = List.of(1, 2, 4, 8, 11, 17);
        List<String> booleanKeys = List.of(
                "entityRegulatedByRbi", "complaintNotDirectlyAddressedToOmbudsman",
                "complaintNotRegisteredWithEntity", "frivolousVexatiousThreatening",
                "subJudiceOrArbitration", "sameGrievancePendingBeforeCourt",
                "sameGrievanceSettledBeforeCourt", "complaintMadeThroughAdvocate",
                "complainantIsAdvocate", "sameGrievancePendingBeforeOmbudsman",
                "alreadyDealtWithByOmbudsman", "complaintAgainstManagement",
                "complaintFiledWithCEPCOrRBI", "disputeBetweenREs",
                "staffOfREEmployerRelationship", "completeInformationUnavailable",
                "writtenComplaintFiledWithRE", "receivedReplyFromEntity");

        for (Integer seq : answered) {
            Complaint c = complaints.get(seq - 1);
            for (int k = 0; k < booleanKeys.size(); k++) {
                String key = booleanKeys.get(k);
                // The first two and the last two are the ones that must be true for a complaint to be
                // maintainable at all; the disqualifying questions in between are answered No. Mixing them
                // by position would seed a set that no real complaint could hold.
                boolean answer = switch (key) {
                    case "entityRegulatedByRbi", "complaintNotDirectlyAddressedToOmbudsman",
                         "writtenComplaintFiledWithRE", "receivedReplyFromEntity" -> true;
                    default -> false;
                };
                eligibilityRepository.save(ComplaintEligibilityAnswer.builder()
                        .complaintNumber(c.getComplaintNumber())
                        .questionKey(key)
                        .booleanAnswer(answer)
                        .build());
            }
            eligibilityRepository.save(ComplaintEligibilityAnswer.builder()
                    .complaintNumber(c.getComplaintNumber())
                    .questionKey("firstFiledWithREDate")
                    .dateAnswer(c.getFiledAt().minusDays(45).toLocalDate())
                    .build());
            eligibilityRepository.save(ComplaintEligibilityAnswer.builder()
                    .complaintNumber(c.getComplaintNumber())
                    .questionKey("replyDate")
                    .dateAnswer(c.getFiledAt().minusDays(10).toLocalDate())
                    .build());
        }
    }

    /**
     * The Conciliation tab, in all three states the GET can return.
     *
     * <p>#2, #3, #27 and #28 are the four the dashboard's Meeting Scheduled tab counts, so their meeting is
     * OPEN and {@code current} is non-null. All four, not two: #27 and #28 claimed the status with no meeting
     * row at all, which is a complaint whose tab says a meeting is ahead and whose Conciliation tab is empty.
     * #8 has a completed meeting AND a follow-up, which is the case where
     * {@code history} and {@code current} are both populated. #11 has a completed meeting and nothing
     * open — the {@code {current: null, history: [...]}} shape, which is the one a client is most likely
     * to mishandle and so the one worth seeding deliberately.
     *
     * <p>{@code Complaint.conciliationOutcome} and {@code conciliationDate} are mirrored here for the same
     * reason the PUT mirrors them: the dashboard reads the complaint, the tab reads the meetings, and a
     * seed that set only one of the two would show a settled complaint whose grid row disagrees.
     */
    private void seedConciliation(List<Complaint> complaints, LocalDateTime now) {
        // Open meetings: every complaint the Meeting Scheduled tab is counting. Keep this list and the
        // MEETING_SCHEDULED rows of SPECS in step — a status without a meeting row is the mismatch the tab
        // was reported for, read from the other end.
        for (int seq : new int[]{2, 3, 27, 28}) {
            Complaint c = complaints.get(seq - 1);
            meetingRepository.save(meeting(c, 1, "SCHEDULED", now.plusDays(7).toLocalDate(), "11:30",
                    null, null, true,
                    "Joint conciliation meeting convened over video conference.", null, now));
            c.setConciliationDate(now.plusDays(7));
        }

        // Completed, then reconvened: history AND current are both non-empty.
        Complaint eight = complaints.get(7);
        meetingRepository.save(meeting(eight, 1, "COMPLETED", now.minusDays(12).toLocalDate(), "15:00",
                true, false, false,
                "The entity disputed the extent of the loss; no agreement reached.",
                "Complainant accepted the facts; the entity did not accept liability.", now));
        meetingRepository.save(meeting(eight, 2, "SCHEDULED", now.plusDays(10).toLocalDate(), "10:00",
                null, null, true, "Reconvened to consider the revised offer.", null, now));
        eight.setConciliationDate(now.plusDays(10));
        // NOT_SETTLED, not null: meeting 1 concluded without agreement, and that is a determination the
        // grid must show rather than an absence of one. The open follow-up does not erase it.
        eight.setConciliationOutcome("NOT_SETTLED");

        // Completed with nothing open: the {current: null, history: [...]} shape.
        Complaint eleven = complaints.get(10);
        meetingRepository.save(meeting(eleven, 1, "COMPLETED", now.minusDays(40).toLocalDate(), "12:00",
                true, true, false,
                "Both parties accepted the settlement terms and the complaint was closed.",
                "Settled in conciliation.", now));
        eleven.setConciliationOutcome("SETTLED");
        eleven.setConciliationDate(now.minusDays(40));
    }

    private CepcConciliationMeeting meeting(Complaint c, int sequenceNo, String status, LocalDate date,
                                            String time, Boolean byComplainant, Boolean byEntity,
                                            boolean vc, String meetingComments, String comments,
                                            LocalDateTime now) {
        return CepcConciliationMeeting.builder()
                .complaintNumber(c.getComplaintNumber())
                .sequenceNo(sequenceNo)
                .meetingStatus(status)
                .meetingDate(date)
                .meetingTime(time)
                .acceptedByComplainant(byComplainant)
                .acceptedByEntity(byEntity)
                .conductedThroughVc(vc)
                .meetingComments(meetingComments)
                .comments(comments)
                .createdBy(c.getAssignedOfficer())
                .createdAt(now.minusDays(14))
                .updatedBy(c.getAssignedOfficer())
                .updatedAt(now.minusDays(1))
                .build();
    }

    /**
     * One transfer awaiting the office head, so the Forward tab's "Other Office" branch and the CRPC head's
     * pending queue both have a row to act on.
     *
     * <p>{@code MAA} is deliberately NOT the destination: it is seeded at capacity so the refusal path can
     * be demonstrated, and a pending request the head cannot approve would be a poor default.
     */
    private void seedPendingTransfer(List<Complaint> complaints, LocalDateTime now) {
        Complaint c = complaints.get(3);
        transferRepository.save(InterOfficeTransfer.builder()
                .complaintNumber(c.getComplaintNumber())
                .fromOffice(OFFICE_CODE)
                .toOffice("HYD")
                .fromOfficeCode(OFFICE_CODE)
                .toOfficeCode("HYD")
                .transferType("CEPC_CEPC")
                .status("PENDING")
                .originModule("CEPC")
                .reason("The complainant has moved to Hyderabad and the entity branch is in that "
                        + "jurisdiction.")
                .requestedBy(c.getAssignedOfficer())
                .previousOwner(c.getAssignedOfficer())
                .requestedAt(now.minusDays(2))
                .build());
    }

    /**
     * RE response trackers for the three complaints forwarded to an entity.
     *
     * <p>One per {@code reActivityStatus} the specs assign, so the tracker and the dashboard's RE tabs tell
     * the same story: #5 has not opened the notice, #6 has answered — which is what moves it out of
     * "Sent to RE" and into "Response from RE" — and #7 is mid-ladder with the window still open.
     */
    private void seedReResponseTrackers(List<Complaint> complaints, List<Bank> banks, LocalDateTime now) {
        for (int seq : new int[]{5, 6, 7}) {
            Complaint c = complaints.get(seq - 1);
            RegulatedEntity entity = regulatedEntityRepo
                    .findByNameNormalized(RegulatedEntity.normalize(entityName(banks, seq))).orElse(null);
            if (entity == null) {
                // regulatedEntityId is NOT NULL, and a tracker naming no entity would be meaningless.
                continue;
            }
            boolean responded = c.getReActivityStatus() == ReActivityStatus.RESPONSE_SUBMITTED;
            LocalDateTime forwardedAt = now.minusDays(6);
            reTrackerRepository.save(ReResponseTracker.builder()
                    .complaintId(c.getId())
                    .regulatedEntityId(entity.getId())
                    .forwardedAt(forwardedAt)
                    .respondedAt(responded ? now.minusDays(1) : null)
                    .windowDays(30)
                    .windowExpiresAt(forwardedAt.plusDays(30))
                    .breached(false)
                    .exParteEligible(false)
                    .notes("13(1) notice issued to the nodal officer.")
                    .responseText(responded
                            ? "The disputed interest has been recomputed and the difference credited."
                            : null)
                    .createdAt(forwardedAt)
                    .updatedAt(responded ? now.minusDays(1) : forwardedAt)
                    .build());
        }
    }

    /**
     * Officer notes on complaints, as threaded {@code PUBLIC} staff comments.
     *
     * <p>The pre-threaded version of this seeder also attached two of these to the nodal-officer
     * record (#5) via a {@code noRecordNumber}/{@code target} discriminator on a flat note table.
     * {@code ComplaintComment} is now keyed by {@code complaintId} alone with no such concept, and
     * there is no replacement nodal-record comment thread, so those two notes are seeded as ordinary
     * complaint-level comments instead of being dropped — they are still real seed content, just no
     * longer addressable from the nodal-record tab.
     */
    private void seedComments(List<Complaint> complaints, LocalDateTime now) {
        for (int seq : new int[]{1, 4, 8}) {
            Complaint c = complaints.get(seq - 1);
            comment(c, "CEPC DO User 1", ME, "CEPC_DO",
                    "Entity reply received and placed on record. The reversal offered falls short of the "
                            + "disputed amount.", now.minusDays(5));
            comment(c, "CEPC REVIWER User 1", OTHER, "CEPC_REVIEWER",
                    "Examined. Please obtain the transaction log before the next step.",
                    now.minusDays(3));
        }

        Complaint five = complaints.get(4);
        comment(five, "CEPC DO User 1", ME, "CEPC_DO",
                "Nodal officer contacted by telephone; confirmed the notice was received.",
                now.minusDays(4));
        comment(five, "CEPC DO User 1", ME, "CEPC_DO",
                "Principal Nodal Officer copied in, the branch has not responded to the NO.",
                now.minusDays(2));
    }

    private void comment(Complaint c, String authorName, String authorUserId, String authorRole,
                         String body, LocalDateTime at) {
        commentRepository.save(ComplaintComment.builder()
                .complaintId(c.getId())
                .body(body)
                .visibility(ComplaintComment.VISIBILITY_PUBLIC)
                .authorUserId(authorUserId)
                .authorName(authorName)
                .authorRole(authorRole)
                .createdAt(at)
                .editCount(0)
                .build());
    }

    /**
     * The Email Communication tab, covering every bucket its renderer branches on.
     *
     * <p>All four statuses appear because each drives a different control: {@code DRAFT} is the only one
     * that is {@code editable}, {@code SENT} and any inbound message are {@code canReply}, and
     * {@code FAILED} is the only one that is {@code canRetry} — so a seed without a failed row leaves the
     * Retry button untestable. The failed row carries a real {@code lastError} for the same reason: the
     * button is meaningless without a stated cause.
     *
     * <p>Recipients are the seeded nodal addresses, which are now reachable because
     * {@link #seedAllowedEmailDomains()} admits their domains.
     */
    private void seedEmails(List<Complaint> complaints, List<Bank> banks, LocalDateTime now) {
        Complaint one = complaints.get(0);
        String bankOne = slug(entityName(banks, 1));
        String threadOne = "THREAD-" + one.getComplaintNumber();
        SimulatedEmail sent = email(one, threadOne, 1, "cepc.blr@rbi.org.in",
                "nodal1@" + bankOne + ".example.in", "13(1) notice - " + entityName(banks, 1),
                "You are requested to furnish your comments on the attached complaint within 30 days.",
                ComplaintEmailService.DIRECTION_OUTBOUND, SimulatedEmail.STATUS_SENT, now.minusDays(6));
        sent.setSentAt(now.minusDays(6));
        emailRepository.save(sent);

        SimulatedEmail reply = email(one, threadOne, 2, "nodal1@" + bankOne + ".example.in",
                "cepc.blr@rbi.org.in", "Re: 13(1) notice - " + entityName(banks, 1),
                "Our branch has examined the transaction and a partial reversal has been credited.",
                ComplaintEmailService.DIRECTION_INBOUND, SimulatedEmail.STATUS_SENT, now.minusDays(3));
        reply.setInReplyToId(sent.getId());
        reply.setReceivedAt(now.minusDays(3));
        emailRepository.save(reply);

        SimulatedEmail failed = email(one, threadOne, 3, "cepc.blr@rbi.org.in",
                "nodal.grievances@unlisted-entity.example.org", "Reminder - 13(1) notice",
                "A reminder in respect of the notice issued on the captioned complaint.",
                ComplaintEmailService.DIRECTION_OUTBOUND, SimulatedEmail.STATUS_FAILED, now.minusDays(2));
        failed.setLastError("Recipient domain unlisted-entity.example.org is not in the outbound "
                + "allowlist (email.outbound.allowed_domains).");
        failed.setRetryCount(1);
        emailRepository.save(failed);

        emailRepository.save(email(one, threadOne, 4, "cepc.blr@rbi.org.in",
                "nodal1@" + bankOne + ".example.in", "Advisory - closure of the captioned complaint",
                "Draft advisory, pending approval of the closing authority.",
                ComplaintEmailService.DIRECTION_OUTBOUND, SimulatedEmail.STATUS_DRAFT, now.minusDays(1)));

        // #6 is the complaint whose entity HAS answered, so its thread is what the Response from RE tab
        // leads to. An inbound message with no outbound original would be a thread with no beginning.
        Complaint six = complaints.get(5);
        String bankSix = slug(entityName(banks, 6));
        String threadSix = "THREAD-" + six.getComplaintNumber();
        SimulatedEmail sentSix = email(six, threadSix, 1, "cepc.blr@rbi.org.in",
                "nodal6@" + bankSix + ".example.in", "13(1) notice - " + entityName(banks, 6),
                "Please furnish your comments on the recurring deposit interest computation.",
                ComplaintEmailService.DIRECTION_OUTBOUND, SimulatedEmail.STATUS_SENT, now.minusDays(6));
        sentSix.setSentAt(now.minusDays(6));
        emailRepository.save(sentSix);

        SimulatedEmail replySix = email(six, threadSix, 2, "nodal6@" + bankSix + ".example.in",
                "cepc.blr@rbi.org.in", "Re: 13(1) notice - " + entityName(banks, 6),
                "The rate has been recomputed and the difference credited to the customer's account.",
                ComplaintEmailService.DIRECTION_INBOUND, SimulatedEmail.STATUS_SENT, now.minusDays(1));
        replySix.setInReplyToId(sentSix.getId());
        replySix.setReceivedAt(now.minusDays(1));
        emailRepository.save(replySix);

        // A pending row, so the fourth status is represented too: queued but not yet dispatched.
        Complaint eight = complaints.get(7);
        emailRepository.save(email(eight, "THREAD-" + eight.getComplaintNumber(), 1,
                "cepc.blr@rbi.org.in", "nodal8@" + slug(entityName(banks, 8)) + ".example.in",
                "Request for the policy document and the benefit illustration",
                "Please forward the signed benefit illustration placed before the complainant.",
                ComplaintEmailService.DIRECTION_OUTBOUND, SimulatedEmail.STATUS_PENDING, now.minusHours(4)));
    }

    private SimulatedEmail email(Complaint c, String threadId, int n, String from, String to,
                                 String subject, String body, String direction, String status,
                                 LocalDateTime at) {
        return SimulatedEmail.builder()
                .messageId(threadId + "-" + n)
                .threadId(threadId)
                .fromEmail(from)
                .toEmail(to)
                .subject(subject)
                .body(body)
                .direction(direction)
                .status(status)
                .complaintId(c.getId())
                .complaintNumber(c.getComplaintNumber())
                .assignedTo(c.getAssignedOfficer())
                .updatedAt(at)
                .build();
    }

    /**
     * Three to five history rows per complaint, ending where {@link #SPECS} leaves it.
     *
     * <p>The table was empty, so the history panel on every complaint was blank. The rows are backdated
     * between {@code filedAt} and now, which works because {@code ComplaintTimeline}'s {@code @PrePersist}
     * assigns {@code performedAt} only when it is still null — an unconditional one would flatten the whole
     * history to a single instant and make the ordering untestable.
     *
     * <p>The last row's {@code toStatus} is the complaint's CURRENT status, so the panel agrees with the
     * grid. A history that ends somewhere else is worse than no history: it reads as a lost transition.
     */
    private void seedTimeline(List<Complaint> complaints, LocalDateTime now) {
        for (int i = 0; i < complaints.size(); i++) {
            Complaint c = complaints.get(i);
            Spec s = SPECS.get(i);
            LocalDateTime filed = c.getFiledAt();

            timeline(c, "CREATED", null, CepcStatus.NEW_COMPLAINT, filed, "system", null,
                    "Complaint received through " + s.filingType() + " and registered.");

            // A draft was never actually assigned or examined, so it gets no further history.
            if (CepcStatus.DRAFT.equals(s.status())) {
                continue;
            }

            timeline(c, "ASSIGNED", CepcStatus.NEW_COMPLAINT, CepcStatus.NEW_COMPLAINT, filed.plusDays(1),
                    s.officer(), "CEPC_DO", "Assigned to the dealing officer for examination.");

            if ("RE".equals(s.role())) {
                timeline(c, "FORWARD_TO_RE", CepcStatus.NEW_COMPLAINT, CepcStatus.NEW_COMPLAINT, filed.plusDays(2),
                        s.officer(), "CEPC_DO", "13(1) notice issued to the nodal officer of the entity.");
                if (c.getReActivityStatus() == ReActivityStatus.RESPONSE_SUBMITTED) {
                    timeline(c, "RE_RESPONSE_RECEIVED", CepcStatus.NEW_COMPLAINT, CepcStatus.NEW_COMPLAINT,
                            now.minusDays(1), "entity.nodal", "RE", "Entity submitted its response.");
                }
            }

            // The closing row, only where the complaint has actually moved past intake. Comparing on the
            // spec's own status keeps this in step with SPECS instead of restating it.
            if (!CepcStatus.NEW_COMPLAINT.equals(s.status())) {
                timeline(c, closingAction(s.status()), CepcStatus.NEW_COMPLAINT, s.status(), now.minusDays(1),
                        s.officer(), s.role(), "Recorded as " + CepcStatus.label(s.status()) + ".");
            }
        }
    }

    private static String closingAction(String status) {
        return switch (status) {
            case CepcStatus.COMPLAINT_CLOSED -> "CLOSE_COMPLAINT";
            case CepcStatus.COMPLAINT_WITHDRAWN -> "WITHDRAW";
            case CepcStatus.COMPLAINT_REJECTED -> "REJECT";
            case CepcStatus.SENT_BACK_TO_DO -> "SEND_BACK_DO";
            case CepcStatus.SENT_BACK_TO_REVIEWER -> "SEND_BACK_REVIEWER";
            case CepcStatus.SENT_BACK_TO_INCHARGE -> "SEND_BACK_INCHARGE";
            case CepcStatus.SENT_TO_REVIEWER -> "SUBMIT_FOR_REVIEW";
            case CepcStatus.SENT_TO_INCHARGE -> "FORWARD_TO_INCHARGE";
            case CepcStatus.PENDING_OFFICE_HEAD_APPROVAL -> "FORWARD_TO_INCHARGE";
            case CepcStatus.SENT_TO_CLOSING_AUTHORITY -> "FORWARD_TO_CLOSING_AUTHORITY";
            case CepcStatus.COMPLAINT_SETTLED -> "FORWARD_TO_CLOSING_AUTHORITY";
            case CepcStatus.ADVISORY_COMPLIED -> "ADVISORY_COMPLIED";
            case CepcStatus.INFORMATION_REQUIRED -> "REQUEST_INFORMATION";
            case CepcStatus.SENT_TO_OTHER_DEPARTMENTS -> "FORWARD_TO_CONTACT_PERSON";
            case CepcStatus.SENT_TO_OTHER_REGULATED_BODIES -> "FORWARD_TO_REGULATORY_BODY";
            case CepcStatus.SENT_TO_OTHER_OFFICE -> "FORWARD_TO_OTHER_OFFICE";
            case CepcStatus.COMPLAINT_REOPEN -> "REOPEN";
            case CepcStatus.MEETING_SCHEDULED -> "SCHEDULE_MEETING";
            case CepcStatus.SENT_TO_RBI -> "CONTACT_PERSON_RESPONSE";
            default -> "STATUS_CHANGED";
        };
    }

    private void timeline(Complaint c, String action, String from, String to, LocalDateTime at,
                          String by, String role, String remarks) {
        timelineRepository.save(ComplaintTimeline.builder()
                .complaintId(c.getId())
                .action(action)
                .fromStatus(from)
                .toStatus(to)
                .performedAt(at)
                .performedBy(by)
                .performedByRole(role)
                .remarks(remarks)
                .eventSource(TimelineEventSource.AUTOMATIC)
                .build());
    }

    /**
     * An entity name that is never null.
     *
     * <p>Falls back to a literal when the bank master is empty, because the grid's Entity column is one of
     * the four the enrichment is meant to prove it fills — a blank there looks like broken enrichment
     * rather than a missing master table.
     */
    private String entityName(List<Bank> banks, int seq) {
        Bank b = pick(banks, seq);
        return b == null ? "Dev Bank " + ((seq % 3) + 1) : b.getName();
    }

    private static <T> T pick(List<T> from, int seq) {
        return from.isEmpty() ? null : from.get(seq % from.size());
    }
}
