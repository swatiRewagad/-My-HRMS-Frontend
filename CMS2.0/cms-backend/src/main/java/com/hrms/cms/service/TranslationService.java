package com.hrms.cms.service;

import com.hrms.cms.entity.SupportedLocale;
import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TranslationService {

    private final TranslationRepository translationRepository;
    private final TranslationKeyRepository translationKeyRepository;

    public TranslationService(TranslationRepository translationRepository,
                              TranslationKeyRepository translationKeyRepository) {
        this.translationRepository = translationRepository;
        this.translationKeyRepository = translationKeyRepository;
    }

    @Cacheable(value = "translations", key = "#locale")
    @Transactional(readOnly = true)
    public Map<String, String> getTranslationsForLocale(String locale) {
        String resolvedLocale = SupportedLocale.isSupported(locale) ? locale : "en";

        List<Translation> translations = translationRepository.findAllByLocale(resolvedLocale);

        Map<String, String> result = translations.stream()
            .collect(Collectors.toMap(
                t -> t.getTranslationKey().getCode(),
                Translation::getValue,
                (v1, v2) -> v2
            ));

        List<TranslationKey> allKeys = translationKeyRepository.findAll();
        for (TranslationKey key : allKeys) {
            result.putIfAbsent(key.getCode(), key.getDefaultValue() != null ? key.getDefaultValue() : key.getCode());
        }

        return result;
    }

    @Cacheable(value = "translations-module", key = "#locale + '-' + #module")
    @Transactional(readOnly = true)
    public Map<String, String> getTranslationsForLocaleAndModule(String locale, String module) {
        String resolvedLocale = SupportedLocale.isSupported(locale) ? locale : "en";

        List<Translation> translations = translationRepository.findByLocaleAndModule(resolvedLocale, module);

        Map<String, String> result = translations.stream()
            .collect(Collectors.toMap(
                t -> t.getTranslationKey().getCode(),
                Translation::getValue,
                (v1, v2) -> v2
            ));

        List<TranslationKey> moduleKeys = translationKeyRepository.findByModule(module);
        for (TranslationKey key : moduleKeys) {
            result.putIfAbsent(key.getCode(), key.getDefaultValue() != null ? key.getDefaultValue() : key.getCode());
        }

        return result;
    }

    public List<Map<String, Object>> getSupportedLocales() {
        List<Map<String, Object>> locales = new ArrayList<>();
        for (SupportedLocale sl : SupportedLocale.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", sl.getCode());
            entry.put("name", sl.getNameEn());
            entry.put("nativeName", sl.getNativeName());
            entry.put("rtl", sl.isRtl());
            locales.add(entry);
        }
        return locales;
    }

    @CacheEvict(value = {"translations", "translations-module"}, allEntries = true)
    @Transactional
    public void upsertTranslation(String code, String locale, String value) {
        TranslationKey key = translationKeyRepository.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Translation key not found: " + code));

        List<Translation> existing = translationRepository.findByKeyIdAndLocale(key.getId(), locale);

        if (!existing.isEmpty()) {
            Translation t = existing.get(0);
            t.setValue(value);
            translationRepository.save(t);
        } else {
            Translation t = new Translation();
            t.setTranslationKey(key);
            t.setLocale(locale);
            t.setValue(value);
            translationRepository.save(t);
        }
    }

    @CacheEvict(value = {"translations", "translations-module"}, allEntries = true)
    @Transactional
    public TranslationKey createKey(String code, String module, String description, String defaultValue) {
        if (translationKeyRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Translation key already exists: " + code);
        }
        TranslationKey key = new TranslationKey();
        key.setCode(code);
        key.setModule(module);
        key.setDescription(description);
        key.setDefaultValue(defaultValue);
        return translationKeyRepository.save(key);
    }

    @CacheEvict(value = {"translations", "translations-module"}, allEntries = true)
    @Transactional
    public void bulkUpsert(List<Map<String, String>> entries) {
        for (Map<String, String> entry : entries) {
            String code = entry.get("code");
            String locale = entry.get("locale");
            String value = entry.get("value");

            Optional<TranslationKey> keyOpt = translationKeyRepository.findByCode(code);
            TranslationKey key;
            if (keyOpt.isEmpty()) {
                key = new TranslationKey();
                key.setCode(code);
                key.setModule(code.contains(".") ? code.substring(0, code.indexOf('.')) : "general");
                key.setDescription(code);
                key.setDefaultValue(value);
                key = translationKeyRepository.save(key);
            } else {
                key = keyOpt.get();
            }

            List<Translation> existing = translationRepository.findByKeyIdAndLocale(key.getId(), locale);

            if (!existing.isEmpty()) {
                existing.get(0).setValue(value);
                translationRepository.save(existing.get(0));
            } else {
                Translation t = new Translation();
                t.setTranslationKey(key);
                t.setLocale(locale);
                t.setValue(value);
                translationRepository.save(t);
            }
        }
    }
}
