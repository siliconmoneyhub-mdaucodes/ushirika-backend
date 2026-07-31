package com.mdau.ushirika.module.auth.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps password-reset OTP requests per submitted email address. The cap is keyed
 * on the raw email string, not on whether an account actually exists for it, so
 * the limiter behaves identically for real and fake addresses — preserving the
 * anti-enumeration guarantee in AuthService.forgotPassword().
 */
@Component
public class PasswordResetRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.password-reset.max-per-hour:3}")
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
