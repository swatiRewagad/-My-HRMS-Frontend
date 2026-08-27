package com.rbi.cms.mailintake.parser;

import com.rbi.cms.mailintake.config.MailIntakeProperties;
import com.rbi.cms.mailintake.entity.SenderResolverType;
import org.apache.james.mime4j.dom.Message;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives every resolver against the required .eml corpus (src/test/resources/eml). Pure unit
 * tests — no Spring context — since sender resolution is deterministic logic over a parsed
 * message, not something that needs a container. Wired up the same way {@link SmtpServer} wires
 * the real chain: resolvers constructed directly, in brief-specified priority order.
 */
class SenderResolutionChainCorpusTest {

    private final MailIntakeProperties properties = new MailIntakeProperties();
    private final MimeMessageFactory messageFactory = new MimeMessageFactory();
    private final InlineForwardBlockParser blockParser = new InlineForwardBlockParser();
    private final SenderResolutionChain chain = new SenderResolutionChain(List.of(
            new RedirectHeaderSenderResolver(),
            new CustomHeaderSenderResolver(properties),
            new NestedMessageSenderResolver(properties),
            new TnefSenderResolver(properties, blockParser),
            new InlineForwardBlockSenderResolver(properties, blockParser)
    ));

    @Test
    void exchangeRedirectResolvesViaRedirectHeaders() throws Exception {
        Optional<ResolvedSender> result = chain.resolve(load("01-exchange-redirect.eml"));
        assertThat(result).isPresent();
        assertThat(result.get().resolverType()).isEqualTo(SenderResolverType.REDIRECT_HEADERS);
        assertThat(result.get().originalFrom()).isEqualTo("ramesh.kumar@example.com");
    }

    @Test
    void exchangeForwardResolvesViaNestedMessage() throws Exception {
        Optional<ResolvedSender> result = chain.resolve(load("02-exchange-forward-nested.eml"));
        assertThat(result).isPresent();
        assertThat(result.get().resolverType()).isEqualTo(SenderResolverType.NESTED_MESSAGE);
        assertThat(result.get().originalFrom()).isEqualTo("priya.sharma@example.com");
        assertThat(result.get().originalSubject()).isEqualTo("Complaint about credit card overcharge");
    }

    @Test
    void inlineForwardBlockResolvesViaRegex() throws Exception {
        Optional<ResolvedSender> result = chain.resolve(load("03-inline-forward-block.eml"));
        assertThat(result).isPresent();
        assertThat(result.get().resolverType()).isEqualTo(SenderResolverType.INLINE_FORWARD_BLOCK);
        assertThat(result.get().originalFrom()).isEqualTo("amit.verma@example.com");
    }

    @Test
    void forwardOfForwardUnwrapsToTheDeepestOriginalMessage() throws Exception {
        Optional<ResolvedSender> result = chain.resolve(load("04-forward-of-forward.eml"));
        assertThat(result).isPresent();
        assertThat(result.get().resolverType()).isEqualTo(SenderResolverType.NESTED_MESSAGE);
        // Must be the ORIGINAL complainant (innermost), not the intermediate nodal officer.
        assertThat(result.get().originalFrom()).isEqualTo("sunita.rao@example.com");
    }

    @Test
    void undecodableTnefFailsGracefullyRatherThanCrashingTheChain() throws Exception {
        // Apache POI's HMEF is read-only (no TNEF writer exists), so this fixture's winmail.dat
        // is deliberately not valid TNEF — the more realistic and more important behaviour to
        // verify is that a bad TNEF blob doesn't crash resolution (rule 4). Since this message
        // has no other resolvable signal, the chain correctly ends up UNRESOLVED.
        Optional<ResolvedSender> result = chain.resolve(load("05-tnef-winmail.eml"));
        assertThat(result).isEmpty();
    }

    @Test
    void rfc2047EncodedSubjectIsDecoded() throws Exception {
        Optional<ResolvedSender> result = chain.resolve(load("06-rfc2047-subject.eml"));
        assertThat(result).isPresent();
        assertThat(result.get().originalSubject()).isEqualTo("खाते में अनधिकृत डेबिट");
    }

    @Test
    void devanagariBodySurvivesParsingUncorrupted() throws Exception {
        Message message = load("07-devanagari-body.eml");
        Optional<ResolvedSender> result = chain.resolve(message);
        assertThat(result).isPresent();

        String body = MimeBodyUtils.findPlainText(result.get().canonicalMessage()).orElseThrow();
        assertThat(body).contains("मेरे बैंक खाते से बिना अनुमति के पैसे कट गए हैं");
    }

    @Test
    void base64PdfAttachmentDecodesToAValidPdf() throws Exception {
        Message message = load("08-base64-pdf-attachment.eml");
        List<RawAttachment> attachments = MimeBodyUtils.extractAttachments(message);
        assertThat(attachments).hasSize(1);
        byte[] content = attachments.get(0).content();
        assertThat(new String(content, 0, 8)).isEqualTo("%PDF-1.4");
    }

    @Test
    void malformedUnterminatedBoundaryStillParsesSomethingRatherThanThrowing() throws Exception {
        // rule 4: never fail-closed on a parse error. mime4j's lenient config (MimeMessageFactory)
        // should recover at least the first part's content even without a proper closing boundary.
        Message message = load("10-malformed-unterminated-boundary.eml");
        assertThat(message).isNotNull();
        assertThat(message.getSubject()).isEqualTo("Complaint with broken multipart structure");
    }

    @Test
    void missingMessageIdDoesNotCrashParsingOrResolution() throws Exception {
        Message message = load("11-no-message-id.eml");
        assertThat(message.getMessageId()).isNull();
        Optional<ResolvedSender> result = chain.resolve(message);
        assertThat(result).isPresent();
        assertThat(result.get().originalFrom()).isEqualTo("anjali.gupta@example.com");
    }

    @Test
    void resolverChainRecordsWhichResolverFiredForEveryPositiveResult() throws Exception {
        // Every fixture that resolves at all must report a real resolver, never UNRESOLVED —
        // the audit trail depends on this (brief: "record which resolver fired").
        for (String fixture : List.of("01-exchange-redirect.eml", "02-exchange-forward-nested.eml",
                "03-inline-forward-block.eml", "06-rfc2047-subject.eml", "11-no-message-id.eml")) {
            Optional<ResolvedSender> result = chain.resolve(load(fixture));
            assertThat(result).as("fixture " + fixture).isPresent();
            assertThat(result.get().resolverType()).as("fixture " + fixture).isNotEqualTo(SenderResolverType.UNRESOLVED);
        }
    }

    private Message load(String fixtureName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("eml/" + fixtureName)) {
            assertThat(in).as("fixture eml/%s must exist on the test classpath", fixtureName).isNotNull();
            return messageFactory.parse(in.readAllBytes());
        }
    }
}
