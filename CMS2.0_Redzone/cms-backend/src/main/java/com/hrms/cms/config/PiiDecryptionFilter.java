package com.hrms.cms.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.service.EncryptionKeyService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class PiiDecryptionFilter implements Filter {

    private final EncryptionKeyService encryptionKeyService;
    private final ObjectMapper objectMapper;

    private static final String SESSION_HEADER = "X-Session-Id";
    private static final String ENCRYPTED_MARKER = "ENC:";
    private static final int GCM_TAG_LENGTH = 128;
    private static final Set<String> PII_FIELDS = Set.of(
            "complainantName", "complainantEmail", "complainantPhone",
            "accountNumber", "complainantAddress", "name", "email",
            "mobileNumber", "address", "pincode"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!isDecryptionTarget(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String sessionId = httpRequest.getHeader(SESSION_HEADER);
        if (sessionId == null || sessionId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String body = readRequestBody(httpRequest);
            if (body == null || body.isBlank() || !body.contains(ENCRYPTED_MARKER)) {
                chain.doFilter(new CachedBodyRequest(httpRequest, body), response);
                return;
            }

            Map<String, Object> jsonBody = objectMapper.readValue(body, new TypeReference<>() {});
            String sessionKey = encryptionKeyService.deriveSessionKey(sessionId);
            byte[] keyBytes = Base64.getDecoder().decode(sessionKey);

            boolean decrypted = false;
            for (String field : PII_FIELDS) {
                Object value = jsonBody.get(field);
                if (value instanceof String strVal && strVal.startsWith(ENCRYPTED_MARKER)) {
                    String plaintext = decryptField(strVal.substring(ENCRYPTED_MARKER.length()), keyBytes);
                    if (plaintext != null) {
                        jsonBody.put(field, plaintext);
                        decrypted = true;
                    } else {
                        httpResponse.setStatus(400);
                        httpResponse.setContentType("application/json");
                        httpResponse.getWriter().write(
                                "{\"error\":\"DECRYPTION_FAILED\",\"message\":\"Failed to decrypt PII field: " + field + "\"}");
                        return;
                    }
                }
            }

            String newBody = objectMapper.writeValueAsString(jsonBody);
            if (decrypted) {
                log.debug("Decrypted PII fields for session: {}****", sessionId.substring(0, 8));
            }
            chain.doFilter(new CachedBodyRequest(httpRequest, newBody), response);

        } catch (Exception e) {
            log.error("PII decryption filter error: {}", e.getMessage());
            httpResponse.setStatus(400);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"error\":\"DECRYPTION_ERROR\",\"message\":\"Invalid encrypted payload.\"}");
        }
    }

    private String decryptField(String encryptedBase64, byte[] keyBytes) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            if (combined.length < 12) return null;

            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] ciphertext = Arrays.copyOfRange(combined, 12, combined.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Decryption failed for field: {}", e.getMessage());
            return null;
        }
    }

    private boolean isDecryptionTarget(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method)) return false;

        String path = request.getRequestURI();
        return path.contains("/api/v1/complaints") || path.contains("/api/complaints");
    }

    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final String body;

        public CachedBodyRequest(HttpServletRequest request, String body) {
            super(request);
            this.body = body;
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new StringReader(body != null ? body : ""));
        }

        @Override
        public ServletInputStream getInputStream() {
            byte[] bytes = (body != null ? body : "").getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {}
                @Override public int read() { return bais.read(); }
            };
        }

        @Override
        public int getContentLength() {
            return body != null ? body.getBytes(StandardCharsets.UTF_8).length : 0;
        }

        @Override
        public long getContentLengthLong() {
            return getContentLength();
        }
    }
}
