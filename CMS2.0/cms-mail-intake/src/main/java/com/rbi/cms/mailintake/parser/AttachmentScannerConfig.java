package com.rbi.cms.mailintake.parser;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AttachmentScannerConfig {

    @Bean
    @ConditionalOnMissingBean(AttachmentScanner.class)
    AttachmentScanner attachmentScanner() {
        return new NoOpAttachmentScanner();
    }
}
