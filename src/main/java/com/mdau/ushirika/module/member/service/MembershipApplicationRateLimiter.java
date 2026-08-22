package com.mdau.ushirika.module.member.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Caps public membership applications per IP. A flood of fake applications is the highest-value
 *  target for a bot on this site (each one lands in the admin review queue), so this stays much
 *  tighter than the general /public/** throttle in RateLimitFilter. */
@Component
public class MembershipApplicationRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.application-per-day:3}")
    private int maxPerDay;

    public boolean tryConsume(String ip) {
        return buckets.computeIfAbsent(ip, k -> buildBucket()).tryConsume(1);
    }

    private Bucket buildBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(maxPerDay)
                        .refillGreedy(maxPerDay, Duration.ofDays(1))
                        .build())
                .build();
    }
}
