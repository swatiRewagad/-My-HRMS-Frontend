package com.hrms.cms.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the OTP cannot be exposed outside local development.
 *
 * <h2>The defect these tests lock down</h2>
 *
 * <p>{@code cms.auth.otp.dev-auto-populate} makes {@code POST /api/v1/citizen/auth/send-otp} return the
 * generated OTP in its own response body, so anyone who can call it can authenticate as any mobile
 * number. It defaulted to TRUE in {@code application.yml}, and the only override lived in the
 * {@code prod} profile — while the OpenShift ConfigMap set {@code SPRING_PROFILES_ACTIVE=openshift}, a
 * profile that did not exist. Spring does not warn about an unknown profile, so the override was dead
 * code and every deployed environment exposed the OTP.
 *
 * <p>The fix is three independent layers, and each is asserted here, because any one of them alone has
 * a failure mode: a safe default can be overridden by an env var, an explicit ConfigMap value only helps
 * the environment that reads it, and a profile block only helps if that profile actually activates.
 */
class OtpExposureGuardTest {

    private static OtpExposureGuard guard(boolean exposed, String... activeProfiles) {
        AuthSecurityProperties props = new AuthSecurityProperties();
        props.getOtp().setDevAutoPopulate(exposed);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(activeProfiles);
        return new OtpExposureGuard(props, env);
    }

    @Nested
    @DisplayName("startup assertion")
    class StartupAssertion {

        @Test
        @DisplayName("REFUSES TO START when the OTP is exposed under the openshift profile")
        void refusesUnderOpenshift() {
            assertThatThrownBy(() -> guard(true, "openshift").verifyOtpIsNotExposed())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REFUSING TO START")
                    .hasMessageContaining("authenticate as any mobile number");
        }

        @Test
        @DisplayName("REFUSES TO START when the OTP is exposed with NO profile set")
        void refusesWithNoProfile() {
            // The most dangerous case: an unset SPRING_PROFILES_ACTIVE falls back to the base config,
            // which is exactly how this shipped. An empty profile list must never be treated as dev.
            assertThatThrownBy(() -> guard(true).verifyOtpIsNotExposed())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REFUSING TO START");
        }

        @Test
        @DisplayName("REFUSES TO START when the OTP is exposed under prod")
        void refusesUnderProd() {
            assertThatThrownBy(() -> guard(true, "prod").verifyOtpIsNotExposed())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("a profile merely CONTAINING 'dev' is not enough — the match must be exact")
        void refusesLookalikeProfiles() {
            // Guards against a substring check: "dev", "development" and "dev-local-ish" are not
            // dev-local, and a sloppy contains() would have waved all of them through.
            for (String lookalike : List.of("dev", "development", "dev-local-ish", "local")) {
                assertThatThrownBy(() -> guard(true, lookalike).verifyOtpIsNotExposed())
                        .as("profile '%s' must not be treated as dev-local", lookalike)
                        .isInstanceOf(IllegalStateException.class);
            }
        }

        @Test
        @DisplayName("PERMITS the exposure under dev-local, where the E2E suite needs it")
        void permitsUnderDevLocal() {
            assertThatCode(() -> guard(true, "dev-local").verifyOtpIsNotExposed())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("starts normally whenever the OTP is not exposed, under any profile")
        void startsWhenNotExposed() {
            for (String profile : List.of("openshift", "prod", "dev-local", "sit")) {
                assertThatCode(() -> guard(false, profile).verifyOtpIsNotExposed())
                        .as("a safe configuration must never block startup (profile %s)", profile)
                        .doesNotThrowAnyException();
            }
            assertThatCode(() -> guard(false).verifyOtpIsNotExposed()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("configuration itself")
    class ConfigurationDefaults {

        @Test
        @DisplayName("the Java default is false, so an absent config cannot expose the OTP")
        void javaDefaultIsSafe() {
            assertThat(new AuthSecurityProperties().getOtp().isDevAutoPopulate()).isFalse();
        }

        /**
         * Reads application.yml directly. The point is the DEFAULT baked into the placeholder: the
         * property resolves to the fallback in any environment that does not set OTP_DEV_AUTO_POPULATE,
         * which is every environment today, so a `true` fallback is itself the vulnerability.
         */
        @Test
        @DisplayName("application.yml defaults the placeholder to false, not true")
        void yamlPlaceholderDefaultsToFalse() {
            String yaml = readResource("application.yml");

            assertThat(yaml)
                    .as("the OTP_DEV_AUTO_POPULATE fallback must be false; a true fallback exposes the "
                            + "OTP in every environment that does not set the variable")
                    .contains("dev-auto-populate: ${OTP_DEV_AUTO_POPULATE:false}")
                    .doesNotContain("dev-auto-populate: ${OTP_DEV_AUTO_POPULATE:true}");
        }

        /**
         * The ConfigMap names this profile, so it must exist. While it did not, every {@code prod}
         * override was dead code in the only environment it was written for.
         */
        @Test
        @DisplayName("an openshift profile block exists and sets dev-auto-populate false")
        void openshiftProfileExistsAndIsSafe() {
            List<Map<String, Object>> documents = loadYamlDocuments("application.yml");

            Map<String, Object> openshift = documents.stream()
                    .filter(doc -> "openshift".equals(activeProfileOf(doc)))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "No 'openshift' profile block in application.yml, but the OpenShift "
                                    + "ConfigMap sets SPRING_PROFILES_ACTIVE=openshift. Spring does not "
                                    + "warn about an unknown profile, so every override would be dead."));

            assertThat(otpAutoPopulateOf(openshift))
                    .as("the openshift profile must not expose the OTP")
                    .isEqualTo(false);
        }

        @Test
        @DisplayName("dev-local is the only profile that turns the exposure on")
        void onlyDevLocalEnablesExposure() {
            assertThat(otpAutoPopulateOf(loadYamlDocuments("application-dev-local.yml").get(0)))
                    .as("dev-local needs devOtp so the Playwright suite can authenticate")
                    .isEqualTo(true);

            for (Map<String, Object> doc : loadYamlDocuments("application.yml")) {
                Boolean value = otpAutoPopulateOf(doc);
                if (value != null) {
                    assertThat(value)
                            .as("profile '%s' in application.yml must not enable the OTP exposure",
                                    activeProfileOf(doc))
                            .isFalse();
                }
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Reads from src/main/resources ON DISK, deliberately NOT from the classpath.
     *
     * <p>{@code src/test/resources/application.yml} exists (an H2 test fixture) and SHADOWS the real
     * file on the test classpath, so {@code getResourceAsStream("application.yml")} returns a 27-line
     * stub with no profile blocks at all. These tests assert on the configuration that SHIPS, so they
     * must read the shipping file; loading the shadowed copy would have made them assert nothing while
     * appearing to pass once the stub happened to satisfy them.
     */
    private static Path mainResource(String name) {
        Path path = Paths.get("src", "main", "resources", name);
        if (!Files.exists(path)) {
            throw new AssertionError(path.toAbsolutePath() + " does not exist");
        }
        return path;
    }

    private static String readResource(String name) {
        try {
            return Files.readString(mainResource(name));
        } catch (Exception e) {
            throw new AssertionError("could not read " + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadYamlDocuments(String name) {
        try (InputStream in = Files.newInputStream(mainResource(name))) {
            List<Map<String, Object>> docs = new java.util.ArrayList<>();
            for (Object doc : new Yaml().loadAll(in)) {
                if (doc instanceof Map) docs.add((Map<String, Object>) doc);
            }
            return docs;
        } catch (Exception e) {
            throw new AssertionError("could not parse " + name, e);
        }
    }

    /** The profile a YAML document activates, or null for the base document. */
    @SuppressWarnings("unchecked")
    private static String activeProfileOf(Map<String, Object> document) {
        Object spring = document.get("spring");
        if (!(spring instanceof Map)) return null;
        Object config = ((Map<String, Object>) spring).get("config");
        if (!(config instanceof Map)) return null;
        Object activate = ((Map<String, Object>) config).get("activate");
        if (!(activate instanceof Map)) return null;
        Object onProfile = ((Map<String, Object>) activate).get("on-profile");
        return onProfile == null ? null : String.valueOf(onProfile);
    }

    /** cms.auth.otp.dev-auto-populate for one document, or null when it does not set it. */
    @SuppressWarnings("unchecked")
    private static Boolean otpAutoPopulateOf(Map<String, Object> document) {
        Object cms = document.get("cms");
        if (!(cms instanceof Map)) return null;
        Object auth = ((Map<String, Object>) cms).get("auth");
        if (!(auth instanceof Map)) return null;
        Object otp = ((Map<String, Object>) auth).get("otp");
        if (!(otp instanceof Map)) return null;
        Object value = ((Map<String, Object>) otp).get("dev-auto-populate");
        if (value == null) return null;
        // The base document holds an unresolved ${OTP_DEV_AUTO_POPULATE:false} placeholder.
        if (value instanceof Boolean b) return b;
        String text = String.valueOf(value);
        if (text.contains("${")) {
            return text.contains(":false}") ? Boolean.FALSE : Boolean.TRUE;
        }
        return Boolean.parseBoolean(text);
    }
}
