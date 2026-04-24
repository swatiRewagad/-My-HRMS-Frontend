package com.hrms.realmaiconfig.config;

import com.hrms.realmaiconfig.entity.*;
import com.hrms.realmaiconfig.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RealmRepository realmRepository;
    private final RealmConfigurationRepository configRepository;
    private final ConfiguredServiceRepository serviceRepository;
    private final RegisteredServiceRepository registeredServiceRepository;
    private final ActivityLogRepository activityLogRepository;

    @Override
    public void run(String... args) {
        if (realmRepository.count() > 0) return;

        seedRealms();
        seedRealmConfigurations();
        seedRegisteredServices();
        seedActivityLog();
    }

    private void seedRealms() {
        realmRepository.saveAll(List.of(
                Realm.builder().id("ngcb").name("next-generation-core-banking").displayName("NGCB - GPX (Government Payment Exchange)").initials("NGCB").description("Next Generation core banking platform").realmId("NGCB-001").owner("Anita Kumar").ownerEmail("anita.kumar@rbi.org.in").department("Core Banking").type("Production").userCount(450).status("active").createdAt(LocalDateTime.of(2024, 2, 10, 0, 0)).syncedAt(LocalDateTime.of(2026, 3, 27, 8, 0)).build(),
                Realm.builder().id("central-fin-literacy").name("central-financial-literacy").displayName("Central for Financial Literacy").initials("CFL").description("Central realm for financial literacy programs").realmId("CFL-001").owner("Rahul Jain").ownerEmail("rahul.jain@rbi.org.in").department("Financial Education").type("Production").userCount(1240).status("active").createdAt(LocalDateTime.of(2023, 4, 15, 0, 0)).syncedAt(LocalDateTime.of(2024, 3, 26, 9, 15)).build(),
                Realm.builder().id("pdm").name("public-debt-management").displayName("Public Debt Management").initials("PDM").description("Public debt management operations and reporting").realmId("PDM-001").owner("Vikram Singh").ownerEmail("vikram.singh@rbi.org.in").department("Debt Management").type("Production").userCount(238).status("active").createdAt(LocalDateTime.of(2023, 6, 10, 0, 0)).syncedAt(LocalDateTime.of(2026, 3, 27, 8, 0)).build(),
                Realm.builder().id("ips").name("invoice-processing-system").displayName("Invoice Processing System").initials("IPS").description("Automated invoice processing and approval workflows").realmId("IPS-001").owner("Wg. Cdr. Amit Roy").ownerEmail("amit.roy@rbi.org.in").department("Finance Operations").type("Production").userCount(192).status("active").createdAt(LocalDateTime.of(2023, 9, 5, 0, 0)).syncedAt(LocalDateTime.of(2026, 3, 27, 8, 0)).build()
        ));
    }

    private void seedRealmConfigurations() {
        RealmConfiguration ngcbConfig = configRepository.save(
                RealmConfiguration.builder().realmId("NGCB-001").mode("non-app-designer").platformVersion("v3.2.1").deploymentType("independent").configuredBy("Anita Kumar").configuredAt(LocalDateTime.now().minusHours(2)).isActive(true).build()
        );
        serviceRepository.saveAll(List.of(
                ConfiguredService.builder().configurationId(ngcbConfig.getId()).serviceId("kavach").serviceLabel("Kavach").serviceGroup("basic").build(),
                ConfiguredService.builder().configurationId(ngcbConfig.getId()).serviceId("face-detection").serviceLabel("Face / Object Detection").serviceGroup("ai").build(),
                ConfiguredService.builder().configurationId(ngcbConfig.getId()).serviceId("audit-trail").serviceLabel("Audit Trail").serviceGroup("basic").build()
        ));

        RealmConfiguration cflConfig = configRepository.save(
                RealmConfiguration.builder().realmId("CFL-001").mode("app-designer").platformVersion("v3.1.0").deploymentType("dependent").configuredBy("Rahul Jain").configuredAt(LocalDateTime.now().minusDays(1)).isActive(true).build()
        );
        serviceRepository.saveAll(List.of(
                ConfiguredService.builder().configurationId(cflConfig.getId()).serviceId("kavach").serviceLabel("Kavach").serviceGroup("basic").build(),
                ConfiguredService.builder().configurationId(cflConfig.getId()).serviceId("notifications").serviceLabel("Notifications").serviceGroup("basic").build()
        ));

        RealmConfiguration pdmConfig = configRepository.save(
                RealmConfiguration.builder().realmId("PDM-001").mode("non-app-designer").platformVersion("v3.2.0").deploymentType("independent").configuredBy("Vikram Singh").configuredAt(LocalDateTime.now().minusDays(3)).isActive(true).build()
        );
        serviceRepository.saveAll(List.of(
                ConfiguredService.builder().configurationId(pdmConfig.getId()).serviceId("audit-trail").serviceLabel("Audit Trail").serviceGroup("basic").build(),
                ConfiguredService.builder().configurationId(pdmConfig.getId()).serviceId("mdms").serviceLabel("MDMS").serviceGroup("basic").build()
        ));

        RealmConfiguration ipsConfig = configRepository.save(
                RealmConfiguration.builder().realmId("IPS-001").mode("non-app-designer").platformVersion("v3.0.5").deploymentType("independent").configuredBy("Wg. Cdr. Amit Roy").configuredAt(LocalDateTime.now().minusDays(5)).isActive(true).build()
        );
        serviceRepository.saveAll(List.of(
                ConfiguredService.builder().configurationId(ipsConfig.getId()).serviceId("document-ai").serviceLabel("Document AI").serviceGroup("ai").build()
        ));
    }

    private void seedRegisteredServices() {
        registeredServiceRepository.saveAll(List.of(
                RegisteredService.builder().name("Optical Character Recognition").slug("ocr-service").baseUrl("https://ocr.internal.kavach.gov.in/api/v2").version("v2.0").description("AI-powered OCR for document digitization").category("AI & ML").authType("API Key").healthCheckEndpoint("/health").ownerName("Priya Nair").ownerEmail("priya.nair@rbi.org.in").tags("ai,ocr,document").status("Active").registeredAt(LocalDateTime.now().minusDays(30)).build(),
                RegisteredService.builder().name("Face & Object Detection").slug("face-detection-svc").baseUrl("https://vision.internal.kavach.gov.in/api/v1").version("v1.0").description("Real-time face and object detection engine").category("AI & ML").authType("Bearer Token").healthCheckEndpoint("/health").ownerName("Amit Roy").ownerEmail("amit.roy@rbi.org.in").tags("ai,vision,detection").status("Active").registeredAt(LocalDateTime.now().minusDays(25)).build(),
                RegisteredService.builder().name("Anomaly Detection Engine").slug("anomaly-detect").baseUrl("https://analytics.internal.kavach.gov.in/anomaly/v3").version("v3.0").description("Statistical and ML-based anomaly flagging").category("Data & Analytics").authType("OAuth 2.0").healthCheckEndpoint("/health").ownerName("Vikram Singh").ownerEmail("vikram.singh@rbi.org.in").tags("analytics,anomaly,ml").status("Active").registeredAt(LocalDateTime.now().minusDays(20)).build(),
                RegisteredService.builder().name("Notification & Messaging Gateway").slug("notif-gateway").baseUrl("https://notify.internal.kavach.gov.in/v2").version("v2.0").description("Multi-channel notification service").category("Communication").authType("API Key").healthCheckEndpoint("/health").ownerName("Neha Gupta").ownerEmail("neha.gupta@rbi.org.in").tags("notification,email,sms").status("Active").registeredAt(LocalDateTime.now().minusDays(15)).build(),
                RegisteredService.builder().name("Secure Document Vault").slug("doc-vault").baseUrl("https://storage.internal.kavach.gov.in/vault/v1").version("v1.0").description("Encrypted document storage and retrieval").category("Storage").authType("Bearer Token").healthCheckEndpoint("/health").ownerName("Anita Kumar").ownerEmail("anita.kumar@rbi.org.in").tags("storage,document,vault").status("Inactive").registeredAt(LocalDateTime.now().minusDays(10)).build(),
                RegisteredService.builder().name("Identity Token Issuer").slug("iti-service").baseUrl("https://identity.kavach.gov.in/token/v4").version("v4.0").description("Identity token issuance and verification").category("Identity").authType("OAuth 2.0").healthCheckEndpoint("/health").ownerName("Rahul Jain").ownerEmail("rahul.jain@rbi.org.in").tags("identity,auth,token").status("Deprecated").registeredAt(LocalDateTime.now().minusDays(60)).build()
        ));
    }

    private void seedActivityLog() {
        activityLogRepository.saveAll(List.of(
                ActivityLog.builder().action("Realm configured").entityType("REALM").entityName("NGCB - GPX (Government Payment Exchange)").performedBy("Anita Kumar").performedAt(LocalDateTime.now().minusHours(2)).build(),
                ActivityLog.builder().action("Service registered").entityType("SVC").entityName("Auth Gateway v2.4.1").performedBy("System").performedAt(LocalDateTime.now().minusHours(5)).build(),
                ActivityLog.builder().action("Deployment type changed").entityType("REALM").entityName("Central for Financial Literacy").performedBy("Rahul Jain").performedAt(LocalDateTime.now().minusDays(1)).build(),
                ActivityLog.builder().action("Service deprecated").entityType("SVC").entityName("Notification Bus").performedBy("System").performedAt(LocalDateTime.now().minusDays(2)).build(),
                ActivityLog.builder().action("Version updated").entityType("REALM").entityName("Public Debt Management").performedBy("Vikram Singh").performedAt(LocalDateTime.now().minusDays(3)).build(),
                ActivityLog.builder().action("Service deregistered").entityType("SVC").entityName("Legacy Auth v1.0").performedBy("System").performedAt(LocalDateTime.now().minusDays(5)).build()
        ));
    }
}
