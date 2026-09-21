package com.mdau.ushirika.module.auth.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two in-memory buckets for the activation flow:
 *  - per user id: at most {@value #MAX_RESENDS_PER_HOUR} code/link re-sends per hour, and
 *  - per client IP: at most {@value #MAX_VERIFY_PER_HOUR} code verifications per hour, so the
 *    per-account attempt counter can't be sidestepped by cycling through many tokens.
 * Single-instance assumption, same as the other limiters in this package.
 */
@Component
public class ActivationRateLimiter {

    public static final int MAX_RESENDS_PER_HOUR = 5;
    public static final int MAX_VERIFY_PER_HOUR = 20;

    private final Map<UUID, Bucket> resendBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> verifyBuckets = new ConcurrentHashMap<>();

    public boolean tryConsumeResend(UUID userId) {
        return resendBuckets.computeIfAbsent(userId, k -> hourly(MAX_RESENDS_PER_HOUR)).tryConsume(1);
    }

    public boolean tryConsumeVerify(String ip) {
        String key = ip == null ? "unknown" : ip;
        return verifyBuckets.computeIfAbsent(key, k -> hourly(MAX_VERIFY_PER_HOUR)).tryConsume(1);
    }

    private static Bucket hourly(int max) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(max).refillGreedy(max, Duration.ofHours(1)).build())
                .build();
    }
}
