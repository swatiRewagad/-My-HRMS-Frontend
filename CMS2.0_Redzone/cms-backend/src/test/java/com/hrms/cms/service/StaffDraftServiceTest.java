package com.hrms.cms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.StaffDraft;
import com.hrms.cms.repository.ComplaintEditPresenceRepository;
import com.hrms.cms.repository.StaffDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Staff draft ownership, auto-save and discard semantics (UST673, UST674).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StaffDraftService")
class StaffDraftServiceTest {

    @Mock private StaffDraftRepository draftRepository;
    @Mock private ComplaintEditPresenceRepository presenceRepository;
    @Mock private SystemConfigService systemConfig;

    private StaffDraftService service;

    @BeforeEach
    void setUp() {
        service = new StaffDraftService(draftRepository, presenceRepository, systemConfig,
                new ObjectMapper());
        when(draftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Map<String, Object> form() {
        return Map.of("remarks", "partially typed", "closureClause", "");
    }

    @Nested
    @DisplayName("Ownership")
    class OwnershipTests {

        @Test
        @DisplayName("the owner is taken from the caller, and stored on the row")
        void ownerIsStored() {
            when(draftRepository.findByMilestoneAndOwnerUserIdAndComplaintNumber(any(), any(), any()))
                    .thenReturn(Optional.empty());

            service.save("CONCILIATION", "rbio_do_001", "CMP-1", form(), false);

            ArgumentCaptor<StaffDraft> captor = ArgumentCaptor.forClass(StaffDraft.class);
            verify(draftRepository).save(captor.capture());
            assertThat(captor.getValue().getOwnerUserId()).isEqualTo("rbio_do_001");
        }

        @Test
        @DisplayName("a save with no resolved owner is refused")
        void refusesMissingOwner() {
            assertThatThrownBy(() -> service.save("CONCILIATION", "  ", "CMP-1", form(), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("resolved owner");
        }

        @Test
        @DisplayName("deleting another user's draft reports not-found rather than deleting it")
        void cannotDeleteAnotherUsersDraft() {
            // The repository finder is owner-scoped, so a foreign id simply does not resolve. Asserting
            // the delete never happens is the point: the sibling EmailDraft PUT has no ownership check at
            // all, so any caller who knows an id can overwrite it.
            when(draftRepository.findByIdAndOwnerUserId(99L, "someone_else"))
                    .thenReturn(Optional.empty());

            assertThat(service.deleteOwn(99L, "someone_else")).isFalse();
            verify(draftRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Save integrity")
    class SaveIntegrityTests {

        @Test
        @DisplayName("an empty form is refused, so the timer cannot blank a good draft")
        void refusesEmptyForm() {
            // The autosave timer firing on an untouched form must not overwrite what the user saved a
            // moment earlier with nothing.
            assertThatThrownBy(() -> service.save("CONCILIATION", "u", "CMP-1", Map.of(), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no field values");
        }

        @Test
        @DisplayName("a non-REGISTER milestone requires a complaint number")
        void requiresComplaintNumberExceptRegister() {
            assertThatThrownBy(() -> service.save("FINAL_DECISION", "u", null, form(), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requires a complaint number");
        }

        @Test
        @DisplayName("REGISTER saves without a complaint number, because none exists yet")
        void registerNeedsNoComplaintNumber() {
            when(draftRepository.findFirstByMilestoneAndOwnerUserIdAndComplaintNumberIsNull(any(), any()))
                    .thenReturn(Optional.empty());

            StaffDraft saved = service.save("REGISTER", "u", null, form(), false);

            assertThat(saved.getComplaintNumber()).isNull();
        }

        @Test
        @DisplayName("an unknown milestone is refused by name")
        void refusesUnknownMilestone() {
            assertThatThrownBy(() -> service.save("NOT_A_MILESTONE", "u", "CMP-1", form(), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown draft milestone");
        }

        @Test
        @DisplayName("saving the same milestone twice UPDATES rather than inserting")
        void repeatedSaveUpdatesOneRow() {
            StaffDraft existing = StaffDraft.builder()
                    .id(7L).milestone("CONCILIATION").ownerUserId("u").complaintNumber("CMP-1")
                    .formDataJson("{}").build();
            when(draftRepository.findByMilestoneAndOwnerUserIdAndComplaintNumber(
                    "CONCILIATION", "u", "CMP-1")).thenReturn(Optional.of(existing));

            StaffDraft saved = service.save("CONCILIATION", "u", "CMP-1", form(), true);

            // A 2-minute timer over a shift would otherwise pile up ~240 rows per officer per complaint.
            assertThat(saved.getId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("the stored JSON round-trips, so a resume restores every typed value")
        void formDataRoundTrips() {
            when(draftRepository.findByMilestoneAndOwnerUserIdAndComplaintNumber(any(), any(), any()))
                    .thenReturn(Optional.empty());

            StaffDraft saved = service.save("FORWARD", "u", "CMP-1",
                    Map.of("remarks", "half written", "office", "MUMBAI"), false);

            assertThat(service.formDataOf(saved))
                    .containsEntry("remarks", "half written")
                    .containsEntry("office", "MUMBAI");
        }
    }

    @Nested
    @DisplayName("Auto-save (UST673)")
    class AutosaveTests {

        @Test
        @DisplayName("the interval comes from SYSTEM_CONFIG")
        void intervalFromConfig() {
            when(systemConfig.getInt(eq("cms.draft.autosave_interval_seconds"), anyInt()))
                    .thenReturn(300);

            assertThat(service.autosaveIntervalSeconds()).isEqualTo(300);
        }

        @Test
        @DisplayName("a negative configured interval is clamped to 0 rather than becoming a runaway timer")
        void negativeIntervalClamped() {
            when(systemConfig.getInt(eq("cms.draft.autosave_interval_seconds"), anyInt()))
                    .thenReturn(-5);

            assertThat(service.autosaveIntervalSeconds()).isZero();
        }

        @Test
        @DisplayName("an autosave row is marked AUTOSAVE, a manual save MANUAL")
        void saveSourceRecorded() {
            when(draftRepository.findByMilestoneAndOwnerUserIdAndComplaintNumber(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThat(service.save("FORWARD", "u", "CMP-1", form(), true).isAutosaved()).isTrue();
            assertThat(service.save("FORWARD", "u", "CMP-1", form(), false).isAutosaved()).isFalse();
        }

        @Test
        @DisplayName("discard removes AUTOSAVE rows and keeps the ones the user chose to save")
        void discardKeepsManualDrafts() {
            StaffDraft auto = StaffDraft.builder().id(1L).ownerUserId("u").complaintNumber("CMP-1")
                    .saveSource("AUTOSAVE").formDataJson("{}").build();
            StaffDraft manual = StaffDraft.builder().id(2L).ownerUserId("u").complaintNumber("CMP-1")
                    .saveSource("MANUAL").formDataJson("{}").build();
            when(draftRepository.findByOwnerUserIdAndComplaintNumber("u", "CMP-1"))
                    .thenReturn(List.of(auto, manual));

            assertThat(service.discardAutosave("u", "CMP-1")).isEqualTo(1);

            ArgumentCaptor<List<StaffDraft>> captor = ArgumentCaptor.forClass(List.class);
            verify(draftRepository).deleteAll(captor.capture());
            // Silently discarding a deliberately-saved draft because the user later submitted something
            // else would lose work they chose to retain.
            assertThat(captor.getValue()).containsExactly(auto);
        }
    }

    @Nested
    @DisplayName("Edit presence (UST675) is advisory")
    class PresenceTests {

        @Test
        @DisplayName("the caller is excluded from the list of other editors")
        void excludesSelf() {
            when(systemConfig.getInt(eq("cms.draft.edit_presence_stale_seconds"), anyInt()))
                    .thenReturn(90);
            when(presenceRepository.findByComplaintNumberAndUserId(any(), any()))
                    .thenReturn(Optional.empty());
            when(presenceRepository.findOtherActiveEditors(any(), any(), any()))
                    .thenReturn(List.of());

            List<Map<String, Object>> others =
                    service.heartbeatAndListOthers("CMP-1", "me", "Me");

            assertThat(others).isEmpty();
            // A user with the form open in two tabs must not be warned about themselves — that trains
            // people to dismiss the warning.
            verify(presenceRepository).findOtherActiveEditors(eq("CMP-1"), eq("me"), any());
        }

        @Test
        @DisplayName("a stale heartbeat window is derived from config, not hardcoded")
        void staleWindowFromConfig() {
            when(systemConfig.getInt(eq("cms.draft.edit_presence_stale_seconds"), anyInt()))
                    .thenReturn(45);

            assertThat(service.presenceStaleSeconds()).isEqualTo(45);
        }

        @Test
        @DisplayName("a zero or negative stale window falls back to the default rather than never warning")
        void invalidStaleWindowFallsBack() {
            when(systemConfig.getInt(eq("cms.draft.edit_presence_stale_seconds"), anyInt()))
                    .thenReturn(0);

            // A 0-second window would treat every heartbeat as instantly stale, so nobody would ever be
            // warned and the feature would look implemented while doing nothing.
            assertThat(service.presenceStaleSeconds()).isEqualTo(90);
        }
    }
}
