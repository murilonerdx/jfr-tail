package io.jfrtail.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * reliable, zero-dependency JWT implementation for Java 8+.
 * Supports HS256 (HMAC-SHA256) only.
 */
public class JwtLite {

    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Pattern PAYLOAD_PATTERN = Pattern.compile("\"exp\":(\\d+)");

    /**
     * Generates a JWT token signed with the given secret.
     * 
     * @param secret     The shared secret key.
     * @param ttlSeconds Time-to-live in seconds.
     * @return The JWT string.
     */
    public static String generateToken(String secret, long ttlSeconds) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Secret cannot be empty");
        }

        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = "{\"exp\":" + exp + ",\"role\":\"admin\"}";

        String b64Header = base64UrlEncode(HEADER.getBytes(StandardCharsets.UTF_8));
        String b64Payload = base64UrlEncode(payload.getBytes(StandardCharsets.UTF_8));

        String signature = sign(b64Header + "." + b64Payload, secret);

        return b64Header + "." + b64Payload + "." + signature;
    }

    /**
     * Verifies a JWT token.
     * 
     * @param token  The JWT string.
     * @param secret The shared secret key.
     * @return true if valid and not expired.
     */
    public static boolean verifyToken(String token, String secret) {
        if (token == null || secret == null)
            return false;

        String[] parts = token.split("\\.");
        if (parts.length != 3)
            return false;

        String b64Header = parts[0];
        String b64Payload = parts[1];
        String signature = parts[2];

        // 1. Verify Signature
        String expectedSignature = sign(b64Header + "." + b64Payload, secret);
        if (!expectedSignature.equals(signature)) {
            return false;
        }

        // 2. Verify Expiration
        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(b64Payload), StandardCharsets.UTF_8);
            Matcher matcher = PAYLOAD_PATTERN.matcher(payloadJson);
            if (matcher.find()) {
                long exp = Long.parseLong(matcher.group(1));
                if (Instant.now().getEpochSecond() > exp) {
                    return false; // Expired
                }
            } else {
                return false; // No exp claim
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    private static String sign(String data, String secret) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Encryption Error", e);
        }
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
