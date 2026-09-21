package com.mdau.ushirika.module.auth.dto;

import java.time.Instant;

/**
 * Result of looking up an activation link. Never distinguishes "no such link" from any other
 * failure beyond the coarse {@code state}, and carries personal data only when VALID.
 *
 * state: "VALID" | "EXPIRED" | "USED" | "LOCKED" | "INVALID"
 */
public record ActivationLookupDto(
        String state,
        String firstName,
        String maskedEmail,
        Instant otpExpiresAt,
        Instant resendAvailableAt,
        int attemptsRemaining
) {
    public static ActivationLookupDto of(String state) {
        return new ActivationLookupDto(state, null, null, null, null, 0);
    }
}
