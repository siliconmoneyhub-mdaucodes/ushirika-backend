package com.mdau.ushirika.module.member.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.dto.UserDto;
import com.mdau.ushirika.module.auth.dto.UserProfileDto;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.dto.AdminResetCredentialsRequest;
import com.mdau.ushirika.module.member.dto.CreateMemberRequest;
import com.mdau.ushirika.module.member.dto.UpdateMemberTierRequest;
import com.mdau.ushirika.module.member.dto.UpdateRoleRequest;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.dues.service.MembershipDuesService;
import com.mdau.ushirika.module.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final MemberProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final MembershipDuesService membershipDuesService;
    private final AuditLogService auditLogService;

    private static final int MAX_SUPERADMINS = 5;

    @Transactional(readOnly = true)
    public PagedResponse<UserDto> listUsers(Pageable pageable) {
        return PagedResponse.of(userRepository.findAll(pageable).map(UserDto::from));
    }

    /**
     * SUPERADMIN accounts are hidden from this directory for everyone except fellow
     * SUPERADMINs (up to {@link #MAX_SUPERADMINS} may exist — see updateRole's guard) —
     * ordinary ADMIN/LEADERSHIP users browsing this list should never see them.
     */
    @Transactional(readOnly = true)
    public PagedResponse<UserProfileDto> listMembersWithProfile(Pageable pageable) {
        User actor = currentUser();
        Page<User> page = userRepository.findAll(pageable);

        List<UserProfileDto> content = page.getContent().stream()
                .filter(u -> u.getRole() != UserRole.SUPERADMIN || actor.getRole() == UserRole.SUPERADMIN)
                .map(user -> {
                    MemberProfile profile = profileRepository.findByUser(user).orElse(null);
                    return UserProfileDto.from(user, profile, resolveDuesStatus(user, profile));
                })
                .toList();

        long hidden = page.getContent().size() - content.size();
        long total = page.getTotalElements() - hidden;
        int totalPages = pageable.getPageSize() == 0 ? 0 : (int) Math.ceil((double) total / pageable.getPageSize());

        return new PagedResponse<>(content, page.getNumber(), page.getSize(), total, totalPages, page.isLast());
    }

    @Transactional(readOnly = true)
    public UserDto getUser(UUID userId) {
        return UserDto.from(findById(userId));
    }

    /**
     * SUPERADMIN can change any user's role and official title.
     * Guards:
     * - Cannot demote or modify another SUPERADMIN
     * - Cannot promote anyone to SUPERADMIN once {@link #MAX_SUPERADMINS} already exist
     */
    @Transactional
    public UserDto updateRole(UUID userId, UpdateRoleRequest req) {
        User target = findById(userId);
        User actor = currentUser();

        if (target.getId().equals(actor.getId())) {
            throw new BadRequestException("You cannot change your own role.");
        }

        if (target.getRole() == UserRole.SUPERADMIN) {
            throw new ForbiddenException("The SUPERADMIN role cannot be modified.");
        }

        if (req.role() == UserRole.SUPERADMIN && userRepository.countByRole(UserRole.SUPERADMIN) >= MAX_SUPERADMINS) {
            throw new ForbiddenException("Cannot promote to SUPERADMIN — the maximum of " + MAX_SUPERADMINS + " has been reached.");
        }

        UserRole previousRole = target.getRole();
        target.setRole(req.role());
        target.setOfficialTitle(req.officialTitle());
        target.setCapabilities(req.capabilities() != null ? new HashSet<>(req.capabilities()) : new HashSet<>());
        userRepository.save(target);

        auditLogService.log(actor, "ROLE_CHANGED", "User", target.getId(),
                "Changed " + target.getFullName() + "'s role from " + previousRole + " to " + req.role()
                        + ", capabilities set to " + target.getCapabilities()
                        + " (by " + actor.getFullName() + ")");

        return UserDto.from(target);
    }

    /**
     * Activate or deactivate a user account.
     * Deactivated accounts cannot log in (isEnabled() = false).
     */
    @Transactional
    public UserDto setActive(UUID userId, boolean active) {
        User target = findById(userId);
        User actor = currentUser();

        if (target.getId().equals(actor.getId())) {
            throw new BadRequestException("You cannot deactivate your own account.");
        }

        if (target.getRole() == UserRole.SUPERADMIN) {
            throw new ForbiddenException("The SUPERADMIN account cannot be deactivated.");
        }

        target.setActive(active);
        userRepository.save(target);

        auditLogService.log(actor, active ? "ACCOUNT_ACTIVATED" : "ACCOUNT_DEACTIVATED", "User", target.getId(),
                (active ? "Activated " : "Deactivated ") + target.getFullName() + "'s account (by " + actor.getFullName() + ")");

        return UserDto.from(target);
    }

    /**
     * Update a member's contribution plan tier (Standard / Family / Patron).
     * Only meaningful for users who have an approved MemberProfile.
     */
    @Transactional
    public UserProfileDto updateTier(UUID userId, UpdateMemberTierRequest req) {
        User target = findById(userId);
        MemberProfile profile = profileRepository.findByUser(target)
                .orElseThrow(() -> new BadRequestException(
                        "This user does not have an approved member profile yet. " +
                        "Tier can only be set after membership approval."));

        profile.setMembershipTier(req.tier());
        profileRepository.save(profile);
        return UserProfileDto.from(target, profile, resolveDuesStatus(target, profile));
    }

    /**
     * Admin-initiated member creation. Bypasses the normal application flow.
     * Creates a fully verified, active member account and emails the credentials
     * to the member with a mandatory password-change notice.
     *
     * There is no checkout step in this path, so the one-time registration fee can
     * never actually be collected here -- req.waiveRegistrationFee() must be an explicit,
     * deliberate admin acknowledgement (not a silent default) that this member is being
     * created without that payment, audit-logged the same way approveMembership()'s waiver
     * is, so finance can still account for it instead of the member being invisible to
     * both the "paid" and "waived" buckets.
     */
    @Transactional
    public UserProfileDto createMember(CreateMemberRequest req) {
        log.info("[createMember] start — email={}", req.email());
        if (!req.waiveRegistrationFee()) {
            throw new BadRequestException(
                    "You must acknowledge that no registration fee will be collected for this member.");
        }
        if (userRepository.existsByEmail(req.email().toLowerCase())) {
            throw new ConflictException("An account with this email already exists.");
        }
        if (userRepository.existsByPhone(req.phone())) {
            throw new ConflictException("An account with this phone number already exists.");
        }

        String tempPassword = generateTempPassword();
        log.info("[createMember] building User");

        User user = User.builder()
                .firstName(req.firstName())
                .lastName(req.lastName())
                .email(req.email().toLowerCase())
                .phone(req.phone())
                .password(passwordEncoder.encode(tempPassword))
                .emailVerified(true)
                .active(true)
                .build();

        log.info("[createMember] saving User");
        user = userRepository.saveAndFlush(user);
        log.info("[createMember] User saved id={}", user.getId());

        String memberId = generateMemberId();
        log.info("[createMember] memberId={}", memberId);
        String tier = (req.tier() != null && !req.tier().isBlank()) ? req.tier() : "Standard";

        // Identity/address fields are left null — an admin-direct-created member fills
        // these in via their profile page, same as anyone whose onboarding is incomplete.
        log.info("[createMember] building MemberProfile");
        MemberProfile profile = MemberProfile.builder()
                .user(user)
                .memberId(memberId)
                .memberSince(LocalDate.now())
                .membershipTier(tier)
                .build();

        log.info("[createMember] saving MemberProfile");
        profileRepository.save(profile);
        log.info("[createMember] MemberProfile saved id={}", profile.getId());

        log.info("[createMember] calling createInitialDues");
        membershipDuesService.createInitialDues(user);
        log.info("[createMember] createInitialDues done");

        User admin = currentUser();
        auditLogService.log(admin, "REGISTRATION_FEE_WAIVED", "User", user.getId(),
                "Registration fee waived for " + user.getFullName()
                        + " — created directly by admin " + admin.getFullName() + " (no onboarding checkout)");

        try {
            sendWelcomeCredentials(user.getEmail(), user.getFirstName(), memberId, tempPassword);
        } catch (Exception e) {
            log.warn("Welcome email failed for {} — account created, credentials must be shared manually: {}", user.getEmail(), e.getMessage());
        }

        log.info("[createMember] building UserProfileDto");
        return UserProfileDto.from(user, profile, resolveDuesStatus(user, profile));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Mirrors UserController.me()'s dues-status resolution -- only meaningful for approved
     * MEMBER-role users (a memberId assigned). Without this, UserProfileDto.from(user, profile)
     * (the 2-arg overload) always passes a null duesStatus, which its status-derivation logic
     * treats identically to "no dues record" -- i.e. every approved member shows "inactive"
     * regardless of whether they've actually paid, which is exactly what the Member Directory
     * was doing before this fix.
     */
    private String resolveDuesStatus(User user, MemberProfile profile) {
        if (user.getRole() != UserRole.MEMBER || profile == null || profile.getMemberId() == null) {
            return null;
        }
        return membershipDuesService.getCurrentYearStatus(user).map(Enum::name).orElse(null);
    }

    private String generateMemberId() {
        int year = LocalDate.now().getYear();
        long sequence = profileRepository.countByMemberIdNotNull() + 1;
        return "UW-%d-%04d".formatted(year, sequence);
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$!";
        SecureRandom rand = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void sendWelcomeCredentials(String toEmail, String firstName, String memberId, String tempPassword) {
        String subject = "Welcome to Ushirika Welfare Organization — Your Member Account";
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:auto;color:#1a1a1a">
                  <h2 style="color:#007834">Welcome, %s!</h2>
                  <p>Your Ushirika Welfare Organization member account has been created by the administrator. You can now sign in to your member portal.</p>
                  <table style="border-collapse:collapse;width:100%%;margin:24px 0;border:1px solid #e5e7eb;border-radius:8px">
                    <tr style="background:#f9fafb">
                      <td style="padding:12px 16px;font-weight:600;width:160px">Member ID</td>
                      <td style="padding:12px 16px;font-family:monospace;font-weight:700">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:12px 16px;font-weight:600;border-top:1px solid #e5e7eb">Login Email</td>
                      <td style="padding:12px 16px;border-top:1px solid #e5e7eb">%s</td>
                    </tr>
                    <tr style="background:#f9fafb">
                      <td style="padding:12px 16px;font-weight:600;border-top:1px solid #e5e7eb">Temporary Password</td>
                      <td style="padding:12px 16px;border-top:1px solid #e5e7eb;font-family:monospace;font-weight:700;font-size:16px">%s</td>
                    </tr>
                  </table>
                  <div style="padding:16px;background:#fff3cd;border-left:4px solid #ffc107;border-radius:4px;margin-bottom:24px">
                    <strong>Important:</strong> This is a temporary password. Please change it immediately after your first login via <strong>Settings → Change Password</strong>.
                  </div>
                  <p><a href="https://ushirikacommunity.site/login" style="display:inline-block;padding:12px 24px;background:#007834;color:#fff;text-decoration:none;border-radius:24px;font-weight:600">Sign in to your portal</a></p>
                  <p style="margin-top:24px;color:#666;font-size:13px">Questions? Contact us at <a href="mailto:admin@ushirikawelfare.org">admin@ushirikawelfare.org</a></p>
                </div>
                """.formatted(firstName, memberId, toEmail, tempPassword);
        emailService.sendPlain(toEmail, firstName, subject, html);
    }

    /**
     * Superadmin force-reset of any user's email and/or password.
     * No current-password required. Cannot target another SUPERADMIN.
     */
    @Transactional
    public UserDto resetCredentials(UUID userId, AdminResetCredentialsRequest req) {
        if (req.newEmail() == null && req.newPassword() == null) {
            throw new BadRequestException("Provide at least one of newEmail or newPassword.");
        }

        User target = findById(userId);
        User actor = currentUser();

        if (target.getRole() == UserRole.SUPERADMIN) {
            throw new ForbiddenException("Cannot reset credentials for the SUPERADMIN account.");
        }

        String oldEmail = target.getEmail();

        if (req.newEmail() != null && !req.newEmail().isBlank()) {
            String normalized = req.newEmail().toLowerCase().trim();
            if (!normalized.equals(oldEmail) && userRepository.existsByEmail(normalized)) {
                throw new ConflictException("That email address is already in use by another account.");
            }
            target.setEmail(normalized);
            target.setEmailVerified(true);
        }

        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            target.setPassword(passwordEncoder.encode(req.newPassword()));
        }

        userRepository.save(target);

        // Notify the user at their new email (or old one if only password changed)
        String notifyEmail = target.getEmail();
        try {
            emailService.sendPlain(
                    notifyEmail, target.getFirstName(),
                    "Your Ushirika account credentials have been updated",
                    """
                    <div style="font-family:sans-serif;max-width:480px;margin:auto;color:#1a1a1a">
                      <h2 style="color:#007834">Account Credentials Updated</h2>
                      <p>Hi %s,</p>
                      <p>An administrator has updated your login credentials.</p>
                      %s
                      <p>If this was unexpected, contact <a href="mailto:admin@ushirikawelfare.org">admin@ushirikawelfare.org</a> immediately.</p>
                    </div>
                    """.formatted(
                            target.getFirstName(),
                            req.newPassword() != null
                                    ? "<p>A new password has been set. Please sign in and change it immediately.</p>"
                                    : ""
                    )
            );
        } catch (Exception e) {
            log.warn("Credential-reset notification failed for {}: {}", notifyEmail, e.getMessage());
        }

        auditLogService.log(actor, "CREDENTIALS_RESET", "User", target.getId(),
                "Reset credentials for " + target.getFullName() + " (by " + actor.getFullName() + ")"
                        + (req.newEmail() != null ? " — email changed to " + target.getEmail() : "")
                        + (req.newPassword() != null ? " — password reset" : ""));

        log.info("Superadmin reset credentials for user {} ({})", target.getId(), target.getEmail());
        return UserDto.from(target);
    }

    private User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
