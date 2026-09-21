package com.mdau.ushirika.module.auth.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory throttles on FAILED attempts, on top of the per-IP RateLimitFilter:
 *  - login: 10 failures / 15 min per normalised submitted username (and per resolved account email),
 *  - one-time codes (password reset, email verification): 5 failures / 15 min per email.
 * A success clears the key. Blocking is checked BEFORE credentials are evaluated so a locked key
 * gives no signal about whether a guess was right.
 *
 * In-memory is consistent with every other limiter in this codebase and fine for the current
 * single-instance Railway deploy; a multi-instance deploy would need a shared store (Redis).
 */
@Component
public class LoginAttemptLimiter {

    public static final int LOGIN_MAX_FAILURES = 10;
    public static final int OTP_MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private final Window login = new Window(LOGIN_MAX_FAILURES);
    private final Window otp = new Window(OTP_MAX_FAILURES);

    // -- login --
    public boolean isLoginBlocked(String key)  { return login.isBlocked(normalise(key)); }
    public void recordLoginFailure(String key) { login.fail(normalise(key)); }
    public void clearLogin(String key)         { login.clear(normalise(key)); }

    // -- one-time codes --
    public boolean isOtpBlocked(String key)    { return otp.isBlocked(normalise(key)); }
    public void recordOtpFailure(String key)   { otp.fail(normalise(key)); }
    public void clearOtp(String key)           { otp.clear(normalise(key)); }

    static String normalise(String key) {
        return key == null ? "" : key.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static final class Window {
        private final int max;
        private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

        Window(int max) { this.max = max; }

        private Bucket bucket(String key) {
            return buckets.computeIfAbsent(key, k -> Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(max).refillGreedy(max, WINDOW).build())
                    .build());
        }

        /** Blocked once every failure token is spent. Peeking never consumes anything. */
        boolean isBlocked(String key) {
            Bucket b = buckets.get(key);
            return b != null && b.getAvailableTokens() <= 0;
        }

        void fail(String key) { bucket(key).tryConsume(1); }

        void clear(String key) { buckets.remove(key); }
    }
}
