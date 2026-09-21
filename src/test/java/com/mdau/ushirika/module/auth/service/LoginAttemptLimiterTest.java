package com.mdau.ushirika.module.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptLimiterTest {

    @Test
    void loginBlocksOnlyAfterTenFailures() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        for (int i = 0; i < LoginAttemptLimiter.LOGIN_MAX_FAILURES; i++) {
            assertFalse(limiter.isLoginBlocked("brian@example.com"), "attempt " + (i + 1) + " must still be allowed");
            limiter.recordLoginFailure("brian@example.com");
        }
        assertTrue(limiter.isLoginBlocked("brian@example.com"), "the 11th attempt must be blocked");
    }

    @Test
    void successClearsTheKey() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        for (int i = 0; i < LoginAttemptLimiter.LOGIN_MAX_FAILURES; i++) limiter.recordLoginFailure("a@b.com");
        assertTrue(limiter.isLoginBlocked("a@b.com"));

        limiter.clearLogin("a@b.com");

        assertFalse(limiter.isLoginBlocked("a@b.com"));
    }

    @Test
    void keysAreNormalised_caseAndWhitespace() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        for (int i = 0; i < LoginAttemptLimiter.LOGIN_MAX_FAILURES; i++) limiter.recordLoginFailure("John  Wanjala ");
        assertTrue(limiter.isLoginBlocked("john wanjala"));
        assertTrue(limiter.isLoginBlocked("JOHN WANJALA"));
    }

    @Test
    void differentAccountsAreIndependent() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        for (int i = 0; i < LoginAttemptLimiter.LOGIN_MAX_FAILURES; i++) limiter.recordLoginFailure("a@b.com");
        assertTrue(limiter.isLoginBlocked("a@b.com"));
        assertFalse(limiter.isLoginBlocked("c@d.com"));
    }

    @Test
    void peekingNeverConsumes() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        for (int i = 0; i < 100; i++) assertFalse(limiter.isLoginBlocked("a@b.com"));
        for (int i = 0; i < LoginAttemptLimiter.LOGIN_MAX_FAILURES - 1; i++) limiter.recordLoginFailure("a@b.com");
        assertFalse(limiter.isLoginBlocked("a@b.com"));
    }

    @Test
    void otpWindowIsSeparateAndStricter() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        for (int i = 0; i < LoginAttemptLimiter.OTP_MAX_FAILURES; i++) {
            assertFalse(limiter.isOtpBlocked("reset:a@b.com"));
            limiter.recordOtpFailure("reset:a@b.com");
        }
        assertTrue(limiter.isOtpBlocked("reset:a@b.com"));
        assertFalse(limiter.isLoginBlocked("reset:a@b.com"), "login and code windows must not share state");
        limiter.clearOtp("reset:a@b.com");
        assertFalse(limiter.isOtpBlocked("reset:a@b.com"));
    }
}
