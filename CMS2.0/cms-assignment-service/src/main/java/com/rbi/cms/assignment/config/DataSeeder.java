package com.rbi.cms.assignment.config;

import com.rbi.cms.assignment.domain.entity.AsgnAttribute;
import com.rbi.cms.assignment.domain.enums.DataType;
import com.rbi.cms.assignment.domain.enums.ValueSource;
import com.rbi.cms.assignment.persistence.repository.AttributeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Profile("dev-local")
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final AttributeRepository attributeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String tenant = "RBI-CMS";
        List<AsgnAttribute> existing = attributeRepository.findByTenantIdAndActiveOrderByDisplayOrder(tenant, true);
        if (!existing.isEmpty()) {
            log.info("Seed data already present ({} attributes), skipping.", existing.size());
            return;
        }

        log.info("Seeding assignment attributes...");

        attributeRepository.saveAll(List.of(
            AsgnAttribute.builder()
                .tenantId(tenant).code("claimAmount").label("Claim Amount")
                .description("Monetary value of the claim in INR")
                .dataType(DataType.MONEY).sourcePath("complaint.claimAmount")
                .required(false).valueSource(ValueSource.FREE_TEXT)
                .caseSensitive(false).piiFlag(false).displayOrder(1).active(true)
                .allowedOperators("GTE,LTE,GT,LT,BETWEEN,EQ,IS_NULL,IS_NOT_NULL")
                .build(),
            AsgnAttribute.builder()
                .tenantId(tenant).code("complaintCategory").label("Category")
                .description("Complaint category code")
                .dataType(DataType.ENUM).sourcePath("complaint.category")
                .required(false).valueSource(ValueSource.STATIC_LIST)
                .caseSensitive(false).piiFlag(false).displayOrder(2).active(true)
                .allowedOperators("EQ,NEQ,IN,NOT_IN,IS_NULL,IS_NOT_NULL")
                .build(),
            AsgnAttribute.builder()
                .tenantId(tenant).code("state").label("State")
                .description("Complainant state code")
                .dataType(DataType.ENUM).sourcePath("complaint.state")
                .required(false).valueSource(ValueSource.STATIC_LIST)
                .caseSensitive(false).piiFlag(false).displayOrder(3).active(true)
                .allowedOperators("EQ,NEQ,IN,NOT_IN,IS_NULL,IS_NOT_NULL")
                .build(),
            AsgnAttribute.builder()
                .tenantId(tenant).code("channel").label("Channel")
                .description("Intake channel")
                .dataType(DataType.ENUM).sourcePath("complaint.channel")
                .required(false).valueSource(ValueSource.STATIC_LIST)
                .caseSensitive(false).piiFlag(false).displayOrder(4).active(true)
                .allowedOperators("EQ,NEQ,IN,NOT_IN,IS_NULL,IS_NOT_NULL")
                .build(),
            AsgnAttribute.builder()
                .tenantId(tenant).code("regulatedEntityType").label("RE Type")
                .description("Regulated entity type")
                .dataType(DataType.ENUM).sourcePath("complaint.regulatedEntityType")
                .required(false).valueSource(ValueSource.STATIC_LIST)
                .caseSensitive(false).piiFlag(false).displayOrder(5).active(true)
                .allowedOperators("EQ,NEQ,IN,NOT_IN,IS_NULL,IS_NOT_NULL")
                .build(),
            AsgnAttribute.builder()
                .tenantId(tenant).code("escalationLevel").label("Escalation")
                .description("Current escalation level")
                .dataType(DataType.NUMBER).sourcePath("complaint.escalationLevel")
                .required(false).valueSource(ValueSource.FREE_TEXT)
                .caseSensitive(false).piiFlag(false).displayOrder(6).active(true)
                .allowedOperators("EQ,GTE,LTE,GT,LT")
                .build(),
            AsgnAttribute.builder()
                .tenantId(tenant).code("isRepeatComplaint").label("Repeat?")
                .description("Whether complainant has prior complaints")
                .dataType(DataType.BOOLEAN).sourcePath("complaint.isRepeat")
                .required(false).valueSource(ValueSource.FREE_TEXT)
                .caseSensitive(false).piiFlag(false).displayOrder(7).active(true)
                .allowedOperators("IS_TRUE,IS_FALSE")
                .build()
        ));

        log.info("Seeded 7 assignment attributes.");
    }
}
