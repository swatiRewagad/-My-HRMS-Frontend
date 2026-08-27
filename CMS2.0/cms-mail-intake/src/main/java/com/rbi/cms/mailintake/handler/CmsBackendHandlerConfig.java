package com.rbi.cms.mailintake.handler;

import com.rbi.cms.mailintake.spi.InboundMailHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class CmsBackendHandlerConfig {

    @Bean
    public RestTemplate mailIntakeRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public InboundMailHandler inboundMailHandler(
            RestTemplate mailIntakeRestTemplate,
            @Value("${cms.mail.intake.backend.syndication-url}") String syndicationUrl) {
        return new CmsBackendInboundMailHandler(mailIntakeRestTemplate, syndicationUrl);
    }
}
