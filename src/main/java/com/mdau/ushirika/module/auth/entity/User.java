package com.mdau.ushirika.module.auth.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.enums.OfficialTitle;
import com.mdau.ushirika.module.auth.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_role",         columnList = "role"),
        @Index(name = "idx_users_role_active",  columnList = "role, active"),
        @Index(name = "idx_users_active",       columnList = "active"),
        @Index(name = "idx_users_created_at",   columnList = "created_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity implements UserDetails {

    @Column(name = "email", unique = true, nullable = false, length = 150)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone", unique = true, nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.MEMBER;

    /**
     * Organizational title — independent of role.
     * Set by SUPERADMIN. A member can hold a title without approval power.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "official_title", length = 30)
    private OfficialTitle officialTitle;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "email_verification_otp", length = 6)
    private String emailVerificationOtp;

    @Column(name = "email_verification_otp_expiry")
    private LocalDateTime emailVerificationOtpExpiry;

    @Column(name = "password_reset_otp", length = 6)
    private String passwordResetOtp;

    @Column(name = "password_reset_otp_expiry")
    private LocalDateTime passwordResetOtpExpiry;

    /**
     * One-time magic-login token issued alongside the temp password when an application's
     * form is sent — lets the "Continue Your Application" email button log the applicant
     * straight in instead of requiring them to type the emailed credentials. Cleared after
     * first use or once the applicant becomes a full MEMBER.
     */
    @Column(name = "onboarding_login_token", length = 64)
    private String onboardingLoginToken;

    @Column(name = "onboarding_login_token_expiry")
    private LocalDateTime onboardingLoginTokenExpiry;

    /** SUPERADMIN can deactivate any account without deleting it. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Set by the attendance engine when a member misses two consecutive
     * quarterly meetings. Distinct from suspension (admin action).
     * When true, active is also set to false to block login.
     */
    @Column(name = "membership_ceased", nullable = false)
    @Builder.Default
    private boolean membershipCeased = false;

    // ── UserDetails ───────────────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active && emailVerified;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isSuperAdmin() {
        return role == UserRole.SUPERADMIN;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN || role == UserRole.SUPERADMIN;
    }

    public boolean isFinancialAdmin() {
        return role == UserRole.FINANCIAL_ADMIN;
    }

    public boolean isFinancialOfficial() {
        return role == UserRole.FINANCIAL_OFFICIAL;
    }

    public boolean hasFinancialAccess() {
        return role == UserRole.FINANCIAL_ADMIN || role == UserRole.FINANCIAL_OFFICIAL
                || role == UserRole.ADMIN || role == UserRole.SUPERADMIN;
    }
}
