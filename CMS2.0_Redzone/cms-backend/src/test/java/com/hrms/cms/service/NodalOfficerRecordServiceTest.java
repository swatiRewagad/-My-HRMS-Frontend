package com.hrms.cms.service;

import com.hrms.cms.dto.CreateNodalOfficerRecordRequest;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.NodalOfficerRecordStatus;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NodalOfficerRecordService} — UST569 auto-creation, UST570/574 status defaulting,
 * and UST572-575 server-side mandatory-field enforcement.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodalOfficerRecordServiceTest {

    private static final String COMPLAINT_NO = "N2026270130000012";
    private static final String SBI = "State Bank of India";
    private static final String MUMBAI = "Mumbai I";

    @Mock private NodalOfficerRecordRepository recordRepository;
    @Mock private NodalOfficerResolver resolver;
    @Mock private ComplaintTimelineRepository timelineRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private NodalOfficerRecordService service;

    private void resolverReturns(NodalOfficerResolver.Resolution resolution) {
        when(resolver.resolve(any(), any())).thenReturn(resolution);
    }

    private NodalOfficerResolver.Resolution contactsFound() {
        return NodalOfficerResolver.Resolution.builder()
                .nodalOfficerName("Mumbai Nodal Officer")
                .designation("Deputy General Manager")
                .email("no.mumbai@sbi.example")
                .phone("9876543210")
                .pnoName("National PNO")
                .assignedTo("no.mumbai@sbi.example")
                .source(NodalOfficerResolver.Source.ENTITY_OFFICE)
                .processingOffice(MUMBAI)
                .build();
    }

    private NodalOfficerResolver.Resolution adminFallback() {
        return NodalOfficerResolver.Resolution.builder()
                .assignedTo("RBIO-MUM_ADMIN")
                .source(NodalOfficerResolver.Source.OMBUDSMAN_ADMIN)
                .processingOffice(MUMBAI)
                .build();
    }

    /** Mirrors the DB assigning an id on insert. */
    private void saveEchoesBack() {
        when(recordRepository.saveAndFlush(any(NodalOfficerRecord.class))).thenAnswer(inv -> {
            NodalOfficerRecord r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });
    }

    private CreateNodalOfficerRecordRequest validRequest() {
        return CreateNodalOfficerRecordRequest.builder()
                .complaintId(7L)
                .complaintNumber(COMPLAINT_NO)
                .entityName(SBI)
                .nodalOfficerName("Hand Entered NO")
                .designation("Chief Manager")
                .email("hand.no@sbi.example")
                .phone("9876501234")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // UST569: auto-creation on complaint registration
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UST569 — ensureRecordExists()")
    class AutoCreation {

        @Test
        @DisplayName("creates a record when the entity has none for this complaint")
        void createsRecordWhenAbsent() {
            when(recordRepository.findFirstByComplaintNumber(COMPLAINT_NO)).thenReturn(Optional.empty());
            resolverReturns(contactsFound());
            saveEchoesBack();

            NodalOfficerRecord result = service.ensureRecordExists(7L, COMPLAINT_NO, SBI, MUMBAI);

            ArgumentCaptor<NodalOfficerRecord> saved = ArgumentCaptor.forClass(NodalOfficerRecord.class);
            verify(recordRepository).saveAndFlush(saved.capture());

            assertThat(result).isNotNull();
            assertThat(saved.getValue().getComplaintNumber()).isEqualTo(COMPLAINT_NO);
            assertThat(saved.getValue().getEntityName()).isEqualTo(SBI);
            assertThat(saved.getValue().getProcessingOffice())
                    .as("UST773: without the office on the record there is no way to tell which office's "
                        + "mapping produced these contacts")
                    .isEqualTo(MUMBAI);
        }

        @Test
        @DisplayName("does NOT create a second record when one already exists")
        void doesNotDuplicateExistingRecord() {
            NodalOfficerRecord existing = NodalOfficerRecord.builder()
                    .id(1L).complaintNumber(COMPLAINT_NO).entityName(SBI)
                    .status(NodalOfficerRecordStatus.CONFIRMED)
                    .nodalOfficerName("Officer Corrected By Hand")
                    .build();
            when(recordRepository.findFirstByComplaintNumber(COMPLAINT_NO)).thenReturn(Optional.of(existing));

            NodalOfficerRecord result = service.ensureRecordExists(7L, COMPLAINT_NO, SBI, MUMBAI);

            assertThat(result).isSameAs(existing);
            verify(recordRepository, never()).saveAndFlush(any());
            verify(recordRepository, never()).save(any());

            assertThat(result.getStatus())
                    .as("re-deriving on every call would overwrite a Dealing Officer's correction and bump "
                        + "lastModifiedAt, resetting the staleness clock so the escalation never fires")
                    .isEqualTo(NodalOfficerRecordStatus.CONFIRMED);
            assertThat(result.getNodalOfficerName()).isEqualTo("Officer Corrected By Hand");

            verify(resolver, never()).resolve(any(), any());
            verifyNoInteractions(notificationService);
            verifyNoInteractions(timelineRepository);
        }

        @Test
        @DisplayName("a lost create race is treated as success, not as a failed complaint registration")
        void concurrentCreationIsTreatedAsSuccess() {
            NodalOfficerRecord winner = NodalOfficerRecord.builder()
                    .id(2L).complaintNumber(COMPLAINT_NO).build();
            when(recordRepository.findFirstByComplaintNumber(COMPLAINT_NO))
                    .thenReturn(Optional.empty())      // pre-check: nothing there yet
                    .thenReturn(Optional.of(winner));  // post-violation: the racing insert's row
            resolverReturns(contactsFound());
            when(recordRepository.saveAndFlush(any(NodalOfficerRecord.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_no_complaint"));

            NodalOfficerRecord result = service.ensureRecordExists(7L, COMPLAINT_NO, SBI, MUMBAI);

            assertThat(result)
                    .as("the caller asked for a record to exist and one does; rethrowing would fail a valid "
                        + "complaint registration over a duplicate we never wanted")
                    .isSameAs(winner);
        }

        @Test
        @DisplayName("a blank complaint number is skipped rather than inserted as an unfindable orphan")
        void blankComplaintNumberIsSkipped() {
            assertThat(service.ensureRecordExists(7L, "   ", SBI, MUMBAI)).isNull();
            assertThat(service.ensureRecordExists(7L, null, SBI, MUMBAI)).isNull();
            verify(recordRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("writes a timeline entry against the complaint (UST569 logs on BOTH sides)")
        void writesComplaintTimelineEntry() {
            when(recordRepository.findFirstByComplaintNumber(COMPLAINT_NO)).thenReturn(Optional.empty());
            resolverReturns(contactsFound());
            saveEchoesBack();

            service.ensureRecordExists(7L, COMPLAINT_NO, SBI, MUMBAI);

            ArgumentCaptor<ComplaintTimeline> entry = ArgumentCaptor.forClass(ComplaintTimeline.class);
            verify(timelineRepository).save(entry.capture());

            assertThat(entry.getValue().getComplaintId()).isEqualTo(7L);
            assertThat(entry.getValue().getAction()).isEqualTo("no_record_created");
            assertThat(entry.getValue().getRemarks())
                    .as("the office belongs in the history; otherwise an officer cannot tell which office's "
                        + "mapping was used")
                    .contains(SBI)
                    .contains(MUMBAI);
        }

        @Test
        @DisplayName("notifies the admin specifically when the entity has no contact at all (UST571)")
        void notifiesAdminOnNoContactFallback() {
            when(recordRepository.findFirstByComplaintNumber(COMPLAINT_NO)).thenReturn(Optional.empty());
            resolverReturns(adminFallback());
            saveEchoesBack();

            service.ensureRecordExists(7L, COMPLAINT_NO, SBI, MUMBAI);

            verify(notificationService).send(eq("RBIO_ADMIN"), eq("NO_RECORD_NO_CONTACT"), any(), any(),
                    eq(COMPLAINT_NO), eq("NO_RECORD"), any());
            // The supervisor notification still fires: the record exists and is unconfirmed either way.
            verify(notificationService).send(eq("RBIO_SUPERVISOR"), eq("NO_RECORD_CREATED"), any(), any(),
                    eq(COMPLAINT_NO), eq("NO_RECORD"), any());
        }

        @Test
        @DisplayName("does not raise the no-contact alert when contacts were found")
        void noAdminAlertWhenContactsResolved() {
            when(recordRepository.findFirstByComplaintNumber(COMPLAINT_NO)).thenReturn(Optional.empty());
            resolverReturns(contactsFound());
            saveEchoesBack();

            service.ensureRecordExists(7L, COMPLAINT_NO, SBI, MUMBAI);

            verify(notificationService, never()).send(any(), eq("NO_RECORD_NO_CONTACT"), any(), any(),
                    any(), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UST570 / UST574: status defaulting and contact copying
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UST570/574 — INFORMATION_REQUIRED default and contact copying")
    class StatusAndContactCopying {

        @Test
        @DisplayName("an auto-created record defaults to INFORMATION_REQUIRED")
        void autoCreatedRecordDefaultsToInformationRequired() {
            when(recordRepository.findFirstByComplaintNumber(COMPLAINT_NO)).thenReturn(Optional.empty());
            resolverReturns(contactsFound());
            saveEchoesBack();

            service.ensureRecordExists(7L, COMPLAINT_NO, SBI, MUMBAI);

            ArgumentCaptor<NodalOfficerRecord> saved = ArgumentCaptor.forClass(NodalOfficerRecord.class);
            verify(recordRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo("INFORMATION_REQUIRED");
        }

        @Test
        @DisplayName("contacts are copied from the resolver, yet the status stays INFORMATION_REQUIRED")
        void copiesContactsButKeepsInformationRequired() {
            when(recordRepository.findFirstByComplaintNumber(COMPLAINT_NO)).thenReturn(Optional.empty());
            resolverReturns(contactsFound());
            saveEchoesBack();

            service.ensureRecordExists(7L, COMPLAINT_NO, SBI, MUMBAI);

            ArgumentCaptor<NodalOfficerRecord> saved = ArgumentCaptor.forClass(NodalOfficerRecord.class);
            verify(recordRepository).saveAndFlush(saved.capture());
            NodalOfficerRecord r = saved.getValue();

            // Copying: leaving these blank when master data holds them forces a manual lookup.
            assertThat(r.getNodalOfficerName()).isEqualTo("Mumbai Nodal Officer");
            assertThat(r.getDesignation()).isEqualTo("Deputy General Manager");
            assertThat(r.getEmail()).isEqualTo("no.mumbai@sbi.example");
            assertThat(r.getPhone()).isEqualTo("9876543210");
            assertThat(r.getPnoName()).isEqualTo("National PNO");
            assertThat(r.getAssignedTo()).isEqualTo("no.mumbai@sbi.example");

            assertThat(r.getStatus())
                    .as("populated fields are not a confirmation: master data can be years stale, and "
                        + "marking it CONFIRMED would exclude the record from the staleness sweep so the "
                        + "entity is never asked to verify it")
                    .isEqualTo(NodalOfficerRecordStatus.INFORMATION_REQUIRED);
        }

        @Test
        @DisplayName("the admin fallback still produces an INFORMATION_REQUIRED record, not a blank one")
        void adminFallbackStillCreatesInformationRequiredRecord() {
            when(recordRepository.findFirstByComplaintNumber(COMPLAINT_NO)).thenReturn(Optional.empty());
            resolverReturns(adminFallback());
            saveEchoesBack();

            service.ensureRecordExists(7L, COMPLAINT_NO, SBI, MUMBAI);

            ArgumentCaptor<NodalOfficerRecord> saved = ArgumentCaptor.forClass(NodalOfficerRecord.class);
            verify(recordRepository).saveAndFlush(saved.capture());

            assertThat(saved.getValue().getStatus()).isEqualTo(NodalOfficerRecordStatus.INFORMATION_REQUIRED);
            assertThat(saved.getValue().getAssignedTo()).isEqualTo("RBIO-MUM_ADMIN");
            assertThat(saved.getValue().getNodalOfficerName()).isNull();
        }

        @Test
        @DisplayName("a hand-added record also defaults to INFORMATION_REQUIRED")
        void manuallyAddedRecordDefaultsToInformationRequired() {
            when(recordRepository.existsByComplaintNumber(COMPLAINT_NO)).thenReturn(false);
            saveEchoesBack();

            service.addRecord(validRequest(), "cepc_do1");

            ArgumentCaptor<NodalOfficerRecord> saved = ArgumentCaptor.forClass(NodalOfficerRecord.class);
            verify(recordRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getStatus())
                    .as("the DO is recording what they were told, which is not the entity confirming it")
                    .isEqualTo(NodalOfficerRecordStatus.INFORMATION_REQUIRED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UST572-575: server-side mandatory fields
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UST572-575 — addRecord() server-side validation")
    class ManualAddValidation {

        @ParameterizedTest(name = "rejects a missing {0}")
        @CsvSource({
                "entityName",
                "nodalOfficerName",
                "designation",
                "email",
                "phone"
        })
        @DisplayName("each mandatory field is rejected by the SERVER, not just by the form")
        void rejectsEachMissingMandatoryField(String field) {
            CreateNodalOfficerRecordRequest request = validRequest();
            switch (field) {
                case "entityName" -> request.setEntityName(null);
                case "nodalOfficerName" -> request.setNodalOfficerName(null);
                case "designation" -> request.setDesignation(null);
                case "email" -> request.setEmail(null);
                case "phone" -> request.setPhone(null);
                default -> throw new IllegalArgumentException(field);
            }

            assertThatThrownBy(() -> service.addRecord(request, "cepc_do1"))
                    .as("a browser-only required marker is not a control: anything with a session can POST "
                        + "past it, so the server must refuse")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(field);

            verify(recordRepository, never()).saveAndFlush(any());
        }

        @ParameterizedTest(name = "treats whitespace-only {0} as missing")
        @CsvSource({
                "entityName",
                "nodalOfficerName",
                "designation",
                "email",
                "phone"
        })
        @DisplayName("whitespace does not satisfy a mandatory field")
        void whitespaceDoesNotSatisfyMandatoryField(String field) {
            CreateNodalOfficerRecordRequest request = validRequest();
            switch (field) {
                case "entityName" -> request.setEntityName("   ");
                case "nodalOfficerName" -> request.setNodalOfficerName("  ");
                case "designation" -> request.setDesignation("\t");
                case "email" -> request.setEmail(" ");
                case "phone" -> request.setPhone("  ");
                default -> throw new IllegalArgumentException(field);
            }

            assertThatThrownBy(() -> service.addRecord(request, "cepc_do1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(field);
        }

        @Test
        @DisplayName("reports every missing field at once rather than one per round trip")
        void reportsAllMissingFieldsTogether() {
            CreateNodalOfficerRecordRequest request = CreateNodalOfficerRecordRequest.builder()
                    .complaintNumber(COMPLAINT_NO)
                    .build();

            assertThatThrownBy(() -> service.addRecord(request, "cepc_do1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("entityName")
                    .hasMessageContaining("nodalOfficerName")
                    .hasMessageContaining("designation")
                    .hasMessageContaining("email")
                    .hasMessageContaining("phone");
        }

        @Test
        @DisplayName("a complete request is persisted with trimmed values and a history entry")
        void persistsValidRequest() {
            when(recordRepository.existsByComplaintNumber(COMPLAINT_NO)).thenReturn(false);
            saveEchoesBack();

            CreateNodalOfficerRecordRequest request = validRequest();
            request.setEntityName("  State Bank of India  ");

            NodalOfficerRecord result = service.addRecord(request, "cepc_do1");

            assertThat(result.getId()).isEqualTo(99L);

            ArgumentCaptor<NodalOfficerRecord> saved = ArgumentCaptor.forClass(NodalOfficerRecord.class);
            verify(recordRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getEntityName())
                    .as("untrimmed names would not match the normalised lookup key later")
                    .isEqualTo(SBI);

            ArgumentCaptor<ComplaintTimeline> entry = ArgumentCaptor.forClass(ComplaintTimeline.class);
            verify(timelineRepository).save(entry.capture());
            assertThat(entry.getValue().getAction()).isEqualTo("no_record_added");
            assertThat(entry.getValue().getPerformedBy()).isEqualTo("cepc_do1");

            verify(notificationService).send(eq("RBIO_SUPERVISOR"), eq("NO_RECORD_CREATED"), any(), any(),
                    eq(COMPLAINT_NO), eq("NO_RECORD"), any());
        }

        @Test
        @DisplayName("a missing complaint number is rejected: a NO record is always held against a complaint")
        void rejectsMissingComplaintNumber() {
            CreateNodalOfficerRecordRequest request = validRequest();
            request.setComplaintNumber(null);

            assertThatThrownBy(() -> service.addRecord(request, "cepc_do1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("complaintNumber");
        }

        @Test
        @DisplayName("adding a second record for the same complaint is refused with a usable message")
        void refusesDuplicateForSameComplaint() {
            when(recordRepository.existsByComplaintNumber(COMPLAINT_NO)).thenReturn(true);

            assertThatThrownBy(() -> service.addRecord(validRequest(), "cepc_do1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(recordRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("a unique-key violation surfaces as a 400-style conflict, not an opaque 500")
        void uniqueViolationBecomesIllegalArgument() {
            when(recordRepository.existsByComplaintNumber(COMPLAINT_NO)).thenReturn(false);
            when(recordRepository.saveAndFlush(any(NodalOfficerRecord.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_no_complaint"));

            assertThatThrownBy(() -> service.addRecord(validRequest(), "cepc_do1"))
                    .as("GlobalExceptionHandler maps DataIntegrityViolationException to a generic 500, which "
                        + "tells the officer nothing about what to do next")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }
    }
}
