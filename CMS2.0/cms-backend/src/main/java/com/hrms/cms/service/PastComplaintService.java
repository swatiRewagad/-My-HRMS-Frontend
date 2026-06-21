package com.hrms.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PastComplaintService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintTimelineRepository timelineRepository;

    @Value("${cms.ocr.groq-api-key:}")
    private String groqApiKey;

    @Value("${cms.ocr.groq-model:meta-llama/llama-4-scout-17b-16e-instruct}")
    private String groqModel;

    private final RestTemplate restTemplate = buildRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // Circuit breaker: skip Groq for 5 minutes after 3 consecutive failures
    private int consecutiveFailures = 0;
    private Instant circuitOpenUntil = Instant.MIN;
    private static final int FAILURE_THRESHOLD = 3;
    private static final int CIRCUIT_COOLDOWN_SECONDS = 300;

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4_000);   // 4 s to establish connection
        factory.setReadTimeout(12_000);     // 12 s to wait for response
        return new RestTemplate(factory);
    }

    public List<Map<String, Object>> findPastComplaints(String email, String phone, String currentComplaintId) {
        Set<Complaint> results = new LinkedHashSet<>();

        if (email != null && !email.isBlank()) {
            results.addAll(complaintRepository.findByComplainantEmailOrderByCreatedAtDesc(email));
        }

        if (phone != null && !phone.isBlank()) {
            List<Complaint> byPhone = complaintRepository.search(phone);
            results.addAll(byPhone);
        }

        return results.stream()
                .filter(c -> !c.getComplaintNumber().equals(currentComplaintId))
                .limit(20)
                .map(this::toSummaryMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> findSimilarCases(String subject, String description, String category, String currentComplaintId) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.warn("Groq API key not configured — using keyword fallback");
            return fallbackKeywordSearch(subject, description, currentComplaintId);
        }

        // Circuit breaker: skip Groq entirely while cooling down
        if (Instant.now().isBefore(circuitOpenUntil)) {
            log.warn("Groq circuit open (cooling down) — using keyword fallback");
            return fallbackKeywordSearch(subject, description, currentComplaintId);
        }

        List<Complaint> candidates = complaintRepository.findAllByOrderByCreatedAtDesc();
        if (candidates.size() > 100) candidates = candidates.subList(0, 100);
        candidates = candidates.stream()
                .filter(c -> !c.getComplaintNumber().equals(currentComplaintId))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) return List.of();

        String candidateList = candidates.stream()
                .map(c -> c.getComplaintNumber() + " | " + c.getSubject() + " | " + c.getStatus())
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are a complaint matching assistant for a banking ombudsman (RBI CRPC).
                Given a new complaint and a list of existing complaints, identify the top 5 most similar cases.

                NEW COMPLAINT:
                Subject: %s
                Description: %s
                Category: %s

                EXISTING COMPLAINTS (format: ID | Subject | Status):
                %s

                Return ONLY a JSON array of complaint IDs (strings) that are most similar, ranked by relevance.
                Example: ["CMS-20260101-ABC123", "CMS-20260102-DEF456"]
                If none are similar, return [].
                """.formatted(subject, truncate(description, 500), category != null ? category : "Unknown", candidateList);

        Map<String, Object> requestBody = Map.of(
                "model", groqModel,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.1,
                "max_tokens", 512
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // Retry once on rate-limit (429) or server error (5xx), then fall back
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        GROQ_URL, HttpMethod.POST, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    String content = root.path("choices").get(0).path("message").path("content").asText("");

                    int start = content.indexOf('[');
                    int end = content.lastIndexOf(']');
                    if (start >= 0 && end > start) {
                        List<String> matchedIds = new ArrayList<>();
                        for (JsonNode id : objectMapper.readTree(content.substring(start, end + 1))) {
                            matchedIds.add(id.asText());
                        }
                        consecutiveFailures = 0;  // success — reset circuit
                        return matchedIds.stream()
                                .map(id -> complaintRepository.findByComplaintNumber(id).orElse(null))
                                .filter(Objects::nonNull)
                                .map(this::toSummaryMap)
                                .collect(Collectors.toList());
                    }
                }

                log.warn("Groq returned unparseable response on attempt {}", attempt);
                break;  // not a transient error — don't retry

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt == 1) {
                    log.warn("Groq rate-limited (429), retrying once after 1 s...");
                    try { Thread.sleep(1_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                log.error("Groq HTTP error {}: {}", e.getStatusCode(), e.getMessage());
                recordFailure();
                break;

            } catch (Exception e) {
                log.error("Groq call failed (attempt {}): {}", attempt, e.getMessage());
                if (attempt == 2) recordFailure();
                if (attempt == 1) continue;
            }
        }

        return fallbackKeywordSearch(subject, description, currentComplaintId);
    }

    private void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            circuitOpenUntil = Instant.now().plusSeconds(CIRCUIT_COOLDOWN_SECONDS);
            log.warn("Groq circuit OPEN — skipping for {} s after {} consecutive failures",
                    CIRCUIT_COOLDOWN_SECONDS, consecutiveFailures);
            consecutiveFailures = 0;
        }
    }

    public Map<String, Object> getComplaintDetail(String complaintNumber) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) return null;

        Complaint c = opt.get();
        List<ComplaintTimeline> timeline = timelineRepository.findByComplaintIdOrderByPerformedAtDesc(c.getId());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("complaintId", c.getComplaintNumber());
        detail.put("subject", c.getSubject());
        detail.put("description", c.getDescription());
        detail.put("status", c.getStatus());
        detail.put("priority", c.getPriority());
        detail.put("category", c.getCategoryId());
        detail.put("complainantName", c.getComplainantName());
        detail.put("complainantEmail", c.getComplainantEmail());
        detail.put("complainantPhone", c.getComplainantPhone());
        detail.put("entityCode", c.getEntityCode());
        detail.put("department", c.getDepartment());
        detail.put("assignedRole", c.getAssignedRole());
        detail.put("assignedOfficer", c.getAssignedOfficer());
        detail.put("filingType", c.getFilingType());
        detail.put("filedDate", c.getCreatedAt() != null ? c.getCreatedAt().format(FMT) : "");
        detail.put("resolvedAt", c.getResolvedAt() != null ? c.getResolvedAt().format(FMT) : null);
        detail.put("closedAt", c.getClosedAt() != null ? c.getClosedAt().format(FMT) : null);
        detail.put("escalatedAt", c.getEscalatedAt() != null ? c.getEscalatedAt().format(FMT) : null);
        detail.put("reliefSought", c.getReliefSought());

        List<Map<String, Object>> timelineList = timeline.stream().map(t -> {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("action", t.getAction());
            tm.put("performedBy", t.getPerformedBy());
            tm.put("remarks", t.getRemarks());
            tm.put("fromStatus", t.getFromStatus());
            tm.put("toStatus", t.getToStatus());
            tm.put("timestamp", t.getPerformedAt() != null ? t.getPerformedAt().toString() : "");
            return tm;
        }).collect(Collectors.toList());
        detail.put("timeline", timelineList);

        return detail;
    }

    private List<Map<String, Object>> fallbackKeywordSearch(String subject, String description, String currentComplaintId) {
        // Build a meaningful search term: first 4 significant words from subject + first noun phrase from description
        String searchTerm = extractSearchTerms(subject, description);
        if (searchTerm.isBlank()) return List.of();

        log.info("Groq fallback keyword search: '{}'", searchTerm);

        Set<Complaint> seen = new LinkedHashSet<>();
        // Primary: search by extracted term
        seen.addAll(complaintRepository.search(searchTerm));
        // Secondary: try individual significant words if primary returned nothing
        if (seen.isEmpty() && subject != null) {
            for (String word : significantWords(subject)) {
                seen.addAll(complaintRepository.search(word));
                if (seen.size() >= 10) break;
            }
        }

        return seen.stream()
                .filter(c -> !c.getComplaintNumber().equals(currentComplaintId))
                .limit(5)
                .map(this::toSummaryMap)
                .collect(Collectors.toList());
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "is", "in", "on", "at", "to", "for",
            "of", "my", "i", "me", "by", "it", "its", "with", "from", "has", "have",
            "not", "no", "but", "be", "was", "are", "were", "will", "can", "did"
    );

    private String extractSearchTerms(String subject, String description) {
        List<String> words = new ArrayList<>(significantWords(subject));
        if (words.size() < 3 && description != null && !description.isBlank()) {
            words.addAll(significantWords(description.substring(0, Math.min(description.length(), 200))));
        }
        return words.stream().limit(4).collect(Collectors.joining(" "));
    }

    private List<String> significantWords(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.toLowerCase().split("[\\s,./!?;:()]+"))
                .filter(w -> w.length() > 3 && !STOP_WORDS.contains(w))
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, Object> toSummaryMap(Complaint c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("complaintId", c.getComplaintNumber());
        map.put("subject", c.getSubject());
        map.put("status", c.getStatus());
        map.put("complainantName", c.getComplainantName());
        map.put("date", c.getCreatedAt() != null ? c.getCreatedAt().format(FMT) : "");
        map.put("department", c.getDepartment());
        map.put("entityCode", c.getEntityCode());
        return map;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) : text;
    }
}
