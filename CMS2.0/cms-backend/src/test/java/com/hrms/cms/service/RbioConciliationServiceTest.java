package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ConciliationMeeting;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ConciliationMeetingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbioConciliationServiceTest {

    private static final long ID = 92L;

    @Mock private ComplaintRepository complaintRepository;
    @Mock private ConciliationMeetingRepository meetingRepository;
    @Mock private ComplaintService complaintService;
    @Mock private CepcAuditService auditService;

    @InjectMocks
    private RbioConciliationService service;

    private Complaint complaint;

    @BeforeEach
    void setUp() {
        complaint = Complaint.builder()
                .id(ID)
                .complaintNumber("CMS-PNB-1234")
                .complainantName("Ramesh Kumar")
                .entityName("Punjab National Bank")
                .status("conciliation")
                .build();
    }

    private void stubComplaint() {
        when(complaintRepository.findById(ID)).thenReturn(Optional.of(complaint));
    }

    /** JPA assigns the identity on insert; mirror that so audit metadata has a real meeting id. */
    private void stubSaveAssigningId(long assignedId) {
        when(meetingRepository.save(any(ConciliationMeeting.class))).thenAnswer(inv -> {
            ConciliationMeeting m = inv.getArgument(0);
            if (m.getId() == null) m.setId(assignedId);
            return m;
        });
    }

    private void stubHistory(ConciliationMeeting... rows) {
        when(meetingRepository.findByComplaintIdOrderByIdAsc(ID)).thenReturn(List.of(rows));
    }

    private static ConciliationMeeting meeting(long id, String status, String date, String time) {
        return ConciliationMeeting.builder()
                .id(id)
                .complaintId(ID)
                .meetingStatus(status)
                .meetingDate(date == null ? null : LocalDate.parse(date))
                .meetingTime(time)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static Map<String, Object> payload(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> current(Map<String, Object> result) {
        return (Map<String, Object>) result.get("current");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> history(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("history");
    }

    @Nested
    class GetConciliation {

        @Test
        void reportsNoCurrentMeetingWhenNoneHasBeenScheduled() {
            stubComplaint();
            stubHistory();

            Map<String, Object> result = service.getConciliation(ID);

            assertThat(result).containsEntry("complaintId", ID)
                    .containsEntry("complaintNumber", "CMS-PNB-1234")
                    .containsEntry("current", null);
            assertThat(history(result)).isEmpty();
        }

        @Test
        void treatsTheNewestRowAsTheLiveMeetingAndKeepsTheEarlierOnesAsHistory() {
            stubComplaint();
            stubHistory(meeting(1L, "SCHEDULED", "2026-04-01", "10:30"),
                    meeting(2L, "RESCHEDULED", "2026-04-09", "15:00"));

            Map<String, Object> result = service.getConciliation(ID);

            assertThat(current(result)).containsEntry("id", 2L)
                    .containsEntry("meetingStatus", "RESCHEDULED")
                    .containsEntry("meetingDate", "2026-04-09")
                    .containsEntry("meetingTime", "15:00");
            assertThat(history(result)).hasSize(2);
            assertThat(history(result).get(0)).containsEntry("meetingDate", "2026-04-01");
        }

        @Test
        void keepsAnUnansweredYesNoDistinctFromAnsweredNo() {
            stubComplaint();
            ConciliationMeeting m = meeting(1L, "COMPLETED", "2026-04-01", "10:30");
            m.setAcceptedByEntity("no");
            stubHistory(m);

            Map<String, Object> result = service.getConciliation(ID);

            assertThat(current(result)).containsEntry("acceptedByEntity", false)
                    .containsEntry("acceptedByComplainant", null)
                    .containsEntry("conductedThroughVc", null);
        }

        @Test
        void rejectsAnUnknownComplaint() {
            when(complaintRepository.findById(ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getConciliation(ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Complaint not found");
        }
    }

    @Nested
    class SaveMeeting {

        @Test
        void schedulesTheFirstMeetingAndMirrorsItOntoTheComplaint() {
            stubComplaint();
            when(meetingRepository.findFirstByComplaintIdOrderByIdDesc(ID)).thenReturn(Optional.empty());
            stubSaveAssigningId(7L);
            stubHistory(meeting(7L, "SCHEDULED", "2026-04-01", "10:30"));

            service.saveMeeting(ID, payload(
                    "meetingStatus", "SCHEDULED",
                    "meetingDate", "2026-04-01",
                    "meetingTime", "10:30",
                    "acceptedByComplainant", true,
                    "acceptedByEntity", "no",
                    "conductedThroughVc", true,
                    "meetingComments", "Both parties briefed"), "officer1");

            ArgumentCaptor<ConciliationMeeting> saved = ArgumentCaptor.forClass(ConciliationMeeting.class);
            verify(meetingRepository).save(saved.capture());
            assertThat(saved.getValue().getComplaintId()).isEqualTo(ID);
            assertThat(saved.getValue().getMeetingStatus()).isEqualTo("SCHEDULED");
            assertThat(saved.getValue().getMeetingTime()).isEqualTo("10:30");
            assertThat(saved.getValue().getAcceptedByComplainant()).isEqualTo("yes");
            assertThat(saved.getValue().getAcceptedByEntity()).isEqualTo("no");
            assertThat(saved.getValue().getCreatedBy()).isEqualTo("officer1");
            assertThat(saved.getValue().getUpdatedBy()).isEqualTo("officer1");

            // RbioWorkflowService and the dashboard read these, not CONCILIATION_MEETINGS.
            assertThat(complaint.getConciliationDate()).isEqualTo(LocalDateTime.of(2026, 4, 1, 10, 30));
            assertThat(complaint.getWorkflowStage()).isEqualTo("MEETING_SCHEDULED");
            verify(complaintRepository).save(complaint);

            verify(complaintService).addTimeline(eq(ID), eq("rbio_conciliation_scheduled"), eq("officer1"),
                    anyString(), eq("conciliation"), eq("conciliation"));
            verify(auditService).logAction(eq("CMS-PNB-1234"), eq("RBIO_CONCILIATION_SCHEDULED"),
                    eq("officer1"), eq("RBIO"), anyString(), anyMap());
        }

        @Test
        void editsTheLiveMeetingInPlaceRatherThanOpeningAnother() {
            stubComplaint();
            ConciliationMeeting live = meeting(7L, "SCHEDULED", "2026-04-01", "10:30");
            when(meetingRepository.findFirstByComplaintIdOrderByIdDesc(ID)).thenReturn(Optional.of(live));
            stubSaveAssigningId(99L);
            stubHistory(live);

            service.saveMeeting(ID, payload("meetingComments", "Entity sought two more weeks"), "officer1");

            ArgumentCaptor<ConciliationMeeting> saved = ArgumentCaptor.forClass(ConciliationMeeting.class);
            verify(meetingRepository).save(saved.capture());
            assertThat(saved.getValue().getId()).isEqualTo(7L);
            assertThat(saved.getValue().getMeetingComments()).isEqualTo("Entity sought two more weeks");
            // Untouched keys keep their stored value.
            assertThat(saved.getValue().getMeetingDate()).isEqualTo(LocalDate.parse("2026-04-01"));
        }

        @Test
        void opensANewMeetingOnRescheduleSoTheEarlierOneSurvives() {
            stubComplaint();
            ConciliationMeeting live = meeting(7L, "SCHEDULED", "2026-04-01", "10:30");
            live.setMeetingComments("Complainant could not attend");
            when(meetingRepository.findFirstByComplaintIdOrderByIdDesc(ID)).thenReturn(Optional.of(live));
            stubSaveAssigningId(8L);
            stubHistory(live, meeting(8L, "RESCHEDULED", "2026-04-09", "15:00"));

            service.saveMeeting(ID, payload(
                    "meetingStatus", "RESCHEDULED",
                    "meetingDate", "2026-04-09",
                    "meetingTime", "15:00"), "officer1");

            ArgumentCaptor<ConciliationMeeting> saved = ArgumentCaptor.forClass(ConciliationMeeting.class);
            verify(meetingRepository).save(saved.capture());
            assertThat(saved.getValue().getId()).isEqualTo(8L);
            assertThat(saved.getValue().getMeetingDate()).isEqualTo(LocalDate.parse("2026-04-09"));
            // The superseded row is left exactly as it was.
            assertThat(live.getMeetingStatus()).isEqualTo("SCHEDULED");
            assertThat(live.getMeetingDate()).isEqualTo(LocalDate.parse("2026-04-01"));
            assertThat(live.getMeetingComments()).isEqualTo("Complainant could not attend");
        }

        @Test
        void opensANewMeetingOnceTheLiveOneIsClosed() {
            stubComplaint();
            ConciliationMeeting done = meeting(7L, "COMPLETED", "2026-04-01", "10:30");
            when(meetingRepository.findFirstByComplaintIdOrderByIdDesc(ID)).thenReturn(Optional.of(done));
            stubSaveAssigningId(8L);
            stubHistory(done, meeting(8L, "SCHEDULED", "2026-05-02", "11:00"));

            service.saveMeeting(ID, payload(
                    "meetingStatus", "SCHEDULED",
                    "meetingDate", "2026-05-02",
                    "meetingTime", "11:00"), "officer1");

            ArgumentCaptor<ConciliationMeeting> saved = ArgumentCaptor.forClass(ConciliationMeeting.class);
            verify(meetingRepository).save(saved.capture());
            assertThat(saved.getValue().getId()).isEqualTo(8L);
            assertThat(done.getMeetingStatus()).isEqualTo("COMPLETED");
        }

        @Test
        void closingAMeetingLeavesTheWorkflowTransitionAlone() {
            stubComplaint();
            ConciliationMeeting live = meeting(7L, "SCHEDULED", "2026-04-01", "10:30");
            when(meetingRepository.findFirstByComplaintIdOrderByIdDesc(ID)).thenReturn(Optional.of(live));
            stubSaveAssigningId(7L);
            stubHistory(live);

            service.saveMeeting(ID, payload(
                    "meetingStatus", "COMPLETED",
                    "acceptedByEntity", true,
                    "meetingComments", "Entity agreed to refund"), "officer1");

            // CONCILIATION_SUCCESS/FAILED own the outcome and stage; a tab edit must not pre-empt them.
            assertThat(complaint.getWorkflowStage()).isNull();
            assertThat(complaint.getConciliationOutcome()).isNull();
            verify(complaintRepository, never()).save(any(Complaint.class));
            verify(complaintService).addTimeline(eq(ID), eq("rbio_conciliation_completed"), eq("officer1"),
                    anyString(), anyString(), anyString());
        }

        @Test
        void acceptsAnHhMmSsTimeByDroppingTheSeconds() {
            stubComplaint();
            when(meetingRepository.findFirstByComplaintIdOrderByIdDesc(ID)).thenReturn(Optional.empty());
            stubSaveAssigningId(7L);
            stubHistory(meeting(7L, "SCHEDULED", "2026-04-01", "09:05"));

            service.saveMeeting(ID, payload(
                    "meetingDate", "2026-04-01",
                    "meetingTime", "09:05:00"), "officer1");

            ArgumentCaptor<ConciliationMeeting> saved = ArgumentCaptor.forClass(ConciliationMeeting.class);
            verify(meetingRepository).save(saved.capture());
            assertThat(saved.getValue().getMeetingTime()).isEqualTo("09:05");
            // An omitted status opens the meeting as scheduled.
            assertThat(saved.getValue().getMeetingStatus()).isEqualTo("SCHEDULED");
        }

        @Test
        void rejectsALiveMeetingWithNoDateOrTime() {
            stubComplaint();
            when(meetingRepository.findFirstByComplaintIdOrderByIdDesc(ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.saveMeeting(ID, payload(
                    "meetingStatus", "SCHEDULED",
                    "meetingComments", "to be fixed"), "officer1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("meetingDate");

            verify(meetingRepository, never()).save(any());
        }

        @Test
        void rejectsAStatusOutsideTheMeetingLifecycle() {
            stubComplaint();

            assertThatThrownBy(() -> service.saveMeeting(ID, payload("meetingStatus", "SETTLED"), "officer1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("meetingStatus");

            verify(meetingRepository, never()).findFirstByComplaintIdOrderByIdDesc(anyLong());
            verify(meetingRepository, never()).save(any());
        }

        @Test
        void rejectsAnUnrecognisedField() {
            stubComplaint();

            assertThatThrownBy(() -> service.saveMeeting(ID, payload("mettingStatus", "SCHEDULED"), "officer1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown field: mettingStatus");

            verify(meetingRepository, never()).save(any());
        }

        @Test
        void rejectsAMalformedTime() {
            stubComplaint();
            when(meetingRepository.findFirstByComplaintIdOrderByIdDesc(ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.saveMeeting(ID, payload(
                    "meetingDate", "2026-04-01",
                    "meetingTime", "25:99"), "officer1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("meetingTime");
        }

        @Test
        void rejectsAnUnknownComplaintBeforeTouchingTheMeetingTable() {
            when(complaintRepository.findById(ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.saveMeeting(ID, payload("meetingStatus", "CANCELLED"), "officer1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Complaint not found");

            verify(meetingRepository, never()).save(any());
        }
    }
}
