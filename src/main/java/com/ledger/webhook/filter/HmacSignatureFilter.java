package com.ledger.webhook.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Validates HMAC-SHA256 signature against the raw body bytes before parsing.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class HmacSignatureFilter extends OncePerRequestFilter {

    public static final String SIGNATURE_HEADER = "X-Signature";

    private final String webhookSecret;

    public HmacSignatureFilter(@Value("${ledger.webhook.secret:super-secret-shared-key-2026}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!path.startsWith("/api/v1/webhooks")) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String signatureHeader = wrappedRequest.getHeader(SIGNATURE_HEADER);

        if (signatureHeader == null || signatureHeader.trim().isEmpty()) {
            rejectUnauthorized(response, "Missing required X-Signature header");
            return;
        }

        String expectedSignature = signatureHeader.trim();
        if (expectedSignature.startsWith("sha256=")) {
            expectedSignature = expectedSignature.substring(7);
        }

        byte[] bodyBytes = wrappedRequest.getCachedBody();
        String computedSignature = calculateHmacSha256Hex(bodyBytes, webhookSecret);

        // Constant-time comparison to prevent timing attacks
        boolean isValid = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                computedSignature.getBytes(StandardCharsets.UTF_8)
        );

        if (!isValid) {
            log.warn("Webhook HMAC signature verification failed for path={}", path);
            rejectUnauthorized(response, "Invalid HMAC signature");
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private void rejectUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format("{\"error\":\"%s\"}", message));
    }

    public static String calculateHmacSha256Hex(byte[] data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
