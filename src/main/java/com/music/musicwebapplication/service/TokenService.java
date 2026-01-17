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

    /**
     * Generate token with timestamp and field encoded inside
     * Format: base64(timestamp:genericField:signature)
     */
    public String generateToken(long timestamp, String genericField) {
        try {
            String data = timestamp + ":" + genericField;
            String signature = generateHmac(data);
            String fullToken = data + ":" + signature;

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(fullToken.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate token", e);
        }
    }


    public TokenData extractData(String token) {
        try {
            // Decode token
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String decodedString = new String(decoded, StandardCharsets.UTF_8);

            String[] parts = decodedString.split(":");
            if (parts.length != 3) {
                log.error("Invalid token format");
                return null;
            }

            long timestamp = Long.parseLong(parts[0]);
            String genericField = parts[1];
            String signature = parts[2];

            // Verify signature
            String data = timestamp + ":" + genericField;
            String expectedSignature = generateHmac(data);

            if (!signature.equals(expectedSignature)) {
                log.error("Token signature invalid - token may be tampered");
                return null;
            }

            log.info("✅ Extracted from token - timestamp: {}, field: {}", timestamp, genericField);
            return new TokenData(timestamp, genericField);

        } catch (Exception e) {
            log.error("Failed to extract token data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Verify token (alternative method)
     */
    public boolean verifyToken(String token, long timestamp, String genericField) {
        TokenData data = extractData(token);
        if (data == null) {
            return false;
        }
        return data.getTimestamp() == timestamp && data.getGenericField().equals(genericField);
    }

    private String generateHmac(String data) throws Exception {
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