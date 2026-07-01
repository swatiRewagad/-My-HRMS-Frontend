package com.hrms.cms.controller;

import com.hrms.cms.service.TranslationService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/i18n")
@CrossOrigin(origins = "*")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @GetMapping("/locales")
    public ResponseEntity<List<Map<String, Object>>> getSupportedLocales() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
            .body(translationService.getSupportedLocales());
    }

    @GetMapping("/translations/{locale}")
    public ResponseEntity<Map<String, String>> getTranslations(@PathVariable String locale) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic())
            .body(translationService.getTranslationsForLocale(locale));
    }

    @GetMapping("/translations/{locale}/{module}")
    public ResponseEntity<Map<String, String>> getTranslationsByModule(
            @PathVariable String locale,
            @PathVariable String module) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic())
            .body(translationService.getTranslationsForLocaleAndModule(locale, module));
    }

    @PostMapping("/translations")
    public ResponseEntity<Void> upsertTranslation(@RequestBody Map<String, String> body) {
        translationService.upsertTranslation(body.get("code"), body.get("locale"), body.get("value"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/translations/bulk")
    public ResponseEntity<Void> bulkUpsert(@RequestBody List<Map<String, String>> entries) {
        translationService.bulkUpsert(entries);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/keys")
    public ResponseEntity<Map<String, Object>> createKey(@RequestBody Map<String, String> body) {
        var key = translationService.createKey(
            body.get("code"),
            body.get("module"),
            body.get("description"),
            body.get("defaultValue")
        );
        return ResponseEntity.ok(Map.of("id", key.getId(), "code", key.getCode()));
    }
}
