package com.hrms.cms.service;

import com.hrms.cms.config.AuthSecurityProperties;
import com.hrms.cms.entity.CaptchaSession;
import com.hrms.cms.repository.CaptchaSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final CaptchaSessionRepository captchaSessionRepository;
    private final AuthSecurityProperties authProps;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    @Transactional
    public CaptchaChallenge generateVisualCaptcha() {
        String answer = generateRandomText();
        String token = UUID.randomUUID().toString();
        byte[] imageBytes = renderCaptchaImage(answer);
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);

        saveCaptchaSession(token, answer, "VISUAL");

        return new CaptchaChallenge(token, "data:image/png;base64," + imageBase64, null, "VISUAL");
    }

    @Transactional
    public CaptchaChallenge generateMathCaptcha() {
        int a = secureRandom.nextInt(20) + 1;
        int b = secureRandom.nextInt(20) + 1;
        boolean isAddition = secureRandom.nextBoolean();

        String question;
        String answer;
        if (isAddition) {
            question = "What is " + a + " plus " + b + "?";
            answer = String.valueOf(a + b);
        } else {
            int max = Math.max(a, b);
            int min = Math.min(a, b);
            question = "What is " + max + " minus " + min + "?";
            answer = String.valueOf(max - min);
        }

        String token = UUID.randomUUID().toString();
        saveCaptchaSession(token, answer, "MATH");

        return new CaptchaChallenge(token, null, question, "MATH");
    }

    @Transactional
    public boolean verifyCaptcha(String token, String userAnswer) {
        if (token == null || userAnswer == null || userAnswer.isBlank()) {
            return false;
        }

        var session = captchaSessionRepository
                .findByCaptchaTokenAndUsedFalseAndExpiresAtAfter(token, LocalDateTime.now());

        if (session.isEmpty()) {
            return false;
        }

        CaptchaSession cs = session.get();
        cs.setUsed(true);
        captchaSessionRepository.save(cs);

        String inputHash = hashValue(userAnswer.trim().toLowerCase());
        return inputHash.equals(cs.getAnswerHash());
    }

    private void saveCaptchaSession(String token, String answer, String type) {
        CaptchaSession cs = CaptchaSession.builder()
                .captchaToken(token)
                .answerHash(hashValue(answer.toLowerCase()))
                .captchaType(type)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(authProps.getCaptcha().getExpiryMinutes()))
                .build();
        captchaSessionRepository.save(cs);
    }

    private String generateRandomText() {
        int length = authProps.getCaptcha().getLength();
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(CHARS.charAt(secureRandom.nextInt(CHARS.length())));
        }
        return text.toString();
    }

    private byte[] renderCaptchaImage(String text) {
        int width = authProps.getCaptcha().getImageWidth();
        int height = authProps.getCaptcha().getImageHeight();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 245, 245));
        g.fillRect(0, 0, width, height);

        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(
                    secureRandom.nextInt(200),
                    secureRandom.nextInt(200),
                    secureRandom.nextInt(200), 80));
            g.drawLine(
                    secureRandom.nextInt(width), secureRandom.nextInt(height),
                    secureRandom.nextInt(width), secureRandom.nextInt(height));
        }

        for (int i = 0; i < 50; i++) {
            g.setColor(new Color(
                    secureRandom.nextInt(255),
                    secureRandom.nextInt(255),
                    secureRandom.nextInt(255), 100));
            g.fillOval(secureRandom.nextInt(width), secureRandom.nextInt(height), 2, 2);
        }

        Font font = new Font("Arial", Font.BOLD, 28);
        g.setFont(font);

        int charWidth = width / (text.length() + 2);
        for (int i = 0; i < text.length(); i++) {
            g.setColor(new Color(
                    secureRandom.nextInt(100),
                    secureRandom.nextInt(100),
                    secureRandom.nextInt(100)));

            AffineTransform orig = g.getTransform();
            double angle = (secureRandom.nextDouble() - 0.5) * 0.4;
            int x = charWidth + i * charWidth;
            int y = height / 2 + secureRandom.nextInt(10) - 5 + 10;
            g.rotate(angle, x, y);
            g.drawString(String.valueOf(text.charAt(i)), x, y);
            g.setTransform(orig);
        }

        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to render CAPTCHA image", e);
        }
    }

    private String hashValue(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record CaptchaChallenge(String token, String imageData, String audioQuestion, String type) {}
}
