package com.hrms.ecm.config;

import com.hrms.ecm.entity.*;
import com.hrms.ecm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final EcmUserRepository userRepo;
    private final FolderRepository folderRepo;
    private final FileRepository fileRepo;
    private final FolderAccessRepository accessRepo;
    private final FileShareRepository shareRepo;
    private final ActivityLogRepository activityRepo;

    @Override
    public void run(String... args) {
        if (userRepo.count() > 0) return;

        EcmUser admin = userRepo.save(EcmUser.builder().username("rahul.jain").displayName("Rahul Jain").email("rahul.jain@rbi.org.in").role("Admin").initials("RJ").build());
        EcmUser anita = userRepo.save(EcmUser.builder().username("anita.kumar").displayName("Anita Kumar").email("anita.kumar@rbi.org.in").role("Manager").initials("AK").build());
        EcmUser vikram = userRepo.save(EcmUser.builder().username("vikram.singh").displayName("Vikram Singh").email("vikram.singh@rbi.org.in").role("User").initials("VS").build());
        EcmUser priya = userRepo.save(EcmUser.builder().username("priya.nair").displayName("Priya Nair").email("priya.nair@rbi.org.in").role("User").initials("PN").build());
        EcmUser amit = userRepo.save(EcmUser.builder().username("amit.roy").displayName("Amit Roy").email("amit.roy@rbi.org.in").role("User").initials("AR").build());

        // Public folders
        Folder policies = folderRepo.save(Folder.builder().name("Policies & Guidelines").visibility("public").description("Official RBI policies and compliance guidelines").parentId(null).ownerId(admin.getId()).path("/Policies & Guidelines").build());
        Folder templates = folderRepo.save(Folder.builder().name("Templates").visibility("public").description("Shared document templates for all departments").parentId(null).ownerId(admin.getId()).path("/Templates").build());
        Folder training = folderRepo.save(Folder.builder().name("Training Materials").visibility("public").description("Onboarding and training resources").parentId(null).ownerId(anita.getId()).path("/Training Materials").build());

        // Private folders
        Folder hrConfidential = folderRepo.save(Folder.builder().name("HR Confidential").visibility("private").description("Confidential HR documents and employee records").parentId(null).ownerId(anita.getId()).path("/HR Confidential").build());
        Folder financeReports = folderRepo.save(Folder.builder().name("Finance Reports").visibility("private").description("Internal financial reports and audit documents").parentId(null).ownerId(vikram.getId()).path("/Finance Reports").build());
        Folder projectDocs = folderRepo.save(Folder.builder().name("Project Documents").visibility("private").description("Project-specific documentation and deliverables").parentId(null).ownerId(priya.getId()).path("/Project Documents").build());

        // Subfolders
        Folder q1Reports = folderRepo.save(Folder.builder().name("Q1 2026 Reports").visibility("private").description("First quarter financial reports").parentId(financeReports.getId()).ownerId(vikram.getId()).path("/Finance Reports/Q1 2026 Reports").build());
        Folder hrLetters = folderRepo.save(Folder.builder().name("Offer Letters").visibility("private").description("Employee offer letters").parentId(hrConfidential.getId()).ownerId(anita.getId()).path("/HR Confidential/Offer Letters").build());

        // Access rights
        accessRepo.save(FolderAccess.builder().folderId(hrConfidential.getId()).userId(admin.getId()).permission("read-write").build());
        accessRepo.save(FolderAccess.builder().folderId(financeReports.getId()).userId(admin.getId()).permission("read").build());
        accessRepo.save(FolderAccess.builder().folderId(financeReports.getId()).userId(anita.getId()).permission("read").build());
        accessRepo.save(FolderAccess.builder().folderId(projectDocs.getId()).userId(vikram.getId()).permission("read-write").build());
        accessRepo.save(FolderAccess.builder().folderId(projectDocs.getId()).userId(amit.getId()).permission("read").build());

        // Seed files
        fileRepo.saveAll(List.of(
            FileEntity.builder().name("f1").originalName("Annual_Compliance_Policy_2026.pdf").contentType("application/pdf").size(2_450_000L).storagePath("seed").folderId(policies.getId()).uploadedBy(admin.getId()).status("active").uploadedAt(LocalDateTime.now().minusDays(5)).build(),
            FileEntity.builder().name("f2").originalName("Data_Privacy_Guidelines.pdf").contentType("application/pdf").size(1_200_000L).storagePath("seed").folderId(policies.getId()).uploadedBy(admin.getId()).status("active").uploadedAt(LocalDateTime.now().minusDays(4)).build(),
            FileEntity.builder().name("f3").originalName("Leave_Application_Template.docx").contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document").size(85_000L).storagePath("seed").folderId(templates.getId()).uploadedBy(anita.getId()).status("active").uploadedAt(LocalDateTime.now().minusDays(10)).build(),
            FileEntity.builder().name("f4").originalName("Expense_Report_Template.xlsx").contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").size(120_000L).storagePath("seed").folderId(templates.getId()).uploadedBy(vikram.getId()).status("active").uploadedAt(LocalDateTime.now().minusDays(8)).build(),
            FileEntity.builder().name("f5").originalName("New_Employee_Onboarding.pptx").contentType("application/vnd.openxmlformats-officedocument.presentationml.presentation").size(5_800_000L).storagePath("seed").folderId(training.getId()).uploadedBy(anita.getId()).status("active").uploadedAt(LocalDateTime.now().minusDays(3)).build(),
            FileEntity.builder().name("f6").originalName("Q1_Revenue_Summary.xlsx").contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").size(340_000L).storagePath("seed").folderId(q1Reports.getId()).uploadedBy(vikram.getId()).status("active").uploadedAt(LocalDateTime.now().minusDays(2)).build(),
            FileEntity.builder().name("f7").originalName("Salary_Structure_2026.xlsx").contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").size(210_000L).storagePath("seed").folderId(hrConfidential.getId()).uploadedBy(anita.getId()).status("active").uploadedAt(LocalDateTime.now().minusDays(1)).build(),
            FileEntity.builder().name("f8").originalName("Architecture_Diagram.png").contentType("image/png").size(890_000L).storagePath("seed").folderId(projectDocs.getId()).uploadedBy(priya.getId()).status("active").uploadedAt(LocalDateTime.now().minusHours(6)).build(),
            FileEntity.builder().name("f9").originalName("Security_Audit_Report.pdf").contentType("application/pdf").size(3_100_000L).storagePath("seed").folderId(financeReports.getId()).uploadedBy(vikram.getId()).status("active").uploadedAt(LocalDateTime.now().minusHours(3)).build(),
            FileEntity.builder().name("f10").originalName("Team_Photo_2026.jpg").contentType("image/jpeg").size(4_200_000L).storagePath("seed").folderId(training.getId()).uploadedBy(amit.getId()).status("active").uploadedAt(LocalDateTime.now().minusHours(1)).build()
        ));

        // Shares
        shareRepo.saveAll(List.of(
            FileShare.builder().fileId(1L).sharedBy(admin.getId()).sharedWith(anita.getId()).shareType("user").permission("view").build(),
            FileShare.builder().fileId(1L).sharedBy(admin.getId()).sharedWith(vikram.getId()).shareType("user").permission("view").build(),
            FileShare.builder().fileId(6L).sharedBy(vikram.getId()).sharedWith(admin.getId()).shareType("user").permission("edit").build(),
            FileShare.builder().fileId(5L).sharedBy(anita.getId()).sharedWith(null).shareType("link").shareToken("abc123def456gh78").permission("view").expiresAt(LocalDateTime.now().plusDays(7)).build()
        ));

        // Activity log
        activityRepo.saveAll(List.of(
            ActivityLog.builder().action("File uploaded").entityType("FILE").entityName("Team_Photo_2026.jpg").userId(amit.getId()).userName("Amit Roy").performedAt(LocalDateTime.now().minusHours(1)).build(),
            ActivityLog.builder().action("File uploaded").entityType("FILE").entityName("Security_Audit_Report.pdf").userId(vikram.getId()).userName("Vikram Singh").performedAt(LocalDateTime.now().minusHours(3)).build(),
            ActivityLog.builder().action("File shared").entityType("FILE").entityName("Q1_Revenue_Summary.xlsx").userId(vikram.getId()).userName("Vikram Singh").performedAt(LocalDateTime.now().minusHours(5)).build(),
            ActivityLog.builder().action("Folder created").entityType("FOLDER").entityName("Q1 2026 Reports").userId(vikram.getId()).userName("Vikram Singh").performedAt(LocalDateTime.now().minusDays(1)).build(),
            ActivityLog.builder().action("Access granted").entityType("FOLDER").entityName("Project Documents").userId(admin.getId()).userName("Rahul Jain").performedAt(LocalDateTime.now().minusDays(2)).build(),
            ActivityLog.builder().action("File uploaded").entityType("FILE").entityName("Annual_Compliance_Policy_2026.pdf").userId(admin.getId()).userName("Rahul Jain").performedAt(LocalDateTime.now().minusDays(5)).build(),
            ActivityLog.builder().action("Folder created").entityType("FOLDER").entityName("HR Confidential").userId(anita.getId()).userName("Anita Kumar").performedAt(LocalDateTime.now().minusDays(7)).build()
        ));
    }
}
