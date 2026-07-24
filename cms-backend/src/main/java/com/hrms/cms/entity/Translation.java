package com.hrms.cms.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "translations", indexes = {
    @Index(name = "idx_trans_locale", columnList = "locale"),
    @Index(name = "idx_trans_key_locale", columnList = "translation_key_id, locale", unique = true)
})
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "translation_key_id", nullable = false)
    private TranslationKey translationKey;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(name = "\"value\"", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TranslationKey getTranslationKey() { return translationKey; }
    public void setTranslationKey(TranslationKey translationKey) { this.translationKey = translationKey; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
