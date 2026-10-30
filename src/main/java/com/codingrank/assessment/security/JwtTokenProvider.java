package com.codingrank.assessment.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${lms.integration.jwt-secret:supersecretkey}")
    private String secret;

    private SecretKey key;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 7 days token expiration (matching LMS refresh/session standard)
    private static final long EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L;

    @PostConstruct
    public void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            for (int i = bytes.length; i < 32; i++) {
                padded[i] = (byte) '#';
            }
            this.key = Keys.hmacShaKeyFor(padded);
        } else {
            this.key = Keys.hmacShaKeyFor(bytes);
        }
    }

    public String generateToken(String userId, String email, String name, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("email", email)
                .claim("name", name)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        // 1. First attempt: Standard unpadded HMAC-SHA256 signature verification (NestJS / Node.js)
        if (verifyHmacDirect(token, secret)) {
            if (isTokenExpired(token)) {
                log.warn("JWT token is expired");
                return false;
            }
            return true;
        }

        // 2. Second attempt: JJWT padded key verification (Spring Boot internal)
        if (key != null) {
            try {
                Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
                return true;
            } catch (JwtException | IllegalArgumentException ignored) {}
        }

        log.warn("Invalid JWT token: signature did not match secret");
        return false;
    }

    private boolean verifyHmacDirect(String token, String secretKeyStr) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            String data = parts[0] + "." + parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secretKeyStr.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] computedSigBytes = mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
            byte[] actualSigBytes = Base64.getUrlDecoder().decode(parts[2]);

            return MessageDigest.isEqual(computedSigBytes, actualSigBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> parsePayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to parse JWT payload: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    public boolean isTokenExpired(String token) {
        Map<String, Object> claims = parsePayload(token);
        Object expObj = claims.get("exp");
        if (expObj instanceof Number num) {
            long expSeconds = num.longValue();
            return Instant.now().getEpochSecond() > expSeconds;
        }
        return false;
    }

    public String getUserId(String token) {
        Map<String, Object> claims = parsePayload(token);
        Object sub = claims.get("sub");
        if (sub != null && !sub.toString().isBlank()) {
            return sub.toString();
        }
        Object userId = claims.get("userId");
        return userId != null ? userId.toString() : null;
    }

    public String getEmail(String token) {
        Map<String, Object> claims = parsePayload(token);
        Object email = claims.get("email");
        return email != null ? email.toString() : null;
    }

    public String getRole(String token) {
        Map<String, Object> claims = parsePayload(token);
        Object role = claims.get("role");
        return role != null ? role.toString() : "STUDENT";
    }

    public long getExpirationMs() {
        return EXPIRATION_MS;
    }
}
