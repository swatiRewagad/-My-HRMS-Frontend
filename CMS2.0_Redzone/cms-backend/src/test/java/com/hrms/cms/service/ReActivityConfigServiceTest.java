package com.hrms.cms.service;

import com.hrms.cms.entity.ConfigChangeRequest;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.repository.ConfigAuditLogRepository;
import com.hrms.cms.repository.ConfigChangeRequestRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Maker-checker for RE Activity nudge thresholds (UST851).
 *
 * These prove the separation-of-duties logic. They do NOT prove the control is unbypassable —
 * SecurityConfig is still permitAll and the role guards fail open, so a caller can present any
 * X-User-Id as the second approver. That is tracked separately; the logic below is what will become
 * a real control once authentication is enforced.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReActivityConfigServiceTest {

    private static final String KEY = "cms.re.activity.nudge_days.opened";

    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private ConfigChangeRequestRepository changeRequestRepository;
    @Mock private ConfigAuditLogRepository configAuditLogRepository;

    @InjectMocks private ReActivityConfigService service;

    private ConfigChangeRequest pending(String requestedBy, String current, String proposed) {
        return ConfigChangeRequest.builder()
                .id(1L)
                .configKey(KEY)
                .currentValue(current)
                .proposedValue(proposed)
                .requestedBy(requestedBy)
                .status("PENDING")
                .build();
    }

    private void liveValue(String value) {
        when(systemConfigRepository.findByConfigKey(KEY)).thenReturn(Optional.of(
                SystemConfig.builder().configKey(KEY).configValue(value).build()));
    }

    @Nested
    @DisplayName("Requesting a change")
    class Requesting {

        @Test
        @DisplayName("stages the change without applying it")
        void stagesOnly() {
            liveValue("3");
            when(changeRequestRepository.findByConfigKeyAndStatus(KEY, "PENDING"))
                    .thenReturn(Optional.empty());
            when(changeRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ConfigChangeRequest req = service.requestChange(KEY, "7", "Tighter follow-up", "admin_a");

            assertThat(req.getStatus()).isEqualTo("PENDING");
            assertThat(req.getCurrentValue()).isEqualTo("3");
            // Crucially the live config is untouched until somebody else approves.
            verify(systemConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses a second pending change on the same key")
        void onePendingPerKey() {
            liveValue("3");
            when(changeRequestRepository.findByConfigKeyAndStatus(KEY, "PENDING"))
                    .thenReturn(Optional.of(pending("admin_a", "3", "7")));

            assertThatThrownBy(() -> service.requestChange(KEY, "9", null, "admin_b"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already awaiting approval");
        }

        @Test
        @DisplayName("refuses an out-of-range threshold")
        void rejectsOutOfRange() {
            liveValue("3");
            assertThatThrownBy(() -> service.requestChange(KEY, "0", null, "admin_a"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.requestChange(KEY, "400", null, "admin_a"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.requestChange(KEY, "abc", null, "admin_a"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses a key outside the RE activity namespace")
        void rejectsForeignKey() {
            assertThatThrownBy(() -> service.requestChange("timeline.appeal.filing_window_days",
                    "45", null, "admin_a"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.requestChange("cms.re.activity.made_up_key",
                    "5", null, "admin_a"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses an unidentified requester")
        void requiresIdentity() {
            liveValue("3");
            assertThatThrownBy(() -> service.requestChange(KEY, "7", null, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Approving a change")
    class Approving {

        @Test
        @DisplayName("refuses self-approval by the requester")
        void refusesSelfApproval() {
            when(changeRequestRepository.findById(1L))
                    .thenReturn(Optional.of(pending("admin_a", "3", "7")));

            assertThatThrownBy(() -> service.approve(1L, "admin_a", "looks fine to me"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Maker-Checker");

            verify(systemConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses self-approval regardless of username casing")
        void selfApprovalCheckIsCaseInsensitive() {
            when(changeRequestRepository.findById(1L))
                    .thenReturn(Optional.of(pending("admin_a", "3", "7")));

            // Keycloak usernames are case-insensitive, so a case variant is the same person and must
            // not be a way around the control.
            assertThatThrownBy(() -> service.approve(1L, "ADMIN_A", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Maker-Checker");
        }

        @Test
        @DisplayName("applies the value when a different administrator approves")
        void appliesOnIndependentApproval() {
            when(changeRequestRepository.findById(1L))
                    .thenReturn(Optional.of(pending("admin_a", "3", "7")));
            liveValue("3");
            when(changeRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ConfigChangeRequest decided = service.approve(1L, "admin_b", "Agreed");

            assertThat(decided.getStatus()).isEqualTo("APPROVED");
            assertThat(decided.getDecidedBy()).isEqualTo("admin_b");

            ArgumentCaptor<SystemConfig> saved = ArgumentCaptor.forClass(SystemConfig.class);
            verify(systemConfigRepository).save(saved.capture());
            assertThat(saved.getValue().getConfigValue()).isEqualTo("7");

            // The existing audit log stays the single place to read what actually changed.
            verify(configAuditLogRepository).save(any());
        }

        @Test
        @DisplayName("refuses a request whose live value moved since it was raised")
        void refusesStaleRequest() {
            when(changeRequestRepository.findById(1L))
                    .thenReturn(Optional.of(pending("admin_a", "3", "7")));
            // Someone else changed it to 5 in the meantime, so the approver is not approving what
            // they think they are.
            liveValue("5");

            assertThatThrownBy(() -> service.approve(1L, "admin_b", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("changed since");

            verify(systemConfigRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses to decide an already-decided request")
        void refusesDoubleDecision() {
            ConfigChangeRequest approved = pending("admin_a", "3", "7");
            approved.setStatus("APPROVED");
            when(changeRequestRepository.findById(1L)).thenReturn(Optional.of(approved));

            assertThatThrownBy(() -> service.approve(1L, "admin_b", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already APPROVED");
        }
    }

    @Nested
    @DisplayName("Rejecting a change")
    class Rejecting {

        @Test
        @DisplayName("requires a reason")
        void reasonRequired() {
            assertThatThrownBy(() -> service.reject(1L, "admin_b", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("records a self-cancellation as WITHDRAWN, not REJECTED")
        void selfCancellationIsWithdrawal() {
            when(changeRequestRepository.findById(1L))
                    .thenReturn(Optional.of(pending("admin_a", "3", "7")));
            when(changeRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ConfigChangeRequest decided = service.reject(1L, "admin_a", "Changed my mind");

            // Conflating the two would overstate the review that actually took place.
            assertThat(decided.getStatus()).isEqualTo("WITHDRAWN");
        }

        @Test
        @DisplayName("records an independent refusal as REJECTED")
        void independentRefusalIsRejection() {
            when(changeRequestRepository.findById(1L))
                    .thenReturn(Optional.of(pending("admin_a", "3", "7")));
            when(changeRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertThat(service.reject(1L, "admin_b", "Too long a window").getStatus())
                    .isEqualTo("REJECTED");
            verify(systemConfigRepository, never()).save(any());
        }
    }
}
