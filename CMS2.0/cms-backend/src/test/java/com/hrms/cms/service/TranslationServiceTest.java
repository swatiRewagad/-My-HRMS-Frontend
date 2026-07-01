package com.hrms.cms.service;

import com.hrms.cms.entity.SupportedLocale;
import com.hrms.cms.entity.Translation;
import com.hrms.cms.entity.TranslationKey;
import com.hrms.cms.repository.TranslationKeyRepository;
import com.hrms.cms.repository.TranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TranslationServiceTest {

    @Mock
    private TranslationRepository translationRepository;

    @Mock
    private TranslationKeyRepository translationKeyRepository;

    @InjectMocks
    private TranslationService service;

    private TranslationKey sampleKey;
    private Translation sampleTranslation;

    @BeforeEach
    void setup() {
        sampleKey = new TranslationKey();
        sampleKey.setId(1L);
        sampleKey.setCode("common.submit");
        sampleKey.setModule("common");
        sampleKey.setDefaultValue("Submit");

        sampleTranslation = new Translation();
        sampleTranslation.setId(1L);
        sampleTranslation.setTranslationKey(sampleKey);
        sampleTranslation.setLocale("hi");
        sampleTranslation.setValue("जमा करें");
    }

    @Test
    @DisplayName("should return translations map for supported locale")
    void shouldReturnTranslationsForLocale() {
        when(translationRepository.findAllByLocale("hi")).thenReturn(List.of(sampleTranslation));
        when(translationKeyRepository.findAll()).thenReturn(List.of(sampleKey));

        Map<String, String> result = service.getTranslationsForLocale("hi");

        assertThat(result).containsEntry("common.submit", "जमा करें");
    }

    @Test
    @DisplayName("should fallback to English for unsupported locale")
    void shouldFallbackForUnsupportedLocale() {
        Translation enTranslation = new Translation();
        enTranslation.setTranslationKey(sampleKey);
        enTranslation.setLocale("en");
        enTranslation.setValue("Submit");

        when(translationRepository.findAllByLocale("en")).thenReturn(List.of(enTranslation));

        Map<String, String> result = service.getTranslationsForLocale("xx");

        assertThat(result).containsEntry("common.submit", "Submit");
    }

    @Test
    @DisplayName("should fill missing translations with defaultValue")
    void shouldFillMissingWithDefault() {
        TranslationKey missingKey = new TranslationKey();
        missingKey.setId(2L);
        missingKey.setCode("common.cancel");
        missingKey.setModule("common");
        missingKey.setDefaultValue("Cancel");

        when(translationRepository.findAllByLocale("hi")).thenReturn(List.of(sampleTranslation));
        when(translationKeyRepository.findAll()).thenReturn(List.of(sampleKey, missingKey));

        Map<String, String> result = service.getTranslationsForLocale("hi");

        assertThat(result).containsEntry("common.submit", "जमा करें");
        assertThat(result).containsEntry("common.cancel", "Cancel");
    }

    @Test
    @DisplayName("should return translations filtered by module")
    void shouldReturnByModule() {
        when(translationRepository.findByLocaleAndModule("hi", "common")).thenReturn(List.of(sampleTranslation));
        when(translationKeyRepository.findByModule("common")).thenReturn(List.of(sampleKey));

        Map<String, String> result = service.getTranslationsForLocaleAndModule("hi", "common");

        assertThat(result).containsEntry("common.submit", "जमा करें");
    }

    @Test
    @DisplayName("should return all 10 supported locales")
    void shouldReturnSupportedLocales() {
        List<Map<String, Object>> locales = service.getSupportedLocales();

        assertThat(locales).hasSize(10);
        assertThat(locales.get(0).get("code")).isEqualTo("en");
        assertThat(locales.get(7).get("code")).isEqualTo("ur");
        assertThat(locales.get(7).get("rtl")).isEqualTo(true);
    }

    @Test
    @DisplayName("should upsert existing translation")
    void shouldUpsertExisting() {
        Translation existing = new Translation();
        existing.setId(1L);
        existing.setTranslationKey(sampleKey);
        existing.setLocale("hi");
        existing.setValue("Old value");

        when(translationKeyRepository.findByCode("common.submit")).thenReturn(Optional.of(sampleKey));
        when(translationRepository.findByKeyIdAndLocale(1L, "hi")).thenReturn(List.of(existing));
        when(translationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.upsertTranslation("common.submit", "hi", "New value");

        verify(translationRepository).save(existing);
        assertThat(existing.getValue()).isEqualTo("New value");
    }

    @Test
    @DisplayName("should create new translation when none exists")
    void shouldCreateNewTranslation() {
        when(translationKeyRepository.findByCode("common.submit")).thenReturn(Optional.of(sampleKey));
        when(translationRepository.findByKeyIdAndLocale(1L, "bn")).thenReturn(List.of());
        when(translationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.upsertTranslation("common.submit", "bn", "জমা দিন");

        verify(translationRepository).save(argThat(t ->
            t.getLocale().equals("bn") && t.getValue().equals("জমা দিন")
        ));
    }

    @Test
    @DisplayName("should throw when upserting to non-existent key")
    void shouldThrowForMissingKey() {
        when(translationKeyRepository.findByCode("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertTranslation("nonexistent", "hi", "val"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("should create new translation key")
    void shouldCreateKey() {
        when(translationKeyRepository.existsByCode("new.key")).thenReturn(false);
        when(translationKeyRepository.save(any())).thenAnswer(i -> {
            TranslationKey k = i.getArgument(0);
            k.setId(99L);
            return k;
        });

        TranslationKey result = service.createKey("new.key", "common", "New key", "Default");

        assertThat(result.getCode()).isEqualTo("new.key");
        assertThat(result.getModule()).isEqualTo("common");
    }

    @Test
    @DisplayName("should reject duplicate key creation")
    void shouldRejectDuplicateKey() {
        when(translationKeyRepository.existsByCode("common.submit")).thenReturn(true);

        assertThatThrownBy(() -> service.createKey("common.submit", "common", "desc", "val"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Urdu locale should be RTL")
    void shouldIdentifyUrduAsRtl() {
        SupportedLocale urdu = SupportedLocale.fromCode("ur");
        assertThat(urdu.isRtl()).isTrue();
        assertThat(urdu.getNativeName()).isEqualTo("اردو");
    }

    @Test
    @DisplayName("English locale should not be RTL")
    void shouldIdentifyEnglishAsLtr() {
        SupportedLocale en = SupportedLocale.fromCode("en");
        assertThat(en.isRtl()).isFalse();
    }

    @Test
    @DisplayName("unsupported code should default to English")
    void shouldDefaultToEnglish() {
        SupportedLocale unknown = SupportedLocale.fromCode("zz");
        assertThat(unknown).isEqualTo(SupportedLocale.EN);
    }
}
