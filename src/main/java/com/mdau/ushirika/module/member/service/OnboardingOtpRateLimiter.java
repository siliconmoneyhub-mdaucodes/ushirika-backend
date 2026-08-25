package com.mdau.ushirika.module.member.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps onboarding email-OTP requests per applicant. Without this, /onboarding/email-otp/request
 * fell into the generic 120 req/min-per-IP bucket meant for ordinary API traffic -- effectively
 * unthrottled for an endpoint that sends an email on every call. Keyed on the applicant's user
 * id (this endpoint is authenticated, unlike the anonymous forgot-password flow).
 */
@Component
public class OnboardingOtpRateLimiter {

    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.onboarding-otp.max-per-hour:5}")
    private int maxPerHour;

    public boolean tryConsume(UUID userId) {
        return buckets.computeIfAbsent(userId, k -> buildBucket()).tryConsume(1);
    }

    public int getMaxPerHour() {
        return maxPerHour;
    }

    private Bucket buildBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(maxPerHour)
                        .refillGreedy(maxPerHour, Duration.ofHours(1))
                        .build())
                .build();
    }
}
