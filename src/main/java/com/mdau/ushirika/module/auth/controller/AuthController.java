package com.mdau.ushirika.module.auth.controller;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.auth.dto.*;
import com.mdau.ushirika.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * The access/refresh tokens themselves never appear in a JSON response body — they travel only as
 * httpOnly cookies (see setSessionCookies/clearSessionCookies below), so an XSS payload running in
 * the page can't read them out of localStorage or a fetch response the way it could before this
 * migration. Two cookies, scoped differently:
 *   - uwf_at (access token)  — path "/api", sent on every API call, short-lived.
 *   - uwf_rt (refresh token) — path "/api/auth", sent only to these auth endpoints, long-lived.
 * SameSite=None + Secure is required because the frontend (ushirikacommunity.site) and backend
 * (railway.app) are on different sites — there's no way to make this a same-site cookie without
 * proxying the API through the frontend's own domain, which is out of scope here. The real CSRF
 * defense is the strict single-origin CORS allow-list (app.cors.allowed-origins) combined with
 * every mutating endpoint requiring Content-Type: application/json, which forces a CORS preflight
 * that a non-allow-listed origin can't pass — a cross-site <form> POST can't hit these endpoints at
 * all, since it can only send simple (non-JSON) request bodies.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, verify email, login, refresh, logout, password reset")
public class AuthController {

    private final AuthService authService;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:None}")
    private String cookieSameSite;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    private static final String ACCESS_COOKIE = "uwf_at";
    private static final String REFRESH_COOKIE = "uwf_rt";

    @PostMapping("/register")
    @Operation(summary = "Register a new member account")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful. Check your email for the verification code."));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email with OTP — starts a session on success")
    public ResponseEntity<ApiResponse<UserSessionResponse>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest req, HttpServletResponse response) {
        AuthResponse auth = authService.verifyEmail(req);
        setSessionCookies(response, auth);
        return ResponseEntity.ok(ApiResponse.ok("Email verified successfully", UserSessionResponse.from(auth)));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend email verification OTP")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@RequestParam String email) {
        authService.resendVerificationOtp(email);
        return ResponseEntity.ok(ApiResponse.ok("Verification code resent. Check your email."));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<ApiResponse<UserSessionResponse>> login(
            @Valid @RequestBody LoginRequest req, HttpServletResponse response) {
        AuthResponse auth = authService.login(req);
        setSessionCookies(response, auth);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", UserSessionResponse.from(auth)));
    }

    @PostMapping("/magic-login")
    @Operation(summary = "Exchange the one-time onboarding link token for a session — no password needed")
    public ResponseEntity<ApiResponse<UserSessionResponse>> magicLogin(
            @Valid @RequestBody MagicLoginRequest req, HttpServletResponse response) {
        AuthResponse auth = authService.magicLogin(req);
        setSessionCookies(response, auth);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", UserSessionResponse.from(auth)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh-token cookie for a fresh access token")
    public ResponseEntity<ApiResponse<UserSessionResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException("Not signed in.");
        }
        AuthResponse auth = authService.refresh(refreshToken);
        setSessionCookies(response, auth);
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", UserSessionResponse.from(auth)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current session")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        clearSessionCookies(response);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password-reset OTP (always returns 200 to prevent email enumeration)")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.ok(ApiResponse.ok("If an account exists with that email, a reset code has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using OTP from email")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(ApiResponse.ok("Password reset successfully. You can now log in."));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change password while logged in — requires current password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(req);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully. Please log in again with your new password."));
    }

    @PostMapping("/admin-entry/request")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Email a step-up confirmation code before entering the admin panel")
    public ResponseEntity<ApiResponse<Void>> requestAdminEntryOtp() {
        authService.requestAdminEntryOtp();
        return ResponseEntity.ok(ApiResponse.ok("Confirmation code sent. Check your email."));
    }

    @PostMapping("/admin-entry/verify")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Verify the admin-entry confirmation code")
    public ResponseEntity<ApiResponse<Void>> verifyAdminEntryOtp(@Valid @RequestBody VerifyAdminEntryOtpRequest req) {
        authService.verifyAdminEntryOtp(req.otp());
        return ResponseEntity.ok(ApiResponse.ok("Confirmed"));
    }

    // ── Cookie helpers ───────────────────────────────────────────────────────

    private void setSessionCookies(HttpServletResponse response, AuthResponse auth) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie(ACCESS_COOKIE, auth.accessToken(), "/api", Duration.ofMillis(auth.expiresIn() * 1000)).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie(REFRESH_COOKIE, auth.refreshToken(), "/api/auth", Duration.ofMillis(refreshTokenExpiryMs)).toString());
    }

    private void clearSessionCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(ACCESS_COOKIE, "", "/api", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_COOKIE, "", "/api/auth", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
