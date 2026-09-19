package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.enums.RoleConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RbioHierarchyServiceTest {

    private final RbioHierarchyService service = new RbioHierarchyService();

    private Complaint rbioComplaint(String status, String assignedRole, String assignedOfficer) {
        Complaint complaint = new Complaint();
        complaint.setComplaintNumber("RBIO-1");
        complaint.setDepartment("RBIO");
        complaint.setStatus(status);
        complaint.setAssignedRole(assignedRole);
        complaint.setAssignedOfficer(assignedOfficer);
        return complaint;
    }

    @Test
    @DisplayName("DO may forward one rung up to the reviewer")
    void allowsSingleRungClimb() {
        Complaint complaint = rbioComplaint("ASSIGNED", RoleConstants.RBIO_DO, "do.user");

        assertThat(service.validateForward(complaint, "REVIEWER", "DO", "do.user",
                ComplaintStatus.SENT_TO_REVIEWER.name())).isEmpty();
    }

    @Test
    @DisplayName("DO cannot skip the reviewer and go straight to the ombudsman")
    void rejectsSkippingARung() {
        Complaint complaint = rbioComplaint("ASSIGNED", RoleConstants.RBIO_DO, "do.user");

        Optional<RbioHierarchyService.Denial> denial = service.validateForward(
                complaint, "OMBUDSMAN", "DO", "do.user", ComplaintStatus.SENT_TO_OMBUDSMAN.name());

        assertThat(denial).isPresent();
        assertThat(denial.get().status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(denial.get().message()).contains(RoleConstants.RBIO_REVIEWER);
    }

    @Test
    @DisplayName("A send-back down the ladder may skip rungs")
    void allowsDownwardSendBack() {
        Complaint complaint = rbioComplaint(ComplaintStatus.SENT_TO_DEPUTY_OMBUDSMAN.name(),
                RoleConstants.RBIO_DEPUTY_OMBUDSMAN, "dy.user");

        assertThat(service.validateForward(complaint, "DEALING_OFFICER", "DEPUTY_OMBUDSMAN", "dy.user",
                "SENT_TO_DO")).isEmpty();
    }

    @Test
    @DisplayName("Forwarding to the role that already holds the complaint is rejected")
    void rejectsSameRoleForward() {
        Complaint complaint = rbioComplaint("ASSIGNED", RoleConstants.RBIO_REVIEWER, "rev.user");

        Optional<RbioHierarchyService.Denial> denial = service.validateForward(
                complaint, "REVIEWER", "REVIEWER", "rev.user", ComplaintStatus.SENT_TO_REVIEWER.name());

        assertThat(denial).isPresent();
        assertThat(denial.get().status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("A transition that would not change the status is rejected")
    void rejectsSameStatus() {
        Complaint complaint = rbioComplaint(ComplaintStatus.SENT_TO_REVIEWER.name(),
                RoleConstants.RBIO_DO, "do.user");

        Optional<RbioHierarchyService.Denial> denial = service.validateForward(
                complaint, "REVIEWER", "DO", "do.user", ComplaintStatus.SENT_TO_REVIEWER.name());

        assertThat(denial).isPresent();
        assertThat(denial.get().status()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Only the officer holding the complaint may forward it")
    void rejectsForwardByNonHolder() {
        Complaint complaint = rbioComplaint("ASSIGNED", RoleConstants.RBIO_DO, "do.user");

        Optional<RbioHierarchyService.Denial> denial = service.validateForward(
                complaint, "REVIEWER", "DO", "someone.else", ComplaintStatus.SENT_TO_REVIEWER.name());

        assertThat(denial).isPresent();
        assertThat(denial.get().status()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("RBIO_ADMIN may escalate past a rung on someone else's complaint")
    void adminBypassesLadderAndHolderChecks() {
        Complaint complaint = rbioComplaint("ASSIGNED", RoleConstants.RBIO_DO, "do.user");

        assertThat(service.validateForward(complaint, "OMBUDSMAN", RoleConstants.RBIO_ADMIN, "admin.user",
                ComplaintStatus.SENT_TO_OMBUDSMAN.name())).isEmpty();
    }

    @Test
    @DisplayName("Non-RBIO complaints are left to their own department's rules")
    void ignoresNonRbioComplaints() {
        Complaint complaint = rbioComplaint("ASSIGNED", "CEPC_DO", "cepc.user");
        complaint.setDepartment("CEPC");

        assertThat(service.validateForward(complaint, "OMBUDSMAN", "CEPC_DO", "other.user",
                ComplaintStatus.SENT_TO_OMBUDSMAN.name())).isEmpty();
    }

    @Test
    @DisplayName("Closures and out-of-RBIO forwards are not ladder moves")
    void ignoresNonLadderTargets() {
        Complaint complaint = rbioComplaint("ASSIGNED", RoleConstants.RBIO_DO, "do.user");

        assertThat(service.validateForward(complaint, "CLOSE", "DO", "someone.else",
                ComplaintStatus.CLOSED.name())).isEmpty();
        assertThat(service.validateForward(complaint, "OTHER_OFFICE", "DO", "someone.else",
                "PENDING_OFFICE_HEAD_APPROVAL")).isEmpty();
    }

    @Test
    @DisplayName("Admin may swap the person holding a role for absence cover")
    void allowsAdminSameRoleReassign() {
        Complaint complaint = rbioComplaint(ComplaintStatus.SENT_TO_REVIEWER.name(),
                RoleConstants.RBIO_REVIEWER, "rev.onleave");

        assertThat(service.validateAdminReassign(complaint, RoleConstants.RBIO_REVIEWER, "rev.cover")).isEmpty();
    }

    @Test
    @DisplayName("Admin reassignment to the same person in the same role is a no-op")
    void rejectsAdminNoOpReassign() {
        Complaint complaint = rbioComplaint(ComplaintStatus.SENT_TO_REVIEWER.name(),
                RoleConstants.RBIO_REVIEWER, "rev.user");

        Optional<RbioHierarchyService.Denial> denial =
                service.validateAdminReassign(complaint, "REVIEWER", "rev.user");

        assertThat(denial).isPresent();
        assertThat(denial.get().status()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Admin cannot reassign down the hierarchy")
    void rejectsAdminDownwardReassign() {
        Complaint complaint = rbioComplaint(ComplaintStatus.SENT_TO_OMBUDSMAN.name(),
                RoleConstants.RBIO_OMBUDSMAN, "omb.user");

        Optional<RbioHierarchyService.Denial> denial =
                service.validateAdminReassign(complaint, RoleConstants.RBIO_DO, "do.user");

        assertThat(denial).isPresent();
        assertThat(denial.get().status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("Admin cannot reassign an RBIO complaint to another department's role")
    void rejectsAdminCrossDepartmentReassign() {
        Complaint complaint = rbioComplaint("ASSIGNED", RoleConstants.RBIO_DO, "do.user");

        Optional<RbioHierarchyService.Denial> denial =
                service.validateAdminReassign(complaint, "CEPC_REVIEWER", "cepc.user");

        assertThat(denial).isPresent();
        assertThat(denial.get().status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("Role names resolve whether prefixed, bare or ROLE_-qualified")
    void normalizesRoleSpellings() {
        assertThat(service.normalizeRole("do")).isEqualTo(RoleConstants.RBIO_DO);
        assertThat(service.normalizeRole("DEALING_OFFICER")).isEqualTo(RoleConstants.RBIO_DO);
        assertThat(service.normalizeRole("ROLE_RBIO_REVIEWER")).isEqualTo(RoleConstants.RBIO_REVIEWER);
        assertThat(service.normalizeRole("CEPC_DO")).isEqualTo("CEPC_DO");
        assertThat(service.statusForRole("OMBUDSMAN")).isEqualTo(ComplaintStatus.SENT_TO_OMBUDSMAN.name());
    }
}
