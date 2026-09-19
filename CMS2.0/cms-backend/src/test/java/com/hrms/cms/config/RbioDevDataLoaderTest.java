package com.hrms.cms.config;

import com.hrms.cms.entity.*;
import com.hrms.cms.repository.*;
import com.hrms.cms.service.NodalOfficerRecordService;
import com.hrms.cms.service.RbioCompensationService;
import com.hrms.cms.service.triage.ReResponsivenessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Runs the loader against a real schema rather than mocks. The point of the exercise is the inserts
 * themselves: the loader writes to thirteen tables whose NOT NULL and length constraints are only
 * enforced by the database, and dev-local points at MySQL, which is not available in CI. H2 with
 * ddl-auto=create-drop generates its DDL from the same entities, so a constraint the loader violates
 * fails here instead of on a developer's first boot.
 */
@DataJpaTest
@DisplayName("RbioDevDataLoader")
class RbioDevDataLoaderTest {

    private static final String OFFICER = "rbio_mum1";
    private static final String REGIONAL_OFFICE = "Mumbai-I";

    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private ComplaintRbioFormDataRepository formDataRepository;
    @Autowired private ComplaintEligibilityAnswerRepository eligibilityRepository;
    @Autowired private ComplaintAdditionalDetailRepository additionalDetailRepository;
    @Autowired private ConciliationMeetingRepository conciliationMeetingRepository;
    @Autowired private ComplaintCommentRepository commentRepository;
    @Autowired private ComplaintTimelineRepository timelineRepository;
    @Autowired private ReResponseTrackerRepository trackerRepository;
    @Autowired private SimulatedEmailRepository simulatedEmailRepository;
    @Autowired private RegulatedEntityRepository regulatedEntityRepository;
    @Autowired private ComplaintCategoryRepository categoryRepository;
    @Autowired private NodalOfficerRecordRepository nodalOfficerRecordRepository;

    private RbioDevDataLoader loader;

    @BeforeEach
    void setUp() {
        // DataInitializer does not run under @DataJpaTest, so the reference data the loader depends on
        // is created here. Two entities rather than one so the round-robin across entities is
        // actually exercised.
        categoryRepository.save(ComplaintCategory.builder()
                .name("Digital transactions").status("ACTIVE").build());
        regulatedEntityRepository.save(RegulatedEntity.builder()
                .name("State Bank of India").department("RBIO").entityType("Public Sector Bank")
                .portalEnabled(true).build());
        regulatedEntityRepository.save(RegulatedEntity.builder()
                .name("HDFC Bank Limited").department("RBIO").entityType("Private Sector Bank")
                .portalEnabled(true).build());
        // Must be ignored by the loader: it only seeds RBIO complaints.
        regulatedEntityRepository.save(RegulatedEntity.builder()
                .name("Bajaj Finance Limited").department("CEPC").entityType("NBFC")
                .portalEnabled(true).build());

        NodalOfficerRecordService nodalOfficerRecordService = new NodalOfficerRecordService(
                nodalOfficerRecordRepository,
                regulatedEntityRepository,
                complaintRepository,
                formDataRepository,
                trackerRepository,
                new RbioCompensationService(),
                mock(ReResponsivenessService.class));

        loader = new RbioDevDataLoader(
                complaintRepository,
                formDataRepository,
                eligibilityRepository,
                additionalDetailRepository,
                conciliationMeetingRepository,
                commentRepository,
                timelineRepository,
                trackerRepository,
                simulatedEmailRepository,
                regulatedEntityRepository,
                categoryRepository,
                nodalOfficerRecordService,
                nodalOfficerRecordRepository);

        ReflectionTestUtils.setField(loader, "officer", OFFICER);
        ReflectionTestUtils.setField(loader, "regionalOffice", REGIONAL_OFFICE);
    }

    @Nested
    @DisplayName("seeding")
    class Seeding {

        @BeforeEach
        void seed() {
            loader.run();
        }

        @Test
        @DisplayName("writes one complaint per dashboard tab, scoped to the officer's own office")
        void writesOneComplaintPerDashboardTab() {
            List<Complaint> seeded = complaintRepository.findAll();
            assertThat(seeded).hasSize(7);
            assertThat(seeded).allSatisfy(c -> {
                assertThat(c.getComplaintNumber()).startsWith("N2526DEV");
                assertThat(c.getDepartment()).isEqualTo("RBIO");
                assertThat(c.getAssignedOfficer()).isEqualTo(OFFICER);
                // Without this the dashboard's tenancy filter excludes the row outright.
                assertThat(c.getRegionalOffice()).isEqualTo(REGIONAL_OFFICE);
            });

            assertThat(seeded).extracting(Complaint::getStatus)
                    .containsExactlyInAnyOrder("NEW_COMPLAINT", "DRAFT", "MEETING_SCHEDULED",
                            "SENT_BACK_TO_DO", "INFORMATION_REQUIRED", "SENT_TO_RBI",
                            "COMPLAINT_WITHDRAWN");
        }

        @Test
        @DisplayName("gives every complaint the three child rows the details screen reads")
        void givesEveryComplaintItsChildRows() {
            for (Complaint complaint : complaintRepository.findAll()) {
                Long id = complaint.getId();
                assertThat(formDataRepository.findByComplaintId(id))
                        .as("form data for %s", complaint.getComplaintNumber()).isPresent();
                assertThat(eligibilityRepository.findByComplaintId(id))
                        .as("eligibility answers for %s", complaint.getComplaintNumber()).isPresent();
                assertThat(additionalDetailRepository.findByComplaintId(id))
                        .as("additional details for %s", complaint.getComplaintNumber()).isPresent();
            }
        }

        @Test
        @DisplayName("gives every complaint a timeline, comments and an email thread")
        void givesEveryComplaintItsNarrativeRows() {
            for (Complaint complaint : complaintRepository.findAll()) {
                assertThat(timelineRepository.findByComplaintIdOrderByPerformedAtDesc(complaint.getId()))
                        .as("timeline for %s", complaint.getComplaintNumber()).hasSize(3);
                assertThat(commentRepository.findByComplaintNumberOrderByCreatedAtDesc(
                        complaint.getComplaintNumber()))
                        .as("comments for %s", complaint.getComplaintNumber()).isNotEmpty();
                assertThat(simulatedEmailRepository.findByComplaintNumberOrderBySentAtAsc(
                        complaint.getComplaintNumber()))
                        .as("emails for %s", complaint.getComplaintNumber()).hasSize(3);
            }
        }

        @Test
        @DisplayName("shares one complainant so the past-complaints panel is never empty")
        void sharesOneComplainantAcrossComplaints() {
            // The panel looks the complainant up by email, so a distinct complainant per complaint
            // would leave it empty on every one of them.
            assertThat(complaintRepository.findAll()).extracting(Complaint::getComplainantEmail)
                    .containsOnly("dev.complainant@example.com");
        }

        @Test
        @DisplayName("names the nodal and principal nodal officer on every record")
        void namesTheNodalOfficerOnEveryRecord() {
            List<NodalOfficerRecord> records = nodalOfficerRecordRepository.findAll();
            assertThat(records).hasSize(7);
            assertThat(records).allSatisfy(r -> {
                assertThat(r.getRecordNumber()).isNotBlank();
                // The whole reason the loader backfills the regulated entities: these are snapshotted
                // from the entity, so without that backfill the nodal officer tab shows blank names.
                assertThat(r.getNodalOfficerName()).isNotBlank();
                assertThat(r.getNodalOfficerName()).doesNotContain("null");
                assertThat(r.getEmail()).isNotBlank();
                assertThat(r.getPnoName()).isNotBlank();
                assertThat(r.getPnoEmail()).isNotBlank();
                assertThat(r.getDesignation()).isNotBlank();
            });
        }

        @Test
        @DisplayName("persists the assessment on the records that were forwarded to the entity")
        void persistsTheAssessmentOnForwardedRecords() {
            List<NodalOfficerRecord> assessed = nodalOfficerRecordRepository.findAll().stream()
                    .filter(r -> r.getForwardedToReAt() != null)
                    .toList();

            assertThat(assessed).hasSize(2);
            assertThat(assessed).allSatisfy(r -> {
                assertThat(r.getStatus()).isEqualTo("ADVISORY_ISSUED");
                assertThat(r.getAdvisoryComplianceDate()).isNotNull();
                assertThat(r.getDisputeAmount()).isNotNull();
                assertThat(r.getCompensationLoss()).isNotNull();
                assertThat(r.getCompensationMental()).isNotNull();
                assertThat(r.getNotice131ComplyDate()).isNotNull();
            });
        }

        @Test
        @DisplayName("keeps the seeded compensation inside the Ombudsman Scheme caps")
        void keepsCompensationInsideTheSchemeCaps() {
            // The real endpoint rejects anything above these, so seed data that breaches them would
            // be data the application itself would refuse to accept.
            RbioCompensationService caps = new RbioCompensationService();
            nodalOfficerRecordRepository.findAll().stream()
                    .filter(r -> r.getCompensationLoss() != null)
                    .forEach(r -> {
                        caps.validateAward(r.getCompensationLoss(), "CONSEQUENTIAL_LOSS");
                        caps.validateAward(r.getCompensationMental(), "TIME_HARASSMENT");
                        caps.validateAward(r.getCompensationLoss().add(r.getCompensationMental()),
                                "COMBINED");
                    });
        }

        @Test
        @DisplayName("opens an RE response window for the forwarded complaints only")
        void opensAnReResponseWindowForForwardedComplaintsOnly() {
            List<ReResponseTracker> trackers = trackerRepository.findAll();
            assertThat(trackers).hasSize(2);
            // One window still open and one already answered, so both halves of the RE
            // responsiveness display have a row behind them.
            assertThat(trackers).filteredOn(t -> t.getRespondedAt() != null).hasSize(1);
            assertThat(trackers).allSatisfy(t -> {
                assertThat(t.getWindowDays()).isEqualTo(30);
                assertThat(t.getWindowExpiresAt()).isNotNull();
            });
        }

        @Test
        @DisplayName("schedules a conciliation meeting for the maintainable complaints")
        void schedulesConciliationMeetings() {
            List<ConciliationMeeting> meetings = conciliationMeetingRepository.findAll();
            assertThat(meetings).hasSize(5);
            assertThat(meetings).extracting(ConciliationMeeting::getMeetingStatus)
                    .contains("SCHEDULED", "COMPLETED");
        }

        @Test
        @DisplayName("comments the nodal record against both the NO and the PNO")
        void commentsAgainstBothNodalTargets() {
            List<ComplaintComment> nodalComments = commentRepository.findAll().stream()
                    .filter(c -> c.getNoRecordNumber() != null)
                    .toList();

            assertThat(nodalComments).hasSize(14);
            assertThat(nodalComments).extracting(ComplaintComment::getTarget)
                    .containsOnly("NO", "PNO");
        }

        @Test
        @DisplayName("leaves regulated entities in other departments alone")
        void leavesOtherDepartmentsAlone() {
            RegulatedEntity cepc = regulatedEntityRepository.findAll().stream()
                    .filter(e -> "CEPC".equals(e.getDepartment()))
                    .findFirst().orElseThrow();

            assertThat(cepc.getNodalOfficerName()).isNull();
            assertThat(cepc.getPnoEmail()).isNull();
        }

        @Test
        @DisplayName("is a no-op on the second run so a restart does not duplicate the data")
        void isANoOpOnTheSecondRun() {
            long complaints = complaintRepository.count();
            long comments = commentRepository.count();
            long records = nodalOfficerRecordRepository.count();

            loader.run();

            assertThat(complaintRepository.count()).isEqualTo(complaints);
            assertThat(commentRepository.count()).isEqualTo(comments);
            assertThat(nodalOfficerRecordRepository.count()).isEqualTo(records);
        }
    }

    @Test
    @DisplayName("does not seed complaints with no entity to point at")
    void doesNotSeedWithoutRegulatedEntities() {
        // An RBIO complaint with no regulated entity produces a nodal officer record with no contact
        // details, which is the blank tab this loader exists to avoid. Better to seed nothing and say
        // why than to seed something unusable.
        regulatedEntityRepository.deleteAll();

        loader.run();

        assertThat(complaintRepository.count()).isZero();
    }
}
