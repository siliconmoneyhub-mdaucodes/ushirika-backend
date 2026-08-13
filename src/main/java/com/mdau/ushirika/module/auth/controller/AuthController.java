package com.mdau.ushirika.module.auth.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.auth.dto.*;
import com.mdau.ushirika.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, verify email, login, refresh, logout, password reset")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new member account")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful. Check your email for the verification code."));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email with OTP — returns tokens on success")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        AuthResponse auth = authService.verifyEmail(req);
        return ResponseEntity.ok(ApiResponse.ok("Email verified successfully", auth));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend email verification OTP")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@RequestParam String email) {
        authService.resendVerificationOtp(email);
        return ResponseEntity.ok(ApiResponse.ok("Verification code resent. Check your email."));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        AuthResponse auth = authService.login(req);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", auth));
    }

    @PostMapping("/magic-login")
    @Operation(summary = "Exchange the one-time onboarding link token for a session — no password needed")
    public ResponseEntity<ApiResponse<AuthResponse>> magicLogin(@Valid @RequestBody MagicLoginRequest req) {
        AuthResponse auth = authService.magicLogin(req);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", auth));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        AuthResponse auth = authService.refresh(req);
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", auth));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest req) {
        authService.logout(req);
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
}
