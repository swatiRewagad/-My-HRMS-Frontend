package com.rbi.cms.mailintake.spi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InboundMailHandlerConfig {

    @Bean
    @ConditionalOnMissingBean(InboundMailHandler.class)
    public InboundMailHandler inboundMailHandler() {
        return new LoggingInboundMailHandler();
    }
}
