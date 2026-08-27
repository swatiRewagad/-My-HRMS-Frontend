package com.rbi.cms.mailintake.admin;

import com.rbi.cms.mailintake.entity.AdminAction;
import com.rbi.cms.mailintake.entity.AdminActionStatus;
import com.rbi.cms.mailintake.entity.AdminActionType;
import com.rbi.cms.mailintake.entity.InboundEmail;
import com.rbi.cms.mailintake.entity.InboundEmailStatus;
import com.rbi.cms.mailintake.repository.AdminActionRepository;
import com.rbi.cms.mailintake.repository.InboundEmailAttachmentRepository;
import com.rbi.cms.mailintake.repository.InboundEmailEventRepository;
import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import com.rbi.cms.mailintake.smtp.RawMessageStore;
import com.rbi.cms.mailintake.state.InboundEmailStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain Mockito unit tests (no Spring context) for the maker-checker rules — the part of Stage 5
 *  with real logic worth pinning down: a requester can never also be the decider, a request can
 *  only be decided once, and approval dispatches to the right InboundEmailStateMachine call. */
class AdminMailIntakeServiceTest {

    private InboundEmailRepository emailRepository;
    private InboundEmailEventRepository eventRepository;
    private InboundEmailAttachmentRepository attachmentRepository;
    private AdminActionRepository actionRepository;
    private RawMessageStore rawMessageStore;
    private InboundEmailStateMachine stateMachine;
    private AdminMailIntakeService service;

    @BeforeEach
    void setUp() {
        emailRepository = mock(InboundEmailRepository.class);
        eventRepository = mock(InboundEmailEventRepository.class);
        attachmentRepository = mock(InboundEmailAttachmentRepository.class);
        actionRepository = mock(AdminActionRepository.class);
        rawMessageStore = mock(RawMessageStore.class);
        stateMachine = mock(InboundEmailStateMachine.class);
        service = new AdminMailIntakeService(emailRepository, eventRepository, attachmentRepository,
                actionRepository, rawMessageStore, stateMachine);
    }

    private InboundEmail quarantinedEmail(long id) {
        InboundEmail email = InboundEmail.builder()
                .id(id)
                .status(InboundEmailStatus.QUARANTINED)
                .build();
        when(emailRepository.findById(id)).thenReturn(Optional.of(email));
        return email;
    }

    @Test
    void requesterCannotDecideTheirOwnRequest() {
        quarantinedEmail(1L);
        AdminAction pending = AdminAction.builder()
                .id(10L).emailId(1L).actionType(AdminActionType.REPLAY)
                .status(AdminActionStatus.PENDING).requestedBy("alice").requestReason("test")
                .build();
        when(actionRepository.findById(10L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.decide(10L, "alice", true, "approving my own request"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maker-checker");

        verify(stateMachine, never()).replay(any(), any());
    }

    @Test
    void alreadyDecidedActionCannotBeDecidedAgain() {
        AdminAction decided = AdminAction.builder()
                .id(11L).emailId(1L).actionType(AdminActionType.REPLAY)
                .status(AdminActionStatus.APPROVED).requestedBy("alice").requestReason("test")
                .build();
        when(actionRepository.findById(11L)).thenReturn(Optional.of(decided));

        assertThatThrownBy(() -> service.decide(11L, "bob", true, "too late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been decided");
    }

    @Test
    void approvingReplayInvokesStateMachineReplay() {
        InboundEmail email = quarantinedEmail(2L);
        AdminAction pending = AdminAction.builder()
                .id(12L).emailId(2L).actionType(AdminActionType.REPLAY)
                .status(AdminActionStatus.PENDING).requestedBy("alice").requestReason("relay fixed")
                .build();
        when(actionRepository.findById(12L)).thenReturn(Optional.of(pending));
        when(actionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminAction result = service.decide(12L, "bob", true, "confirmed");

        assertThat(result.getStatus()).isEqualTo(AdminActionStatus.APPROVED);
        assertThat(result.getDecidedBy()).isEqualTo("bob");
        verify(stateMachine).replay(eq(email), any());
    }

    @Test
    void approvingForceLinkSetsLinkedComplaintIdWithoutTouchingStatus() {
        InboundEmail email = quarantinedEmail(3L);
        AdminAction pending = AdminAction.builder()
                .id(13L).emailId(3L).actionType(AdminActionType.FORCE_LINK)
                .targetComplaintId("CMP-2026-000999")
                .status(AdminActionStatus.PENDING).requestedBy("alice").requestReason("confirmed by phone")
                .build();
        when(actionRepository.findById(13L)).thenReturn(Optional.of(pending));
        when(actionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.decide(13L, "bob", true, "double-checked");

        assertThat(email.getLinkedComplaintId()).isEqualTo("CMP-2026-000999");
        assertThat(email.getComplaintRef()).isEqualTo("CMP-2026-000999");
        assertThat(email.getStatus()).isEqualTo(InboundEmailStatus.QUARANTINED); // unchanged
        verify(stateMachine, never()).replay(any(), any());
        verify(emailRepository).save(email);
    }

    @Test
    void rejectingDoesNotTouchTheEmailAtAll() {
        quarantinedEmail(4L);
        AdminAction pending = AdminAction.builder()
                .id(14L).emailId(4L).actionType(AdminActionType.REPLAY)
                .status(AdminActionStatus.PENDING).requestedBy("alice").requestReason("test")
                .build();
        when(actionRepository.findById(14L)).thenReturn(Optional.of(pending));
        when(actionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminAction result = service.decide(14L, "bob", false, "not convincing");

        assertThat(result.getStatus()).isEqualTo(AdminActionStatus.REJECTED);
        verifyNoInteractions(rawMessageStore);
        verify(stateMachine, never()).replay(any(), any());
        verify(emailRepository, never()).save(any());
    }

    @Test
    void downloadThrowsOncePurged() {
        InboundEmail purged = InboundEmail.builder()
                .id(5L).status(InboundEmailStatus.PROCESSED)
                .rawPurgedAt(java.time.Instant.now())
                .build();
        when(emailRepository.findById(5L)).thenReturn(Optional.of(purged));

        assertThatThrownBy(() -> service.downloadRaw(5L, "bob"))
                .isInstanceOf(AdminMailIntakeService.RawBytesPurgedException.class);
        verifyNoInteractions(rawMessageStore);
    }
}
