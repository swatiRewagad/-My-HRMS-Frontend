package com.rbi.cms.notification.channel;

import com.rbi.cms.common.enums.NotificationChannel;
import com.rbi.cms.notification.config.NotificationProperties;
import com.rbi.cms.notification.repository.OutboundEmailRow;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Sends an officer-composed mail over SMTP.
 *
 * <p>Uses {@link MimeMessage} rather than the {@code SimpleMailMessage} this service used before, which
 * could carry neither an HTML body nor CC, BCC or attachments — all of which the compose form collects.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatchHandler implements DispatchChannelHandler {

    /**
     * Resolved lazily because dev-local excludes MailSenderAutoConfiguration, so no JavaMailSender bean
     * exists there at all. A hard dependency would stop the service starting on that profile.
     */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationProperties properties;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void dispatch(OutboundEmailRow row) throws Exception {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException(
                    "No JavaMailSender is configured, so this email cannot be sent. Either configure spring.mail.*"
                            + " or set cms.notification.mode=SIMULATE.");
        }

        String[] to = splitAddresses(row.getToEmail());
        if (to.length == 0) {
            // Not retryable: the row will never grow a recipient on its own.
            throw new IllegalArgumentException("Email " + row.getId() + " has no usable recipient address");
        }

        MimeMessage mime = mailSender.createMimeMessage();
        // multipart, so attachment support can be added without changing how the body is set.
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());

        helper.setFrom(StringUtils.hasText(row.getFromEmail())
                ? row.getFromEmail()
                : properties.getEmail().getDefaultFrom());
        helper.setTo(to);

        String[] cc = splitAddresses(row.getCcEmail());
        if (cc.length > 0) {
            helper.setCc(cc);
        }
        String[] bcc = splitAddresses(row.getBccEmail());
        if (bcc.length > 0) {
            helper.setBcc(bcc);
        }

        helper.setSubject(row.getSubject() == null ? "" : row.getSubject());
        helper.setText(row.getBody() == null ? "" : row.getBody(), true);

        mailSender.send(mime);
        log.info("Dispatched email {} for complaint {} to {} recipient(s)",
                row.getId(), row.getComplaintNumber(), to.length);
    }

    /**
     * Splits a stored recipient list into individual addresses.
     *
     * <p>Both separators are accepted because the request validation accepts both, so
     * {@code "a@x.com; b@y.com"} is legitimate input that reaches the column as one string. Handing that
     * whole string to {@code setTo(String)} would treat it as a single malformed address.</p>
     */
    private static String[] splitAddresses(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new String[0];
        }
        return Arrays.stream(raw.split("[,;]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }
}
