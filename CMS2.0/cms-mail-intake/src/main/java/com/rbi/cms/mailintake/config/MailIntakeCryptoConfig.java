package com.rbi.cms.mailintake.config;

import com.rbi.cms.common.crypto.AesGcmPayloadEncryptionService;
import com.rbi.cms.common.crypto.PayloadEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The AES-256 key itself is never a config value — only the *name* of the environment variable
 * that holds it is (cms.mail.intake.encryption.key-env-var, default
 * CMS_MAIL_INTAKE_ENCRYPTION_KEY). Fails fast at startup if the variable is unset or the wrong
 * length, rather than silently falling back to an in-memory dev key that someone forgets isn't
 * production-safe.
 */
@Configuration
@EnableConfigurationProperties(MailIntakeProperties.class)
@RequiredArgsConstructor
public class MailIntakeCryptoConfig {

    private final MailIntakeProperties properties;

    @Bean
    public PayloadEncryptionService payloadEncryptionService(
            @Value("${cms.mail.intake.dev-local-encryption-key:}") String devLocalKey) {

        String envVarName = properties.getEncryption().getKeyEnvVar();
        String base64Key = System.getenv(envVarName);

        if (base64Key == null || base64Key.isBlank()) {
            // dev-local only: application-dev-local.yml supplies a fixed, clearly-fake key via
            // cms.mail.intake.dev-local-encryption-key so local development doesn't require
            // exporting a real secret. Never set in application.yml (the prod/SIT profile).
            if (!devLocalKey.isBlank()) {
                return AesGcmPayloadEncryptionService.fromBase64Key(devLocalKey);
            }
            throw new IllegalStateException(
                    "Environment variable " + envVarName + " is not set. cms-mail-intake refuses "
                    + "to start without a real encryption key outside dev-local — raw message "
                    + "bytes are PII and must be encrypted at rest.");
        }
        return AesGcmPayloadEncryptionService.fromBase64Key(base64Key);
    }
}
