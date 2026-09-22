package com.rbi.cms.notification.channel;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.rbi.cms.common.enums.NotificationChannel;
import com.rbi.cms.notification.config.NotificationProperties;
import com.rbi.cms.notification.repository.OutboundEmailRow;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What actually leaves the process, asserted against an in-process SMTP server.
 *
 * <p>Every case here is something the previous {@code SimpleMailMessage} implementation could not do.</p>
 */
class EmailDispatchHandlerTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private EmailDispatchHandler handler;
    private NotificationProperties properties;

    @BeforeEach
    void setUp() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(greenMail.getSmtp().getPort());

        properties = new NotificationProperties();
        handler = new EmailDispatchHandler(providerOf(sender), properties);
    }

    private static ObjectProvider<JavaMailSender> providerOf(JavaMailSender sender) {
        return new ObjectProvider<>() {
            @Override
            public JavaMailSender getObject(Object... args) {
                return sender;
            }

            @Override
            public JavaMailSender getObject() {
                return sender;
            }

            @Override
            public JavaMailSender getIfAvailable() {
                return sender;
            }

            @Override
            public JavaMailSender getIfUnique() {
                return sender;
            }
        };
    }

    private static OutboundEmailRow.OutboundEmailRowBuilder row() {
        return OutboundEmailRow.builder()
                .id(1L)
                .messageId("msg-1")
                .fromEmail("officer@rbi.org.in")
                .toEmail("nodal@bank.example.com")
                .subject("Comments called for")
                .body("<p>Please respond <b>within 7 days</b>.</p>")
                .complaintNumber("CMS-20260101-ABC123");
    }

    @Test
    void shouldReportTheEmailChannel() {
        assertThat(handler.channel()).isEqualTo(NotificationChannel.EMAIL);
    }

    /**
     * The message is multipart so attachments can be added later, which means the HTML lives in a nested
     * part rather than in the top-level content type - hence asserting against the whole MIME source.
     */
    @Test
    void shouldSendAnHtmlBody() throws Exception {
        handler.dispatch(row().build());

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("Comments called for");

        String source = GreenMailUtil.getWholeMessage(received[0]);
        assertThat(source).contains("text/html");
        assertThat(source).contains("<b>within 7 days</b>");
    }

    @Test
    void shouldUseTheRowsOwnFromAddressRatherThanTheDefault() throws Exception {
        handler.dispatch(row().build());

        MimeMessage received = greenMail.getReceivedMessages()[0];
        assertThat(Arrays.toString(received.getFrom())).contains("officer@rbi.org.in");
        assertThat(Arrays.toString(received.getFrom())).doesNotContain(properties.getEmail().getDefaultFrom());
    }

    @Test
    void shouldFallBackToTheConfiguredFromWhenTheRowHasNone() throws Exception {
        handler.dispatch(row().fromEmail(null).build());

        assertThat(Arrays.toString(greenMail.getReceivedMessages()[0].getFrom()))
                .contains(properties.getEmail().getDefaultFrom());
    }

    @Test
    void shouldCarryCcAndBcc() throws Exception {
        handler.dispatch(row()
                .ccEmail("reviewer@rbi.org.in")
                .bccEmail("archive@rbi.org.in")
                .build());

        // GreenMail delivers one message per recipient, so three in total.
        assertThat(greenMail.getReceivedMessages()).hasSize(3);

        MimeMessage first = greenMail.getReceivedMessages()[0];
        assertThat(recipients(first, Message.RecipientType.CC)).contains("reviewer@rbi.org.in");
        // BCC is intentionally absent from the headers; its presence shows as the extra delivery above.
        assertThat(recipients(first, Message.RecipientType.TO)).contains("nodal@bank.example.com");
    }

    /**
     * The compose form's validation accepts ',' or ';', so a semicolon-separated list is legitimate stored
     * input. Handing it to setTo as one string would produce a single malformed address.
     */
    @Test
    void shouldSplitASemicolonSeparatedRecipientList() throws Exception {
        handler.dispatch(row().toEmail("one@bank.example.com; two@bank.example.com").build());

        assertThat(greenMail.getReceivedMessages()).hasSize(2);
        assertThat(recipients(greenMail.getReceivedMessages()[0], Message.RecipientType.TO))
                .contains("one@bank.example.com")
                .contains("two@bank.example.com");
    }

    @Test
    void shouldSplitACommaSeparatedRecipientList() throws Exception {
        handler.dispatch(row().toEmail("one@bank.example.com, two@bank.example.com").build());

        assertThat(greenMail.getReceivedMessages()).hasSize(2);
    }

    @Test
    void shouldRejectARowWithNoRecipient() {
        // IllegalArgumentException is non-retryable: the row will never grow a recipient by itself.
        assertThatThrownBy(() -> handler.dispatch(row().toEmail("   ").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
    }

    @Test
    void shouldFailClearlyWhenNoMailSenderIsConfigured() {
        EmailDispatchHandler withoutSender = new EmailDispatchHandler(providerOf(null), properties);

        assertThatThrownBy(() -> withoutSender.dispatch(row().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SIMULATE");
    }

    private static List<String> recipients(MimeMessage message, Message.RecipientType type) throws Exception {
        jakarta.mail.Address[] addresses = message.getRecipients(type);
        return addresses == null ? List.of() : Arrays.stream(addresses).map(Object::toString).toList();
    }
}
