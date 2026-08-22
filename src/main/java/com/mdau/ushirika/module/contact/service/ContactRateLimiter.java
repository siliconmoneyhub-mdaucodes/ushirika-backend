package com.mdau.ushirika.module.contact.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Caps public contact-form submissions per IP — independent of, and much tighter than, the
 *  general /public/** throttle in RateLimitFilter. The CAPTCHA raises the cost of any single
 *  bot submission; this caps how many a single source can push through regardless. */
@Component
public class ContactRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.contact-per-hour:5}")
    private int maxPerHour;

    public boolean tryConsume(String ip) {
        return buckets.computeIfAbsent(ip, k -> buildBucket()).tryConsume(1);
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
