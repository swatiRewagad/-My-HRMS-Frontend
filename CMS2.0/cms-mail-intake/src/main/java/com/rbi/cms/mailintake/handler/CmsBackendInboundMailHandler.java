package com.rbi.cms.mailintake.handler;

import com.rbi.cms.mailintake.spi.HandlerResult;
import com.rbi.cms.mailintake.spi.InboundMailHandler;
import com.rbi.cms.mailintake.spi.NormalisedInboundMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class CmsBackendInboundMailHandler implements InboundMailHandler {

    private final RestTemplate restTemplate;
    private final String syndicationUrl;

    @Override
    public HandlerResult handle(NormalisedInboundMail mail) {
        log.info("Dispatching inbound email id={} from={} subject='{}' to cms-backend syndication",
                mail.inboundEmailId(), mail.originalFrom(), mail.originalSubject());

        Map<String, Object> payload = new HashMap<>();
        payload.put("senderEmail", mail.originalFrom());
        payload.put("subject", mail.originalSubject());
        payload.put("body", Optional.ofNullable(mail.textBody()).orElse(mail.htmlBody()));
        payload.put("messageId", "mail-intake-" + mail.inboundEmailId());
        payload.put("toRecipients", mail.originalTo() != null ? String.join(",", mail.originalTo()) : "");
        payload.put("ccRecipients", "");
        payload.put("bccRecipients", "");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(syndicationUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object data = response.getBody().get("data");
                String draftId = extractDraftId(data);
                log.info("Email id={} successfully syndicated, draftId={}", mail.inboundEmailId(), draftId);
                return HandlerResult.success(draftId);
            }

            return HandlerResult.retryableFailure("Unexpected response status: " + response.getStatusCode());

        } catch (HttpClientErrorException e) {
            log.error("Client error syndicating email id={}: {} {}", mail.inboundEmailId(), e.getStatusCode(), e.getResponseBodyAsString());
            return HandlerResult.permanentFailure("HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.warn("Connection error syndicating email id={}: {}", mail.inboundEmailId(), e.getMessage());
            return HandlerResult.retryableFailure("Connection failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error syndicating email id={}", mail.inboundEmailId(), e);
            return HandlerResult.retryableFailure(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String extractDraftId(Object data) {
        if (data instanceof Map) {
            Object id = ((Map<String, Object>) data).get("id");
            if (id != null) return id.toString();
            Object draftId = ((Map<String, Object>) data).get("draftId");
            if (draftId != null) return draftId.toString();
        }
        return data != null ? data.toString() : "unknown";
    }
}
