package com.hrms.cms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LanguageTranslationService {

    @Value("${cms.translation.provider:gemini}")
    private String translationProvider;

    @Value("${cms.ocr.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${cms.ocr.groq-api-key:}")
    private String groqApiKey;

    @Value("${cms.ocr.groq-model:meta-llama/llama-4-scout-17b-16e-instruct}")
    private String groqModel;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final Map<String, String> SCRIPT_PATTERNS = new LinkedHashMap<>();

    static {
        SCRIPT_PATTERNS.put("hi", "[\u0900-\u097F]"); // Devanagari (Hindi, Marathi, Sanskrit)
        SCRIPT_PATTERNS.put("bn", "[\u0980-\u09FF]"); // Bengali
        SCRIPT_PATTERNS.put("ta", "[\u0B80-\u0BFF]"); // Tamil
        SCRIPT_PATTERNS.put("te", "[\u0C00-\u0C7F]"); // Telugu
        SCRIPT_PATTERNS.put("kn", "[\u0C80-\u0CFF]"); // Kannada
        SCRIPT_PATTERNS.put("ml", "[\u0D00-\u0D7F]"); // Malayalam
        SCRIPT_PATTERNS.put("gu", "[\u0A80-\u0AFF]"); // Gujarati
        SCRIPT_PATTERNS.put("pa", "[\u0A00-\u0A7F]"); // Punjabi (Gurmukhi)
        SCRIPT_PATTERNS.put("or", "[\u0B00-\u0B7F]"); // Odia
        SCRIPT_PATTERNS.put("ur", "[\u0600-\u06FF]"); // Urdu (Arabic script)
        SCRIPT_PATTERNS.put("as", "[\u0980-\u09FF]"); // Assamese (same as Bengali script)
    }

    private static final Map<String, String> LANGUAGE_NAMES = Map.ofEntries(
            Map.entry("hi", "Hindi"),
            Map.entry("bn", "Bengali"),
            Map.entry("ta", "Tamil"),
            Map.entry("te", "Telugu"),
            Map.entry("kn", "Kannada"),
            Map.entry("ml", "Malayalam"),
            Map.entry("gu", "Gujarati"),
            Map.entry("pa", "Punjabi"),
            Map.entry("or", "Odia"),
            Map.entry("ur", "Urdu"),
            Map.entry("as", "Assamese"),
            Map.entry("mr", "Marathi"),
            Map.entry("en", "English")
    );

    public Map<String, Object> detectAndTranslate(String text) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (text == null || text.isBlank()) {
            result.put("detectedLanguage", "en");
            result.put("languageName", "English");
            result.put("isVernacular", false);
            result.put("translatedText", text);
            result.put("originalText", text);
            result.put("confidence", 100);
            return result;
        }

        String detectedLang = detectLanguage(text);
        boolean isVernacular = !"en".equals(detectedLang);

        result.put("detectedLanguage", detectedLang);
        result.put("languageName", LANGUAGE_NAMES.getOrDefault(detectedLang, "Unknown"));
        result.put("isVernacular", isVernacular);
        result.put("originalText", text);

        if (isVernacular) {
            String translated = translateToEnglish(text, detectedLang);
            result.put("translatedText", translated);
            result.put("confidence", calculateConfidence(text, detectedLang));
            log.info("Translated {} text ({} chars) to English", LANGUAGE_NAMES.get(detectedLang), text.length());
        } else {
            result.put("translatedText", text);
            result.put("confidence", 100);
        }

        return result;
    }

    public String detectLanguage(String text) {
        if (text == null || text.isBlank()) return "en";

        for (Map.Entry<String, String> entry : SCRIPT_PATTERNS.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getValue());
            long matches = text.chars()
                    .mapToObj(c -> String.valueOf((char) c))
                    .filter(s -> pattern.matcher(s).matches())
                    .count();

            if (matches > text.length() * 0.15) {
                return entry.getKey();
            }
        }

        return "en";
    }

    private String translateToEnglish(String text, String sourceLang) {
        try {
            if (groqApiKey != null && !groqApiKey.isBlank()) {
                return translateViaGroq(text, sourceLang);
            } else if (geminiApiKey != null && !geminiApiKey.isBlank()) {
                return translateViaGemini(text, sourceLang);
            }
        } catch (Exception e) {
            log.error("Translation API failed: {}", e.getMessage());
        }

        return "[Translation unavailable - original " + LANGUAGE_NAMES.getOrDefault(sourceLang, sourceLang) + " text preserved] " + text;
    }

    private String translateViaGroq(String text, String sourceLang) {
        String langName = LANGUAGE_NAMES.getOrDefault(sourceLang, sourceLang);

        String prompt = "Translate the following " + langName + " text to English. " +
                "This is a banking/financial complaint. Preserve all names, account numbers, dates, and amounts exactly as they are. " +
                "Return ONLY the English translation, nothing else:\n\n" + text;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", groqModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "You are a translator for RBI (Reserve Bank of India) complaint management. Translate accurately preserving financial details."),
                Map.of("role", "user", "content", prompt)
        ));
        body.put("temperature", 0.1);
        body.put("max_tokens", 2000);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.groq.com/openai/v1/chat/completions",
                HttpMethod.POST, request, Map.class);

        if (response.getBody() != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
        }

        throw new RuntimeException("Empty response from Groq");
    }

    private String translateViaGemini(String text, String sourceLang) {
        String langName = LANGUAGE_NAMES.getOrDefault(sourceLang, sourceLang);

        String prompt = "Translate the following " + langName + " text to English. " +
                "This is a banking/financial complaint to RBI. Preserve all names, account numbers, dates, and amounts exactly. " +
                "Return ONLY the English translation:\n\n" + text;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                ))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiApiKey;

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

        if (response.getBody() != null) {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
        }

        throw new RuntimeException("Empty response from Gemini");
    }

    private int calculateConfidence(String text, String lang) {
        Pattern pattern = Pattern.compile(SCRIPT_PATTERNS.getOrDefault(lang, ""));
        long matches = text.chars()
                .mapToObj(c -> String.valueOf((char) c))
                .filter(s -> pattern.matcher(s).matches())
                .count();

        double ratio = (double) matches / text.replaceAll("\\s+", "").length();
        return Math.min(98, (int) (ratio * 100) + 50);
    }
}
