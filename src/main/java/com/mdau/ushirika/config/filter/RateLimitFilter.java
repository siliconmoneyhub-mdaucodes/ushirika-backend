package com.mdau.ushirika.config.filter;

import com.mdau.ushirika.common.util.ClientIpResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP in-memory rate limiter using Bucket4j token buckets.
 *
 * Tiers (requests per minute):
 *   /api/auth/**      → auth-rpm    (default 10)  — OTP/login brute-force protection
 *   /api/public/**    → public-rpm  (default 60)  — unauthenticated browsing
 *   everything else   → api-rpm     (default 120) — authenticated API calls
 *
 * Buckets are held in a ConcurrentHashMap keyed by IP. Memory is bounded by the
 * number of distinct clients; in production a distributed cache (Redis/Bucket4j-Redis)
 * would be used instead.
 */
@Slf4j
@Component
@Order(2)
public class RateLimitFilter implements Filter {

    private final Map<String, Bucket> authBuckets   = new ConcurrentHashMap<>();
    private final Map<String, Bucket> publicBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> apiBuckets    = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.auth-rpm:10}")
    private int authRpm;

    @Value("${app.rate-limit.public-rpm:60}")
    private int publicRpm;

    @Value("${app.rate-limit.api-rpm:120}")
    private int apiRpm;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        if (!(req instanceof HttpServletRequest httpReq)) {
            chain.doFilter(req, res);
            return;
        }

        String ip   = ClientIpResolver.resolve(httpReq);
        String path = httpReq.getRequestURI();

        // Stripe webhooks are excluded — throttling them would cause missed events.
        if (path.contains("/webhooks/")) {
            chain.doFilter(req, res);
            return;
        }

        Bucket bucket;
        if (path.contains("/auth/")) {
            bucket = authBuckets.computeIfAbsent(ip, k -> buildBucket(authRpm));
        } else if (path.contains("/public/")) {
            bucket = publicBuckets.computeIfAbsent(ip, k -> buildBucket(publicRpm));
        } else {
            bucket = apiBuckets.computeIfAbsent(ip, k -> buildBucket(apiRpm));
        }

        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            log.warn("Rate limit exceeded for IP={} path={}", ip, path);
            HttpServletResponse httpRes = (HttpServletResponse) res;
            httpRes.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpRes.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpRes.getWriter().write("""
                    {"success":false,"message":"Too many requests. Please slow down and try again shortly."}
                    """);
        }
    }

    private Bucket buildBucket(int requestsPerMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
