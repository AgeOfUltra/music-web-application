package com.music.musicwebapplication.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@Slf4j
public class TokenService {

    private final String SECRET_KEY = "your-secret-key-min-32-chars-long-keep-it-safe-and-secure-123456";

    public TokenService() {
        log.debug("TokenService initialized");
    }

    /**
     * Generate token with timestamp and field encoded inside
     * Format: base64(timestamp:genericField:signature)
     */
    public String generateToken(long timestamp, String genericField) {
        log.debug("Generating token for timestamp: {}, field: {}", timestamp, genericField);
        try {
            String data = timestamp + ":" + genericField;
            String signature = generateHmac(data);
            String fullToken = data + ":" + signature;

            String token = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(fullToken.getBytes(StandardCharsets.UTF_8));

            log.debug("Token generated successfully");
            return token;

        } catch (Exception e) {
            log.error("Failed to generate token for timestamp {}, field {}: {}", timestamp, genericField, e.getMessage(), e);
            throw new RuntimeException("Failed to generate token", e);
        }
    }


    public TokenData extractData(String token) {
        log.debug("Extracting data from token");
        try {
            // Decode token
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String decodedString = new String(decoded, StandardCharsets.UTF_8);

            String[] parts = decodedString.split(":");
            if (parts.length != 3) {
                log.error("Invalid token format - expected 3 parts, got: {}", parts.length);
                return null;
            }

            long timestamp = Long.parseLong(parts[0]);
            String genericField = parts[1];
            String signature = parts[2];
            log.debug("Token parts extracted - timestamp: {}, field: {}", timestamp, genericField);

            // Verify signature
            String data = timestamp + ":" + genericField;
            String expectedSignature = generateHmac(data);

            if (!signature.equals(expectedSignature)) {
                log.error("Token signature verification failed - token may be tampered");
                return null;
            }

            log.info("Token data extracted successfully - timestamp: {}, field: {}", timestamp, genericField);
            return new TokenData(timestamp, genericField);

        } catch (NumberFormatException e) {
            log.error("Failed to parse timestamp from token: {}", e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            log.error("Invalid token encoding: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Failed to extract token data: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Verify token (alternative method)
     */
    public boolean verifyToken(String token, long timestamp, String genericField) {
        log.debug("Verifying token for timestamp: {}, field: {}", timestamp, genericField);
        TokenData data = extractData(token);
        if (data == null) {
            log.warn("Token verification failed - could not extract data");
            return false;
        }

        boolean isValid = data.getTimestamp() == timestamp && data.getGenericField().equals(genericField);
        log.info("Token verification result: {}", isValid ? "valid" : "invalid");
        return isValid;
    }

    private String generateHmac(String data) throws Exception {
        log.debug("Generating HMAC signature");
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
                SECRET_KEY.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    @Data
    @AllArgsConstructor
    public static class TokenData {
        private long timestamp;
        private String genericField;
    }
}