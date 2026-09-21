package com.mdau.ushirika.module.member.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.common.util.TextNormalizer;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.dto.UserDto;
import com.mdau.ushirika.module.auth.dto.UserProfileDto;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.auth.service.ActivationService;
import org.springframework.beans.factory.annotation.Value;
import com.mdau.ushirika.module.member.dto.AdminResetCredentialsRequest;
import com.mdau.ushirika.module.member.dto.BulkSetActiveRequest;
import com.mdau.ushirika.module.member.dto.BulkStatusResultDto;
import com.mdau.ushirika.module.member.dto.CreateMemberRequest;
import com.mdau.ushirika.module.member.dto.SetActiveRequest;
import com.mdau.ushirika.module.member.dto.UpdateMemberTierRequest;
import com.mdau.ushirika.module.member.dto.UpdateRoleRequest;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.enums.MemberStatus;
import com.mdau.ushirika.module.member.enums.MemberStatusReason;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.attendance.dto.FineDto;
import com.mdau.ushirika.module.attendance.enums.FineStatus;
import com.mdau.ushirika.module.attendance.service.FineService;
import com.mdau.ushirika.module.benevolence.service.BenevolenceEnrollmentService;
import com.mdau.ushirika.module.dues.service.MembershipDuesService;
import com.mdau.ushirika.module.member.dto.MemberFinancialSummaryDto;
import com.mdau.ushirika.module.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final BenevolenceEnrollmentService benevolenceEnrollmentService;
    private final FineService fineService;
    private final AuditLogService auditLogService;
    private final MemberStatusChangeService statusChangeService;
    private final ActivationService activationService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

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

    /** Dues balance, Benevolence status/balance, and outstanding fines for one member --
     * backs the Members admin detail panel so an admin can see financial standing without
     * leaving the page to cross-reference Dues/Benevolence/Fines separately. */
    @Transactional(readOnly = true)
    public MemberFinancialSummaryDto getFinancialSummary(UUID userId) {
        User user = findById(userId);

        BigDecimal duesBalance = membershipDuesService.outstandingBalance(user);
        var currentDue = membershipDuesService.currentYearOutstandingDue(user);
        Integer duesYear = currentDue.map(d -> d.getYear()).orElse(null);
        LocalDate duesDueDate = currentDue.map(d -> d.getDueDate()).orElse(null);
        String duesStatus = currentDue.map(d -> d.getStatus().name()).orElse(null);

        BenevolenceEnrollmentService.EnrollmentBalance benBalance = benevolenceEnrollmentService.outstandingBalance(user);
        String benevolenceStatus = benBalance != null ? benBalance.status() : "NOT_ENROLLED";
        BigDecimal benevolenceBalance = benBalance != null ? benBalance.balance() : null;

        List<FineDto> outstandingFines = fineService.getFinesForMember(userId).stream()
                .filter(f -> FineStatus.PENDING.name().equals(f.status()))
                .toList();
        BigDecimal finesTotal = outstandingFines.stream()
                .map(FineDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MemberFinancialSummaryDto(duesBalance, duesYear, duesDueDate, duesStatus,
                benevolenceStatus, benevolenceBalance, outstandingFines, finesTotal);
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

    private static final java.util.Set<MemberStatusReason> ADMIN_SELECTABLE_DEACTIVATE_REASONS =
            java.util.Set.of(MemberStatusReason.ADMIN_MANUAL, MemberStatusReason.VOLUNTARY_EXIT, MemberStatusReason.TERMINATED);

    /**
     * Activate or deactivate a user account. Deactivated accounts cannot log in
     * (isEnabled() = false). A reason is always required and the member is notified by email +
     * in-app with that exact reason -- previously this was a silent boolean flip with no reason
     * captured anywhere the member could see.
     */
    @Transactional
    public UserDto setActive(UUID userId, SetActiveRequest req) {
        User target = findById(userId);
        User actor = currentUser();

        if (target.getId().equals(actor.getId())) {
            throw new BadRequestException("You cannot deactivate your own account.");
        }

        if (target.getRole() == UserRole.SUPERADMIN) {
            throw new ForbiddenException("The SUPERADMIN account cannot be deactivated.");
        }

        MemberStatusReason reason;
        if (req.active()) {
            reason = MemberStatusReason.REINSTATED;
        } else {
            if (req.reason() == null || !ADMIN_SELECTABLE_DEACTIVATE_REASONS.contains(req.reason())) {
                throw new BadRequestException("Reason must be one of: ADMIN_MANUAL, VOLUNTARY_EXIT, TERMINATED.");
            }
            reason = req.reason();
        }

        MemberStatus previousStatus = MemberStatusChangeService.statusOf(target.isActive(), target.isMembershipCeased());
        target.setActive(req.active());
        userRepository.save(target);
        MemberStatus newStatus = MemberStatusChangeService.statusOf(target.isActive(), target.isMembershipCeased());
        statusChangeService.record(target, previousStatus, newStatus, reason, actor, req.notes());
        statusChangeService.notifyStatusChange(target, newStatus, reason, req.notes());

        auditLogService.log(actor, req.active() ? "ACCOUNT_ACTIVATED" : "ACCOUNT_DEACTIVATED", "User", target.getId(),
                (req.active() ? "Activated " : "Deactivated ") + target.getFullName() + "'s account (by "
                        + actor.getFullName() + ") — " + req.notes());

        // Reactivating alone used to leave an OVERDUE dues row untouched -- the very next
        // nightly assessOverdue() run would see the member active again with the same unpaid
        // due and immediately deactivate them a second time, silently undoing what the admin
        // just did. A 7-day grace window gives them a real chance to actually pay before that
        // can happen again; a no-op if they weren't inactive for dues in the first place.
        if (req.active()) {
            membershipDuesService.resetOverdueDuesToGracePeriod(target, 7);
        }

        return UserDto.from(target);
    }

    /**
     * Bulk activate/deactivate -- runs every id through the exact same setActive() path above
     * (one at a time, each in its own guard check) so every safety rule already there applies
     * identically per member: can't touch SUPERADMIN, can't deactivate yourself, dues grace
     * reset on reactivate, audit log entry, and the member's own notification email. A member
     * already in the requested state is skipped rather than reprocessed, so bulk-activating a
     * batch that happens to include an already-active member doesn't spam them with a
     * redundant "reinstated" email. One failure never aborts the rest of the batch.
     */
    @Transactional
    public BulkStatusResultDto bulkSetActive(BulkSetActiveRequest req) {
        int succeeded = 0;
        List<String> failures = new java.util.ArrayList<>();
        for (UUID id : req.userIds()) {
            User target = userRepository.findById(id).orElse(null);
            if (target == null) {
                failures.add(id + ": not found");
                continue;
            }
            if (target.isActive() == req.active()) {
                failures.add(target.getEmail() + ": already " + (req.active() ? "active" : "inactive") + " -- skipped");
                continue;
            }
            try {
                setActive(id, new SetActiveRequest(req.active(), req.reason(), req.notes()));
                succeeded++;
            } catch (Exception e) {
                failures.add(target.getEmail() + ": " + e.getMessage());
            }
        }
        return new BulkStatusResultDto(succeeded, failures);
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
        auditLogService.log(currentUser(), "MEMBER_TIER_CHANGED", "User", target.getId(),
                "Set " + target.getFullName() + "'s membership tier to " + req.tier());
        return UserProfileDto.from(target, profile, resolveDuesStatus(target, profile));
    }

    /**
     * Admin-initiated member creation. Bypasses the normal application flow.
     * Creates a fully verified, active member account and emails the member a setup link +
     * one-time code (account activation) so they choose their own password -- no password is
     * ever generated for, or emailed to, them.
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
        String email = TextNormalizer.normalizeEmail(req.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        if (userRepository.existsByPhone(req.phone())) {
            throw new ConflictException("An account with this phone number already exists.");
        }

        log.info("[createMember] building User");

        // No password is ever emailed. The stored one is a random, never-disclosed placeholder; the
        // member reaches the account through the activation link + code and chooses their own.
        User user = User.builder()
                .firstName(TextNormalizer.titleCase(req.firstName()))
                .middleName(TextNormalizer.titleCase(req.middleName()))
                .lastName(TextNormalizer.titleCase(req.lastName()))
                .email(email)
                .phone(req.phone())
                .password(passwordEncoder.encode(generateUnusablePassword()))
                .mustSetPassword(true)
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
            ActivationService.IssuedActivation activation = activationService.issue(user);
            emailService.sendMemberActivationInvite(user.getEmail(), user.getFirstName(), memberId,
                    siteUrl + "/activate?t=" + activation.rawToken(), activation.rawOtp(), ActivationService.TOKEN_TTL_HOURS);
        } catch (Exception e) {
            log.warn("Setup email failed for {} — account created; use the applicant 'request a new setup link' "
                    + "path or a password reset to get them in: {}", user.getEmail(), e.getMessage());
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

    /** Random placeholder stored as a new account's password -- never emailed or shown to anyone.
     *  64 characters, safely under BCrypt's 72-byte input limit. */
    private String generateUnusablePassword() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
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
            // An admin-chosen password is a stop-gap: flag the account so the user is required to
            // pick their own.
            target.setMustSetPassword(true);
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
                                    ? "<p>A new password was set on your account by an administrator. Please sign in and "
                                      + "choose your own password straight away.</p>"
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
