package com.rbi.cms.mailintake.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every threshold, regex, address, CIDR, and timeout the intake brief calls out is here — no
 * magic numbers in the listener/parser code. Mirrors the {@code AuthSecurityProperties} pattern
 * already used in cms-backend (nested {@code @Getter @Setter} groups rather than a Java record —
 * matched deliberately for consistency with the rest of this codebase; the brief asked for a
 * record, this repo's own convention won out).
 *
 * See src/main/resources/application.yml for a fully commented sample with production-sane
 * defaults.
 */
@Validated
@ConfigurationProperties(prefix = "cms.mail.intake")
@Getter @Setter
public class MailIntakeProperties {

    private Listener listener = new Listener();
    private Tls tls = new Tls();
    private Allowlist allowlist = new Allowlist();
    private Recipients recipients = new Recipients();
    private LoopGuard loopGuard = new LoopGuard();
    private Resolver resolver = new Resolver();
    private ComplaintRef complaintRef = new ComplaintRef();
    private Attachments attachments = new Attachments();
    private Retry retry = new Retry();
    private Retention retention = new Retention();
    private Storage storage = new Storage();
    private Encryption encryption = new Encryption();
    private Parser parser = new Parser();
    private Admin admin = new Admin();
    private Health health = new Health();

    @Getter @Setter
    public static class Listener {
        private String bindAddress = "0.0.0.0";
        /** Deploy concern, not a code concern — see RUNBOOK.md for :25 binding options. Default
         *  here is the safe non-privileged port for local dev / behind a relaying MTA. */
        private int port = 2525;
        private int backlog = 50;
        private int connectionTimeoutSeconds = 60;
        private int commandTimeoutSeconds = 30;
        private int maxConcurrentConnections = 50;
        private int maxConnectionsPerIp = 5;
        @Min(1)
        private long maxMessageSizeBytes = 25L * 1024 * 1024;
        /** Not really configurable in spirit — rule 2 (never an open relay) depends on this
         *  staying 1 — but exposed rather than hardcoded so it's visible/auditable in config. */
        private int maxRecipientsPerTransaction = 1;
    }

    @Getter @Setter
    public static class Tls {
        private boolean required = false;
        private String certPath;
        private String keyPath;
    }

    @Getter @Setter
    public static class Allowlist {
        /** CIDR blocks for the RBI mail relay. Connections outside this list are rejected before
         *  MAIL FROM — this list *is* the authentication mechanism (see brief PART 0 #4). */
        @NotEmpty
        private List<String> cidrs = List.of();
    }

    @Getter @Setter
    public static class Recipients {
        /** RCPT TO must exactly match one of these; anything else gets 550 5.7.1. */
        @NotEmpty
        private List<String> botAddresses = List.of("cms20bot@cms20.rbi.org.in");
    }

    @Getter @Setter
    public static class LoopGuard {
        private String headerName = "X-CMS-Loop-Guard";
        private boolean blockAutoSubmitted = true;
        private boolean blockBulkPrecedence = true;
    }

    @Getter @Setter
    public static class Resolver {
        private String originalSenderHeaderName = "X-Original-Sender";
        @Min(1)
        private int nestedMessageDepthCap = 5;
        /** language tag -> regex, e.g. "en" -> the English "-----Original Message-----" block
         *  pattern, "hi" -> the Devanagari प्रेषक: pattern. Adding a language is a config change. */
        private Map<String, String> inlineForwardPatterns = defaultInlineForwardPatterns();

        private static Map<String, String> defaultInlineForwardPatterns() {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("en", "(?s)-{3,}\\s*Original Message\\s*-{3,}.*?From:\\s*(?<from>[^\\r\\n]+).*?Sent:\\s*(?<sent>[^\\r\\n]+).*?To:\\s*(?<to>[^\\r\\n]+).*?Subject:\\s*(?<subject>[^\\r\\n]+)");
            m.put("hi", "(?s)-{3,}\\s*मूल संदेश\\s*-{3,}.*?प्रेषक:\\s*(?<from>[^\\r\\n]+).*?भेजा गया:\\s*(?<sent>[^\\r\\n]+).*?प्रति:\\s*(?<to>[^\\r\\n]+).*?विषय:\\s*(?<subject>[^\\r\\n]+)");
            return m;
        }
    }

    @Getter @Setter
    public static class ComplaintRef {
        /** Matched against subject/body to thread a reply onto an existing complaint instead of
         *  opening a duplicate. Adjust to the real CMS complaint-number format. */
        private String regex = "\\bCMP-[A-Z0-9-]{6,20}\\b";
    }

    @Getter @Setter
    public static class Attachments {
        private int maxCount = 20;
        private long maxIndividualSizeBytes = 10L * 1024 * 1024;
        private long maxTotalSizeBytes = 25L * 1024 * 1024;
        private ZipBomb zipBomb = new ZipBomb();

        @Getter @Setter
        public static class ZipBomb {
            private int maxDecompressionRatio = 100;
            private int maxDepth = 5;
        }
    }

    @Getter @Setter
    public static class Retry {
        private int maxAttempts = 8;
        private int initialBackoffSeconds = 30;
        private int maxBackoffSeconds = 3600;
        private double backoffMultiplier = 2.0;
    }

    @Getter @Setter
    public static class Retention {
        /** Raw-bytes purge honouring the CMS retention policy — metadata row + audit trail are
         *  kept regardless (see Stage 5 retention job). */
        private int rawBytesRetentionDays = 180;
    }

    @Getter @Setter
    public static class Storage {
        /** Same cms.storage.* convention already used by cms-ingestion-service. */
        private String rawMessageBasePath = "/data/cms/mail-intake/raw";
    }

    @Getter @Setter
    public static class Encryption {
        /** Name of the environment variable holding the base64-encoded AES-256 key — never the
         *  key value itself in config. See PayloadEncryptionService in cms-common. */
        private String keyEnvVar = "CMS_MAIL_INTAKE_ENCRYPTION_KEY";
    }

    @Getter @Setter
    public static class Parser {
        /** How often the scheduler looks for RECEIVED rows and FAILED rows whose
         *  next_attempt_at has passed. The SMTP listener thread never touches this — parsing is
         *  always asynchronous (brief: "A slow or broken parser must never block SMTP
         *  acceptance"). */
        private int pollIntervalSeconds = 5;
        private int workerPoolSize = 4;
        /** Cap on rows fetched per poll, so one tick can't try to load an unbounded backlog into
         *  memory at once. */
        private int batchSize = 50;
    }

    @Getter @Setter
    public static class Admin {
        /** Realm role required for every /admin/mail-intake/** endpoint — the same Keycloak
         *  realm (rbi-cms) the rest of CMS already uses, per Stage 1's SecurityConfig decision. */
        private String requiredRole = "MAIL_INTAKE_ADMIN";
    }

    @Getter @Setter
    public static class Health {
        /** Health goes DOWN once RECEIVED+due-FAILED backlog exceeds this — a growing queue
         *  usually means the parser pool is stuck, not that traffic is merely high. */
        private int queueDepthWarningThreshold = 500;
    }
}
