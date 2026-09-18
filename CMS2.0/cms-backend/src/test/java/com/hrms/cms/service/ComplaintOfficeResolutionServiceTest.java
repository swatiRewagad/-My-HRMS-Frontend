package com.hrms.cms.service;

import com.hrms.cms.entity.BankBranch;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.OfficeCodeMaster;
import com.hrms.cms.entity.Pincode;
import com.hrms.cms.repository.BankBranchRepository;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import com.hrms.cms.repository.PincodeRepository;
import com.hrms.cms.service.ComplaintOfficeResolutionService.OfficeResolution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComplaintOfficeResolutionServiceTest {

    @Mock private BankBranchRepository bankBranchRepository;
    @Mock private PincodeRepository pincodeRepository;
    @Mock private OfficeCodeMasterRepository officeCodeRepository;
    @Mock private ComplaintNumberGeneratorService complaintNumberGenerator;

    @InjectMocks
    private ComplaintOfficeResolutionService service;

    private void stubOffice(String officeName, String officeCode) {
        when(complaintNumberGenerator.resolveOfficeName(anyString(), any(), any())).thenReturn(officeName);
        when(officeCodeRepository.findByOfficeNameAndIsActiveTrue(officeName))
                .thenReturn(Optional.of(OfficeCodeMaster.builder()
                        .officeName(officeName).officeCode(officeCode).build()));
    }

    private static BankBranch branch(String district, String state) {
        return BankBranch.builder().district(district).state(state).pincode("400001").build();
    }

    @Test
    void prefersTheBankBranchRowOverEveryOtherSource() {
        stubOffice("Mumbai-I", "001");
        when(bankBranchRepository.findByBankIdAndPincode(7L, "400001"))
                .thenReturn(List.of(branch("Mumbai", "Maharashtra")));

        Complaint complaint = Complaint.builder()
                .bankId(7L)
                .entityPincode("400001")
                .entityState("Karnataka")
                .complainantPincode("560001")
                .build();

        OfficeResolution resolution = service.resolve(complaint, "RBIO");

        assertThat(resolution.source()).isEqualTo("BANK_BRANCH");
        assertThat(resolution.district()).isEqualTo("Mumbai");
        assertThat(resolution.state()).isEqualTo("Maharashtra");
        assertThat(resolution.officeName()).isEqualTo("Mumbai-I");
        assertThat(resolution.officeCode()).isEqualTo("001");
        verify(pincodeRepository, never()).findByPincode(anyString());
    }

    @Test
    void matchesTheBranchByEntityCodeWhenTheBankIdMisses() {
        stubOffice("Mumbai-I", "001");
        when(bankBranchRepository.findByBankIdAndPincode(anyLong(), anyString())).thenReturn(List.of());
        when(bankBranchRepository.findByBankCodeIgnoreCaseAndPincode("HDFC", "400001"))
                .thenReturn(List.of(branch("Mumbai", "Maharashtra")));

        Complaint complaint = Complaint.builder().bankId(7L).entityPincode("400001").build();
        complaint.setEntityCode("HDFC");

        assertThat(service.resolve(complaint, "RBIO").source()).isEqualTo("BANK_BRANCH");
    }

    @Test
    void fallsBackToThePostalPincodeTableWhenNoBranchRowExists() {
        stubOffice("Bengaluru", "003");
        when(bankBranchRepository.findByBankIdAndPincode(anyLong(), anyString())).thenReturn(List.of());
        when(pincodeRepository.findByPincode("560001")).thenReturn(List.of(
                Pincode.builder().pincode("560001").district("Bengaluru Urban").state("Karnataka").build()));

        Complaint complaint = Complaint.builder().bankId(7L).entityPincode("560001").build();

        OfficeResolution resolution = service.resolve(complaint, "RBIO");

        assertThat(resolution.source()).isEqualTo("ENTITY_PINCODE");
        assertThat(resolution.district()).isEqualTo("Bengaluru Urban");
    }

    @Test
    void usesTheSuppliedEntityLocationWhenNoPincodeIsAvailable() {
        stubOffice("Mumbai-I", "001");

        Complaint complaint = Complaint.builder()
                .entityState("Maharashtra")
                .entityDistrict("Thane")
                .complainantState("Kerala")
                .build();

        OfficeResolution resolution = service.resolve(complaint, "RBIO");

        assertThat(resolution.source()).isEqualTo("ENTITY_LOCATION");
        assertThat(resolution.district()).isEqualTo("Thane");
        verify(complaintNumberGenerator).resolveOfficeName("RBIO", "Maharashtra", "Thane");
    }

    @Test
    void fallsBackToTheComplainantPincodeWhenNothingIsKnownAboutTheEntity() {
        stubOffice("Thiruvananthapuram", "022");
        when(pincodeRepository.findByPincode("695001")).thenReturn(List.of(
                Pincode.builder().pincode("695001").district("Thiruvananthapuram").state("Kerala").build()));

        Complaint complaint = Complaint.builder().complainantPincode("695001").build();

        assertThat(service.resolve(complaint, "RBIO").source()).isEqualTo("COMPLAINANT_PINCODE");
    }

    @Test
    void fallsBackToTheComplainantLocationWhenThePincodeIsUnknown() {
        stubOffice("Thiruvananthapuram", "022");
        when(pincodeRepository.findByPincode("999999")).thenReturn(List.of());

        Complaint complaint = Complaint.builder()
                .complainantPincode("999999")
                .complainantState("Kerala")
                .complainantDistrict("Kollam")
                .build();

        OfficeResolution resolution = service.resolve(complaint, "RBIO");

        assertThat(resolution.source()).isEqualTo("COMPLAINANT_LOCATION");
        verify(complaintNumberGenerator).resolveOfficeName("RBIO", "Kerala", "Kollam");
    }

    @Test
    void reportsDefaultWhenNoGeographyIsAvailableAtAll() {
        stubOffice("New Delhi I", "014");

        OfficeResolution resolution = service.resolve(Complaint.builder().build(), "RBIO");

        assertThat(resolution.source()).isEqualTo("DEFAULT");
        assertThat(resolution.officeName()).isEqualTo("New Delhi I");
        verify(complaintNumberGenerator).resolveOfficeName("RBIO", null, null);
    }

    @Test
    void treatsBlankGeographyAsAbsentRatherThanAsAValue() {
        stubOffice("New Delhi I", "014");

        Complaint complaint = Complaint.builder()
                .entityPincode("   ")
                .entityState("")
                .complainantPincode("  ")
                .complainantState("   ")
                .build();

        assertThat(service.resolve(complaint, "RBIO").source()).isEqualTo("DEFAULT");
        verify(bankBranchRepository, never()).findByBankIdAndPincode(anyLong(), anyString());
    }

    @Test
    void skipsABranchRowThatCarriesNoStateSoItCannotResolveToAnOffice() {
        stubOffice("Bengaluru", "003");
        when(bankBranchRepository.findByBankIdAndPincode(7L, "560001"))
                .thenReturn(List.of(branch("Bengaluru Urban", null)));
        when(pincodeRepository.findByPincode("560001")).thenReturn(List.of(
                Pincode.builder().pincode("560001").district("Bengaluru Urban").state("Karnataka").build()));

        Complaint complaint = Complaint.builder().bankId(7L).entityPincode("560001").build();

        assertThat(service.resolve(complaint, "RBIO").source()).isEqualTo("ENTITY_PINCODE");
    }

    @Test
    void returnsTheOfficeNameEvenWhenNoCodeIsRegisteredForIt() {
        when(complaintNumberGenerator.resolveOfficeName(anyString(), any(), any())).thenReturn("Mumbai-I");
        when(officeCodeRepository.findByOfficeNameAndIsActiveTrue(eq("Mumbai-I"))).thenReturn(Optional.empty());

        OfficeResolution resolution = service.resolve(
                Complaint.builder().entityState("Maharashtra").build(), "RBIO");

        assertThat(resolution.officeName()).isEqualTo("Mumbai-I");
        assertThat(resolution.officeCode()).isNull();
    }

    @Test
    void takesTheFirstRowWhenOnePincodeSpansSeveralPostOffices() {
        stubOffice("Bengaluru", "003");
        when(bankBranchRepository.findByBankIdAndPincode(anyLong(), anyString())).thenReturn(List.of());
        when(pincodeRepository.findByPincode("560001")).thenReturn(List.of(
                Pincode.builder().officeName("Bangalore GPO").district("Bengaluru Urban").state("Karnataka").build(),
                Pincode.builder().officeName("Bangalore Bazaar").district("Bengaluru Urban").state("Karnataka").build()));

        Complaint complaint = Complaint.builder().bankId(7L).entityPincode("560001").build();

        assertThat(service.resolve(complaint, "RBIO").district()).isEqualTo("Bengaluru Urban");
    }
}
