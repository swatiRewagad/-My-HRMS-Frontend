package com.rbi.cms.mailintake.smtp;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Builds the STARTTLS SslContext from the cms20.rbi.org.in cert/key (PEM, not a Java keystore —
 * simplest to hand a DC-issued cert/key pair straight to). Returns null (no bean) if cert-path/
 * key-path aren't both set — STARTTLS then just isn't advertised. cms.mail.intake.tls.required
 * being true with no cert configured is a startup-time misconfiguration, not something to paper
 * over silently, so that combination fails fast rather than quietly running without TLS.
 */
@Slf4j
@Configuration
public class SmtpTlsConfig {

    @Bean
    public SslContext smtpSslContext(MailIntakeProperties properties) throws Exception {
        MailIntakeProperties.Tls tls = properties.getTls();
        boolean certConfigured = tls.getCertPath() != null && !tls.getCertPath().isBlank()
                && tls.getKeyPath() != null && !tls.getKeyPath().isBlank();

        if (!certConfigured) {
            if (tls.isRequired()) {
                throw new IllegalStateException(
                        "cms.mail.intake.tls.required=true but cert-path/key-path are not both set");
            }
            log.warn("cms.mail.intake.tls.cert-path/key-path not configured — STARTTLS will not be offered");
            return null;
        }

        return SslContextBuilder
                .forServer(new File(tls.getCertPath()), new File(tls.getKeyPath()))
                .build();
    }
}
