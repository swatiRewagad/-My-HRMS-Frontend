package com.rbi.cms.sla.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@Profile("dev-local")
@Primary
public class DevLocalSlaMonitorService extends SlaMonitorService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String INGESTION_BASE = "http://localhost:8082/cms-ingestion/api/v1/sla";

    public DevLocalSlaMonitorService() {
        super(null, null);
    }

    @Override
    public int detectAndEscalateBreaches() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    INGESTION_BASE + "/check", HttpMethod.POST, null, String.class);
            log.info("[DEV-LOCAL SLA] Check result: {}", response.getBody());
            return 0;
        } catch (Exception e) {
            log.warn("[DEV-LOCAL SLA] Could not reach ingestion service: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public int detectWarnings() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    INGESTION_BASE + "/breached", String.class);
            log.info("[DEV-LOCAL SLA] Breached complaints: {}", response.getBody());
            return 0;
        } catch (Exception e) {
            log.warn("[DEV-LOCAL SLA] Could not reach ingestion service: {}", e.getMessage());
            return 0;
        }
    }
}
