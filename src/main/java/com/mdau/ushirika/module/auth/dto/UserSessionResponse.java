package com.mdau.ushirika.module.auth.dto;

/**
 * What login/verify-email/magic-login/refresh return to the client now that the actual
 * access/refresh tokens travel only as httpOnly cookies (see AuthController) — the client never
 * sees the raw tokens, only the profile it already needed to render the app.
 */
public record UserSessionResponse(UserProfileDto user, long expiresIn) {
    public static UserSessionResponse from(AuthResponse auth) {
        return new UserSessionResponse(auth.user(), auth.expiresIn());
    }
}
