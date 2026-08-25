package com.mdau.ushirika.module.auth.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps self-registration email-verification OTP resends per submitted email address.
 * Keyed on the raw email string, not on whether an account exists for it, same
 * anti-enumeration rationale as PasswordResetRateLimiter.
 */
@Component
public class EmailVerificationOtpRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.email-verification-otp.max-per-hour:3}")
    private int maxPerHour;

    public boolean tryConsume(String email) {
        return buckets.computeIfAbsent(email, k -> buildBucket()).tryConsume(1);
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
