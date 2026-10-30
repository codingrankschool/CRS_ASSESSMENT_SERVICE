package com.codingrank.assessment.service;

import com.codingrank.assessment.common.enums.AssessmentType;
import com.codingrank.assessment.dto.LaunchTokenDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@Service
@Slf4j
public class LmsIntegrationService {

    @Value("${lms.integration.jwt-secret}")
    private String jwtSecret;

    @Value("${lms.integration.webhook-url}")
    private String webhookUrl;

    @Value("${lms.integration.webhook-hmac-secret}")
    private String hmacSecret;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Validates an LMS SSO Launch JWT Token.
     */
    public LaunchTokenDto validateLaunchToken(String token) {
        try {
            byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = bytes;
            if (bytes.length < 32) {
                keyBytes = new byte[32];
                System.arraycopy(bytes, 0, keyBytes, 0, bytes.length);
                for (int i = bytes.length; i < 32; i++) {
                    keyBytes[i] = (byte) '#';
                }
            }
            Key key = Keys.hmacShaKeyFor(keyBytes);
            Claims claims = Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String typeStr = claims.get("assessmentType", String.class);
            AssessmentType type = typeStr != null ? AssessmentType.valueOf(typeStr) : AssessmentType.ASSIGNMENT;

            log.info("LMS Launch Token validated successfully: userId='{}' email='{}' role='{}' assessmentRefId='{}'",
                    claims.getSubject(), claims.get("email", String.class), claims.get("role", String.class), claims.get("assessmentRefId", String.class));

            return LaunchTokenDto.builder()
                    .userId(claims.getSubject())
                    .email(claims.get("email", String.class))
                    .name(claims.get("name", String.class))
                    .role(claims.get("role", String.class))
                    .courseId(claims.get("courseId", String.class))
                    .batchId(claims.get("batchId", String.class))
                    .assessmentRefId(claims.get("assessmentRefId", String.class))
                    .assessmentType(type)
                    .returnUrl(claims.get("returnUrl", String.class))
                    .build();
        } catch (Exception e) {
            log.error("Invalid LMS Launch token", e);
            throw new IllegalArgumentException("Invalid or expired LMS launch token: " + e.getMessage());
        }
    }

    /**
     * Sends an HMAC-signed webhook to the LMS notifying of completion.
     */
    @Async
    public void notifyLmsAssessmentCompleted(
            String userId,
            String batchId,
            String courseId,
            String assessmentRefId,
            String status,
            int score,
            int maxScore,
            boolean isDisqualified
    ) {
        try {
            long timestamp = Instant.now().getEpochSecond();
            String payload = String.format("""
                {
                  "eventId": "evt_%d",
                  "eventType": "ASSESSMENT_SUBMITTED",
                  "timestamp": "%s",
                  "data": {
                    "userId": "%s",
                    "batchId": "%s",
                    "courseId": "%s",
                    "assessmentRefId": "%s",
                    "status": "%s",
                    "score": %d,
                    "maxScore": %d,
                    "percentage": %.2f,
                    "isDisqualified": %b
                  }
                }
                """,
                timestamp,
                Instant.now().toString(),
                userId,
                batchId != null ? batchId : "",
                courseId != null ? courseId : "",
                assessmentRefId,
                status,
                score,
                maxScore,
                maxScore > 0 ? ((double) score / maxScore) * 100 : 0.0,
                isDisqualified
            );

            String signature = calculateHmac(payload, hmacSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Assessment-Signature", "t=" + timestamp + ",v1=" + signature)
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            log.info("Dispatching assessment webhook to LMS at {}", webhookUrl);
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(res -> {
                        if (res.statusCode() >= 200 && res.statusCode() < 300) {
                            log.info("LMS webhook delivered successfully. Status: {}", res.statusCode());
                        } else {
                            log.warn("LMS webhook returned non-200 status: {} body: {}", res.statusCode(), res.body());
                        }
                    })
                    .exceptionally(ex -> {
                        log.warn("LMS webhook endpoint ({}) offline or unreachable: {}", webhookUrl, ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            log.error("Error creating LMS webhook payload", e);
        }
    }

    private String calculateHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }
}
