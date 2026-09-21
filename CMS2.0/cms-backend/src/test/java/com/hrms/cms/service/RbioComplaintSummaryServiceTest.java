package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintAdditionalDetail;
import com.hrms.cms.entity.ComplaintEligibilityAnswer;
import com.hrms.cms.entity.ComplaintRbioFormData;
import com.hrms.cms.entity.ComplaintReadReceipt;
import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.ComplaintAdditionalDetailRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintEligibilityAnswerRepository;
import com.hrms.cms.repository.ComplaintRbioFormDataRepository;
import com.hrms.cms.repository.ComplaintReadReceiptRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbioComplaintSummaryServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private ComplaintEligibilityAnswerRepository eligibilityRepository;
    @Mock private ComplaintAdditionalDetailRepository additionalDetailRepository;
    @Mock private ComplaintRbioFormDataRepository formDataRepository;
    @Mock private ComplaintReadReceiptRepository readReceiptRepository;
    @Mock private RegulatedEntityRepository regulatedEntityRepository;
    @Mock private ComplaintCategoryRepository categoryRepository;
    @Mock private RbioSlaService rbioSlaService;
    @Mock private ComplaintService complaintService;
    @Mock private CepcAuditService auditService;
    // Real instance, not a mock: the permission rule under test lives inside it.
    @Spy private RbioHierarchyService rbioHierarchyService = new RbioHierarchyService();

    @InjectMocks
    private RbioComplaintSummaryService service;

    private Complaint complaint;

    @BeforeEach
    void setUp() {
        complaint = Complaint.builder()
                .id(92L)
                .complaintNumber("CMS-PNB-1234")
                .complainantName("Ramesh Kumar")
                .complainantEmail("ramesh@example.com")
                .entityName("Punjab National Bank")
                .categoryName("Loans and advances")
                .subject("Loan not disbursed")
                .description("Sanctioned but never disbursed")
                .status("SENT_TO_DEPUTY_OMBUDSMAN")
                .build();
    }

    private void stubComplaint() {
        when(complaintRepository.findById(92L)).thenReturn(Optional.of(complaint));
    }

    @Nested
    class GetSummary {

        @Test
        void returnsAllSectionsWhenEveryChildRowIsMissing() {
            stubComplaint();
            when(eligibilityRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(additionalDetailRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(formDataRepository.findByComplaintId(92L)).thenReturn(Optional.empty());

            Map<String, Object> summary = service.getSummary(92L);

            assertThat(summary).containsKeys(
                    "id", "navBarDto", "basicDetailsDto", "eligibility", "entityDetails", "complainDetailsDto");
            assertThat(section(summary, "complainDetailsDto")).containsKeys(
                    "basicIdentificationDto", "complaintClassification", "financialDetails",
                    "legalCaseDetails", "additionalInformation", "flagsAndIndicators", "complaintLinkage");
            // A complaint filed with no formData must read as "never answered", not as "answered no".
            assertThat(section(summary, "eligibility").get("disputeBetweenREs")).isNull();
        }

        @Test
        void mapsYesNoStringsToTriStateBooleans() {
            stubComplaint();
            when(eligibilityRepository.findByComplaintId(92L)).thenReturn(Optional.of(
                    ComplaintEligibilityAnswer.builder()
                            .complaintId(92L)
                            .filedWithRe("yes")
                            .receivedReply("no")
                            .build()));
            when(additionalDetailRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(formDataRepository.findByComplaintId(92L)).thenReturn(Optional.empty());

            Map<String, Object> eligibility = section(service.getSummary(92L), "eligibility");

            assertThat(eligibility.get("writtenComplaintFiledWithRE")).isEqualTo(true);
            assertThat(eligibility.get("receivedReplyFromEntity")).isEqualTo(false);
            assertThat(eligibility.get("frivolousVexatiousThreatening")).isNull();
        }

        @Test
        void fallsBackToPreviouslyFiledWithCepcWhenTheOfficerColumnIsUnanswered() {
            stubComplaint();
            when(eligibilityRepository.findByComplaintId(92L)).thenReturn(Optional.of(
                    ComplaintEligibilityAnswer.builder()
                            .complaintId(92L)
                            .previouslyFiledWithCepc("yes")
                            .build()));
            when(additionalDetailRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(formDataRepository.findByComplaintId(92L)).thenReturn(Optional.empty());

            assertThat(section(service.getSummary(92L), "eligibility").get("complaintFiledWithCEPCOrRBI"))
                    .isEqualTo(true);
        }

        @Test
        void officerAnswerWinsOverThePublicWizardAnswer() {
            stubComplaint();
            when(eligibilityRepository.findByComplaintId(92L)).thenReturn(Optional.of(
                    ComplaintEligibilityAnswer.builder()
                            .complaintId(92L)
                            .previouslyFiledWithCepc("yes")
                            .complaintFiledWithCepcOrRbi("no")
                            .build()));
            when(additionalDetailRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(formDataRepository.findByComplaintId(92L)).thenReturn(Optional.empty());

            assertThat(section(service.getSummary(92L), "eligibility").get("complaintFiledWithCEPCOrRBI"))
                    .isEqualTo(false);
        }

        @Test
        void takesSlaBreachInFromTheSlaServiceRatherThanRecomputingIt() {
            stubComplaint();
            when(eligibilityRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(additionalDetailRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(formDataRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(rbioSlaService.formatBreachIn(complaint)).thenReturn("-12 Days");

            assertThat(section(service.getSummary(92L), "navBarDto").get("slaBreachIn")).isEqualTo("-12 Days");
        }

        @Test
        void readsTheTwoCommentsBoxesFromTheirOwnColumns() {
            stubComplaint();
            when(eligibilityRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(additionalDetailRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(formDataRepository.findByComplaintId(92L)).thenReturn(Optional.of(
                    ComplaintRbioFormData.builder()
                            .complaintId(92L)
                            .comments("Keyed in by the officer")
                            .additionalComments("Escalated by DEO")
                            .build()));

            Map<String, Object> summary = service.getSummary(92L);

            assertThat(section(summary, "basicDetailsDto").get("comments")).isEqualTo("Keyed in by the officer");
            assertThat(section(section(summary, "complainDetailsDto"), "additionalInformation").get("comments"))
                    .isEqualTo("Escalated by DEO");
        }

        @Test
        void readsTheStaffEmployerRelationshipAnswer() {
            stubComplaint();
            when(eligibilityRepository.findByComplaintId(92L)).thenReturn(Optional.of(
                    ComplaintEligibilityAnswer.builder().complaintId(92L).staffOfRe("yes").build()));
            when(additionalDetailRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(formDataRepository.findByComplaintId(92L)).thenReturn(Optional.empty());

            assertThat(section(service.getSummary(92L), "eligibility").get("staffOfREEmployerRelationship"))
                    .isEqualTo(true);
        }

        @Test
        void throwsWhenTheComplaintDoesNotExist() {
            when(complaintRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSummary(404L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Complaint not found");
        }
    }

    @Nested
    class UpdateSummary {

        @BeforeEach
        void stubReadBack() {
            stubComplaint();
            when(eligibilityRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(additionalDetailRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            when(formDataRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
        }

        @Test
        void writesThroughToTheComplaintRow() {
            service.updateSummary(92L, nest("entityDetails", Map.of("bsrCode", "0123456")), "officer1", Set.of());

            assertThat(complaint.getEntityBsrCode()).isEqualTo("0123456");
            verify(complaintRepository).save(complaint);
        }

        @Test
        void clearsAFieldPresentWithANullValue() {
            complaint.setSubject("Loan not disbursed");
            Map<String, Object> basic = new HashMap<>();
            basic.put("subject", null);

            service.updateSummary(92L, nest("basicDetailsDto", basic), "officer1", Set.of());

            assertThat(complaint.getSubject()).isNull();
        }

        @Test
        void leavesAnOmittedFieldUntouched() {
            service.updateSummary(92L, nest("basicDetailsDto", Map.of("cpgramNumber", "CP-9")), "officer1", Set.of());

            assertThat(complaint.getSubject()).isEqualTo("Loan not disbursed");
        }

        @Test
        void createsTheChildRowWhenNoneExists() {
            service.updateSummary(92L, nest("basicDetailsDto", Map.of("cpgramNumber", "CP-9")), "officer1", Set.of());

            ArgumentCaptor<ComplaintRbioFormData> captor = ArgumentCaptor.forClass(ComplaintRbioFormData.class);
            verify(formDataRepository).save(captor.capture());
            assertThat(captor.getValue().getComplaintId()).isEqualTo(92L);
            assertThat(captor.getValue().getCpgramNumber()).isEqualTo("CP-9");
        }

        @Test
        void editingAdditionalInformationCommentsLeavesBasicDetailsCommentsAlone() {
            // Both boxes used to share one column, so saving either wiped the other.
            when(formDataRepository.findByComplaintId(92L)).thenReturn(Optional.of(
                    ComplaintRbioFormData.builder()
                            .complaintId(92L)
                            .comments("Keyed in by the officer")
                            .build()));

            service.updateSummary(92L,
                    nest("complainDetailsDto", nest("additionalInformation", Map.of("comments", "Escalated by DEO"))),
                    "officer1", Set.of());

            ArgumentCaptor<ComplaintRbioFormData> captor = ArgumentCaptor.forClass(ComplaintRbioFormData.class);
            verify(formDataRepository).save(captor.capture());
            assertThat(captor.getValue().getAdditionalComments()).isEqualTo("Escalated by DEO");
            assertThat(captor.getValue().getComments()).isEqualTo("Keyed in by the officer");
        }

        @Test
        void persistsTheStaffEmployerRelationshipAnswer() {
            service.updateSummary(92L,
                    nest("eligibility", Map.of("staffOfREEmployerRelationship", true)), "officer1", Set.of());

            ArgumentCaptor<ComplaintEligibilityAnswer> captor =
                    ArgumentCaptor.forClass(ComplaintEligibilityAnswer.class);
            verify(eligibilityRepository).save(captor.capture());
            assertThat(captor.getValue().getStaffOfRe()).isEqualTo("yes");
        }

        @Test
        void convertsBooleansBackToTheStoredYesNoStrings() {
            service.updateSummary(92L, nest("eligibility", Map.of("disputeBetweenREs", true)), "officer1", Set.of());

            ArgumentCaptor<ComplaintEligibilityAnswer> captor =
                    ArgumentCaptor.forClass(ComplaintEligibilityAnswer.class);
            verify(eligibilityRepository).save(captor.capture());
            assertThat(captor.getValue().getDisputeBetweenRes()).isEqualTo("yes");
        }

        @Test
        void recordsATimelineEntryAndAnAuditTrail() {
            service.updateSummary(92L, nest("basicDetailsDto", Map.of("cpgramNumber", "CP-9")), "officer1", Set.of());

            verify(complaintService).addTimeline(eq(92L), any(), eq("officer1"), any(), any(), any());
            verify(auditService).logAction(eq("CMS-PNB-1234"), any(), eq("officer1"), any(), any(), any());
        }

        @Test
        void movesTheComplaintToTheEntityTheIdPointsAt() {
            when(regulatedEntityRepository.findById(7L)).thenReturn(Optional.of(RegulatedEntity.builder()
                    .id(7L).name("HDFC Bank Limited").department("RBIO").entityType("Private Sector Bank")
                    .build()));

            service.updateSummary(92L, nest("entityDetails", Map.of("id", 7)), "officer1", Set.of());

            assertThat(complaint.getRegulatedEntityId()).isEqualTo(7L);
            assertThat(complaint.getEntityName()).isEqualTo("HDFC Bank Limited");
        }

        @Test
        void takesTheEntityNameFromTheEntityRatherThanTheRequest() {
            // An id and a name that disagree would show one entity on screen while the complaint is
            // forwarded to the other, so the entity record wins.
            when(regulatedEntityRepository.findById(7L)).thenReturn(Optional.of(RegulatedEntity.builder()
                    .id(7L).name("HDFC Bank Limited").department("RBIO")
                    .build()));

            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("id", 7);
            entity.put("entityName", "Some Other Bank");

            service.updateSummary(92L, nest("entityDetails", entity), "officer1", Set.of());

            assertThat(complaint.getEntityName()).isEqualTo("HDFC Bank Limited");
        }

        @Test
        void clearsTheEntityWhenTheIdIsSentAsNull() {
            complaint.setRegulatedEntityId(7L);
            Map<String, Object> entity = new HashMap<>();
            entity.put("id", null);

            service.updateSummary(92L, nest("entityDetails", entity), "officer1", Set.of());

            assertThat(complaint.getRegulatedEntityId()).isNull();
            // The name is a free-text label on the complaint, so clearing the link must not erase it.
            assertThat(complaint.getEntityName()).isEqualTo("Punjab National Bank");
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsAnUnknownSection() {
            stubComplaint();

            assertThatThrownBy(() -> service.updateSummary(92L, nest("bogusSection", Map.of("a", 1)), "officer1", Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown section");
            verify(complaintRepository, never()).save(any());
        }

        @Test
        void rejectsAnEntityIdThatMatchesNoEntity() {
            stubComplaint();
            when(regulatedEntityRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateSummary(92L,
                    nest("entityDetails", Map.of("id", 404)), "officer1", Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match any regulated entity");
            verify(complaintRepository, never()).save(any());
        }

        @Test
        void rejectsANegativeAmount() {
            stubComplaint();

            assertThatThrownBy(() -> service.updateSummary(92L,
                    nest("complainDetailsDto", Map.of("financialDetails", Map.<String, Object>of("compensationSought", "-5"))),
                    "officer1", Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be negative");
        }

        @Test
        void rejectsANonBooleanYesNoValue() {
            stubComplaint();

            assertThatThrownBy(() -> service.updateSummary(92L,
                    nest("eligibility", Map.of("disputeBetweenREs", "maybe")), "officer1", Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be true, false or null");
        }

        @Test
        void rejectsAMalformedDate() {
            stubComplaint();

            assertThatThrownBy(() -> service.updateSummary(92L,
                    nest("eligibility", Map.of("firstFiledWithREDate", "15-01-2026")), "officer1", Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ISO date");
        }
    }

    @Nested
    class BackfillFromEmailDraft {

        @Test
        void carriesTheDraftFieldsThatTheComplaintRowCannotHold() {
            when(formDataRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            EmailDraft draft = EmailDraft.builder()
                    .draftId("DRF-1")
                    .receivedAt(LocalDateTime.of(2026, 1, 15, 10, 30))
                    .modeOfReceipt("EMAIL")
                    .additionalComments("Escalated by DEO")
                    .cpgramsNumber("CP-77")
                    .legalCaseFiled("Yes")
                    .loanDisposalAmount("1,50,000")
                    .isFreeMarkedComplaint("No")
                    .build();

            service.backfillFromEmailDraft(draft, 92L);

            ArgumentCaptor<ComplaintRbioFormData> captor = ArgumentCaptor.forClass(ComplaintRbioFormData.class);
            verify(formDataRepository).save(captor.capture());
            ComplaintRbioFormData row = captor.getValue();
            assertThat(row.getDraftId()).isEqualTo("DRF-1");
            assertThat(row.getReceiptDate()).isEqualTo(LocalDate.of(2026, 1, 15));
            assertThat(row.getModeOfReceipt()).isEqualTo("EMAIL");
            assertThat(row.getAdditionalComments()).isEqualTo("Escalated by DEO");
            // The draft carries only one comments field, so Basic Details starts empty for the
            // officer to fill in rather than being seeded with the Additional Information text.
            assertThat(row.getComments()).isNull();
            assertThat(row.getCpgramNumber()).isEqualTo("CP-77");
            assertThat(row.getLegalCaseFiled()).isEqualTo("yes");
            assertThat(row.getFreeMarkedComplaint()).isEqualTo("no");
            assertThat(row.getLoanDisposalAmount()).isEqualByComparingTo(new BigDecimal("150000"));
        }

        @Test
        void storesNullRatherThanFailingConversionOnUnparseableDraftText() {
            when(formDataRepository.findByComplaintId(92L)).thenReturn(Optional.empty());
            EmailDraft draft = EmailDraft.builder()
                    .draftId("DRF-2")
                    .dateOfFilingComplaint("sometime last March")
                    .loanDisposalAmount("approx 2 lakh")
                    .legalCaseFiled("unclear")
                    .build();

            service.backfillFromEmailDraft(draft, 92L);

            ArgumentCaptor<ComplaintRbioFormData> captor = ArgumentCaptor.forClass(ComplaintRbioFormData.class);
            verify(formDataRepository).save(captor.capture());
            assertThat(captor.getValue().getDateOfFilingComplaint()).isNull();
            assertThat(captor.getValue().getLoanDisposalAmount()).isNull();
            assertThat(captor.getValue().getLegalCaseFiled()).isNull();
        }

        @Test
        void doesNothingWithoutADraft() {
            service.backfillFromEmailDraft(null, 92L);

            verify(formDataRepository, never()).save(any());
        }
    }

    @Nested
    class MarkRead {

        @Test
        void recordsAReceiptForTheReaderAndReturnsTheComplaintNumberToAnnounce() {
            stubComplaint();

            assertThat(service.markRead(92L, "officer.a")).contains("CMS-PNB-1234");

            ArgumentCaptor<ComplaintReadReceipt> captor = ArgumentCaptor.forClass(ComplaintReadReceipt.class);
            verify(readReceiptRepository).save(captor.capture());
            assertThat(captor.getValue().getComplaintId()).isEqualTo(92L);
            assertThat(captor.getValue().getUsername()).isEqualTo("officer.a");
        }

        @Test
        void reopeningAComplaintThisOfficerAlreadyReadNeitherWritesNorAnnounces() {
            stubComplaint();
            when(readReceiptRepository.existsByComplaintIdAndUsername(92L, "officer.a")).thenReturn(true);

            assertThat(service.markRead(92L, "officer.a")).isEmpty();

            verify(readReceiptRepository, never()).save(any());
        }

        @Test
        void aColleaguesReadDoesNotMarkItReadForThisOfficer() {
            stubComplaint();
            when(readReceiptRepository.existsByComplaintIdAndUsername(92L, "officer.b")).thenReturn(false);

            assertThat(service.markRead(92L, "officer.b")).contains("CMS-PNB-1234");

            verify(readReceiptRepository).save(any());
        }

        @Test
        void treatsAMissingComplaintAsNothingToMark() {
            when(complaintRepository.findById(404L)).thenReturn(Optional.empty());

            assertThat(service.markRead(404L, "officer.a")).isEmpty();

            verify(readReceiptRepository, never()).save(any());
        }

        @Test
        void anUnidentifiedCallerRecordsNothing() {
            assertThat(service.markRead(92L, null)).isEmpty();

            verify(readReceiptRepository, never()).save(any());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    private static Map<String, Object> nest(String key, Object body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(key, body);
        return payload;
    }
}
