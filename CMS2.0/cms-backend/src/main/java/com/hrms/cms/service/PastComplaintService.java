package com.hrms.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.dto.complaint.PastComplaintDetailResponse;
import com.hrms.cms.dto.complaint.PastComplaintSummary;
import com.hrms.cms.dto.complaint.PastComplaintTimelineEntry;
import com.hrms.cms.dto.complaint.SimilarCasesResponse;
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

    public List<PastComplaintSummary> findPastComplaints(String email, String phone, String currentComplaintId) {
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
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public SimilarCasesResponse findSimilarCases(String subject, String description, String category, String currentComplaintId) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            log.warn("Groq API key not configured — using keyword fallback");
            return keywordFallback(subject, description, currentComplaintId);
        }

        // Circuit breaker: skip Groq entirely while cooling down
        if (Instant.now().isBefore(circuitOpenUntil)) {
            log.warn("Groq circuit open (cooling down) — using keyword fallback");
            return keywordFallback(subject, description, currentComplaintId);
        }

        List<Complaint> candidates = complaintRepository.findAllByOrderByCreatedAtDesc();
        if (candidates.size() > 100) candidates = candidates.subList(0, 100);
        candidates = candidates.stream()
                .filter(c -> !c.getComplaintNumber().equals(currentComplaintId))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return SimilarCasesResponse.builder().matchMethod(SimilarCasesResponse.METHOD_NONE).build();
        }

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
                        return SimilarCasesResponse.builder()
                                .cases(matchedIds.stream()
                                        .map(id -> complaintRepository.findByComplaintNumber(id).orElse(null))
                                        .filter(Objects::nonNull)
                                        .map(this::toSummary)
                                        .collect(Collectors.toList()))
                                .matchMethod(SimilarCasesResponse.METHOD_AI)
                                .build();
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

        return keywordFallback(subject, description, currentComplaintId);
    }

    private SimilarCasesResponse keywordFallback(String subject, String description, String currentComplaintId) {
        return SimilarCasesResponse.builder()
                .cases(fallbackKeywordSearch(subject, description, currentComplaintId))
                .matchMethod(SimilarCasesResponse.METHOD_KEYWORD)
                .build();
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

    /** Null when no complaint carries that number — the caller turns that into a 404. */
    public PastComplaintDetailResponse getComplaintDetail(String complaintNumber) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) return null;

        Complaint c = opt.get();
        List<ComplaintTimeline> timeline = timelineRepository.findByComplaintIdOrderByPerformedAtDesc(c.getId());

        return PastComplaintDetailResponse.builder()
                .complaintId(c.getComplaintNumber())
                .subject(c.getSubject())
                .description(c.getDescription())
                .status(c.getStatus())
                .priority(c.getPriority())
                .category(c.getCategoryId())
                .complainantName(c.getComplainantName())
                .complainantEmail(c.getComplainantEmail())
                .complainantPhone(c.getComplainantPhone())
                .entityCode(c.getEntityCode())
                .department(c.getDepartment())
                .assignedRole(c.getAssignedRole())
                .assignedOfficer(c.getAssignedOfficer())
                .filingType(c.getFilingType())
                .filedDate(c.getCreatedAt() != null ? c.getCreatedAt().format(FMT) : "")
                .resolvedAt(c.getResolvedAt() != null ? c.getResolvedAt().format(FMT) : null)
                .closedAt(c.getClosedAt() != null ? c.getClosedAt().format(FMT) : null)
                .escalatedAt(c.getEscalatedAt() != null ? c.getEscalatedAt().format(FMT) : null)
                .reliefSought(c.getReliefSought())
                .timeline(timeline.stream()
                        .map(t -> PastComplaintTimelineEntry.builder()
                                .action(t.getAction())
                                .performedBy(t.displayActor())
                                .remarks(t.getRemarks())
                                .fromStatus(t.getFromStatus())
                                .toStatus(t.getToStatus())
                                .timestamp(t.getPerformedAt() != null ? t.getPerformedAt().toString() : "")
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private List<PastComplaintSummary> fallbackKeywordSearch(String subject, String description, String currentComplaintId) {
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
                .map(this::toSummary)
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

    private PastComplaintSummary toSummary(Complaint c) {
        return PastComplaintSummary.builder()
                .complaintId(c.getComplaintNumber())
                .subject(c.getSubject())
                .status(c.getStatus())
                .complainantName(c.getComplainantName())
                .filedDate(c.getCreatedAt() != null ? c.getCreatedAt().format(FMT) : "")
                .department(c.getDepartment())
                .entityCode(c.getEntityCode())
                .build();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) : text;
    }
}
