package com.hrms.ecm.config;

import com.hrms.ecm.entity.*;
import com.hrms.ecm.repository.*;
import java.util.Arrays;
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
    private final ProjectRepository projectRepo;
    private final ProjectUploadConfigRepository uploadConfigRepo;
    private final DocumentTypeConfigRepository docTypeRepo;
    private final InvoiceFieldConfigRepository fieldConfigRepo;

    @Override
    public void run(String... args) {
        if (projectRepo.count() == 0) {
            Long adminId = userRepo.findAll().stream().findFirst().map(u -> u.getId()).orElse(1L);
            seedProjectsAndConfig(adminId);
        }

        if (userRepo.count() > 0) return;

        EcmUser superAdmin = userRepo.save(EcmUser.builder().username("super.admin").displayName("Super Admin").email("super.admin@rbi.org.in").role("SUPER_ADMIN").initials("SA").build());
        EcmUser admin = userRepo.save(EcmUser.builder().username("rahul.jain").displayName("Rahul Jain").email("rahul.jain@rbi.org.in").role("ADMIN").initials("RJ").build());
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

        // ───── Seed Projects & Config ─────
        if (projectRepo.count() == 0) {
            seedProjectsAndConfig(superAdmin.getId());
        }
    }

    private void seedProjectsAndConfig(Long superAdminId) {
        Project ips = projectRepo.save(Project.builder().code("IPS").name("Integrated Payment System").description("Payment processing and settlement system").createdBy(superAdminId).build());
        Project dicgc = projectRepo.save(Project.builder().code("DICGC").name("Deposit Insurance & Credit Guarantee").description("Insurance and credit guarantee management").createdBy(superAdminId).build());
        Project cms = projectRepo.save(Project.builder().code("CMS").name("Complaint Management System").description("Centralized complaint handling and resolution").createdBy(superAdminId).build());

        // Upload configs
        uploadConfigRepo.save(ProjectUploadConfig.builder().projectId(ips.getId()).maxFileSizeBytes(50L * 1024 * 1024).totalAllocatedStorageBytes(10L * 1024 * 1024 * 1024).allowedContentTypes("application/pdf,image/png,image/jpeg,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").uploadBasePath("C:/ecm-storage/IPS").build());
        uploadConfigRepo.save(ProjectUploadConfig.builder().projectId(dicgc.getId()).maxFileSizeBytes(25L * 1024 * 1024).totalAllocatedStorageBytes(5L * 1024 * 1024 * 1024).allowedContentTypes("application/pdf,image/png,image/jpeg").uploadBasePath("C:/ecm-storage/DICGC").build());
        uploadConfigRepo.save(ProjectUploadConfig.builder().projectId(cms.getId()).maxFileSizeBytes(10L * 1024 * 1024).totalAllocatedStorageBytes(2L * 1024 * 1024 * 1024).allowedContentTypes("application/pdf,image/png,image/jpeg,application/vnd.openxmlformats-officedocument.wordprocessingml.document").uploadBasePath("C:/ecm-storage/CMS").build());

        // Doc types for IPS — Invoice (extraction enabled) + Text (no extraction)
        DocumentTypeConfig ipsInvoice = docTypeRepo.save(DocumentTypeConfig.builder().projectId(ips.getId()).typeName("Invoice").typeCode("INVOICE").description("Vendor invoices for payment processing").extractionEnabled(true).build());
        docTypeRepo.save(DocumentTypeConfig.builder().projectId(ips.getId()).typeName("Text").typeCode("TEXT").description("General text documents").extractionEnabled(false).build());

        // Doc types for DICGC — Invoice + Text
        DocumentTypeConfig dicgcInvoice = docTypeRepo.save(DocumentTypeConfig.builder().projectId(dicgc.getId()).typeName("Invoice").typeCode("INVOICE").description("Insurance premium invoices").extractionEnabled(true).build());
        docTypeRepo.save(DocumentTypeConfig.builder().projectId(dicgc.getId()).typeName("Text").typeCode("TEXT").description("General text documents").extractionEnabled(false).build());

        // Doc types for CMS — Invoice + Text
        docTypeRepo.save(DocumentTypeConfig.builder().projectId(cms.getId()).typeName("Invoice").typeCode("INVOICE").description("Invoices and billing documents").extractionEnabled(true).build());
        docTypeRepo.save(DocumentTypeConfig.builder().projectId(cms.getId()).typeName("Text").typeCode("TEXT").description("General text documents").extractionEnabled(false).build());

        // Invoice extraction fields for IPS Invoice
        seedInvoiceFields(ipsInvoice.getId(), Arrays.asList(
            new String[]{"Invoice Number", "invoiceNumber", "text", "true", "header"},
            new String[]{"Invoice Date", "invoiceDate", "date", "true", "header"},
            new String[]{"Due Date", "dueDate", "date", "false", "header"},
            new String[]{"Vendor Name", "vendorName", "text", "true", "header"},
            new String[]{"Vendor Address", "vendorAddress", "text", "false", "header"},
            new String[]{"Customer Name", "customerName", "text", "false", "header"},
            new String[]{"Subtotal", "subtotal", "currency", "false", "amount"},
            new String[]{"Tax / GST", "tax", "currency", "false", "amount"},
            new String[]{"Total Amount", "totalAmount", "currency", "true", "amount"},
            new String[]{"Currency", "currency", "text", "false", "amount"},
            new String[]{"Description", "description", "text", "true", "lineItem"},
            new String[]{"Quantity", "quantity", "number", "true", "lineItem"},
            new String[]{"Unit Price", "unitPrice", "currency", "true", "lineItem"},
            new String[]{"Amount", "amount", "currency", "true", "lineItem"},
            new String[]{"HSN Code", "hsnCode", "text", "false", "lineItem"},
            new String[]{"Tax Rate", "taxRate", "text", "false", "lineItem"}
        ));

        // Invoice extraction fields for DICGC Invoice (fewer fields)
        seedInvoiceFields(dicgcInvoice.getId(), Arrays.asList(
            new String[]{"Invoice Number", "invoiceNumber", "text", "true", "header"},
            new String[]{"Invoice Date", "invoiceDate", "date", "true", "header"},
            new String[]{"Insurer Name", "vendorName", "text", "true", "header"},
            new String[]{"Policy Number", "policyNumber", "text", "true", "header"},
            new String[]{"Premium Amount", "totalAmount", "currency", "true", "amount"},
            new String[]{"Currency", "currency", "text", "false", "amount"},
            new String[]{"Description", "description", "text", "true", "lineItem"},
            new String[]{"Amount", "amount", "currency", "true", "lineItem"}
        ));
    }

    private void seedInvoiceFields(Long docTypeId, List<String[]> fields) {
        for (int i = 0; i < fields.size(); i++) {
            String[] f = fields.get(i);
            fieldConfigRepo.save(InvoiceFieldConfig.builder()
                    .docTypeConfigId(docTypeId)
                    .fieldName(f[0]).fieldKey(f[1]).fieldType(f[2])
                    .required("true".equals(f[3]))
                    .displayOrder(i)
                    .fieldCategory(f[4])
                    .build());
        }
    }
}
