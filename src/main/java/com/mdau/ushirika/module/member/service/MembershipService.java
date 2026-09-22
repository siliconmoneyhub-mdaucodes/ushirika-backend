package com.mdau.ushirika.module.member.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.common.util.TextNormalizer;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.auth.service.ActivationService;
import com.mdau.ushirika.module.member.dto.*;
import com.mdau.ushirika.module.member.entity.ApplicationApproval;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.enums.ApprovalDecision;
import com.mdau.ushirika.module.member.repository.ApplicationApprovalRepository;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.member.repository.MembershipApplicationRepository;
import com.mdau.ushirika.module.dues.service.MembershipDuesService;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.payment.enums.PaymentBasketLedger;
import com.mdau.ushirika.module.payment.enums.PaymentStatus;
import com.mdau.ushirika.module.payment.repository.PaymentBasketRepository;
import com.mdau.ushirika.module.program.service.ProgramApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipApplicationRepository applicationRepository;
    private final MemberProfileRepository profileRepository;
    private final ApplicationApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final MembershipDuesService membershipDuesService;
    private final PasswordEncoder passwordEncoder;
    private final PaymentBasketRepository paymentBasketRepository;
    private final ProgramApplicationService programApplicationService;
    private final AuditLogService auditLogService;
    private final ActivationService activationService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    private static final int ONBOARDING_LOGIN_TOKEN_HOURS = 48;
    /** Mirrors ActivationService.TOKEN_TTL_HOURS -- stated in the invite email copy. */
    private static final int ACTIVATION_TOKEN_HOURS = 72;

    // ------------------------------------------------------------------ Member

    /**
     * Create or update a DRAFT application.
     * One active application per user — rejected users may reapply.
     */
    @Transactional
    public ApplicationTrackDto saveApplication(MembershipApplicationRequest req) {
        User user = currentUser();

        List<ApplicationStatus> activeStatuses = List.of(
                ApplicationStatus.DRAFT,
                ApplicationStatus.SUBMITTED,
                ApplicationStatus.FORM_SENT,
                ApplicationStatus.ONBOARDING_IN_PROGRESS,
                ApplicationStatus.PAYMENT_SUBMITTED,
                ApplicationStatus.APPROVED
        );
        applicationRepository.findByUser(user).ifPresent(existing -> {
            if (activeStatuses.contains(existing.getStatus())) {
                throw new ConflictException(
                        "You already have an active application (ref: " + existing.getReferenceNumber() + "). " +
                        "You may only reapply after a rejection.");
            }
        });

        if (profileRepository.existsByIdNumber(req.idNumber())) {
            profileRepository.findByUser(user).ifPresent(p -> {
                if (!p.getIdNumber().equals(req.idNumber())) {
                    throw new ConflictException("National ID number is already registered.");
                }
            });
            if (profileRepository.findByUser(user).isEmpty()) {
                throw new ConflictException("National ID number is already registered.");
            }
        }

        MemberProfile profile = profileRepository.findByUser(user)
                .orElse(MemberProfile.builder().user(user).build());
        profile.setIdNumber(req.idNumber());
        profile.setDateOfBirth(req.dateOfBirth());
        profile.setGender(req.gender());
        profile.setMaritalStatus(req.maritalStatus());
        profile.setSpouseName(req.spouseName());
        profile.setChildrenJson(serializeChildren(req.children()));
        profile.setOccupation(req.occupation());
        profile.setEmployer(req.employer());
        profile.setReference1Name(req.reference1Name());
        profile.setReference1MemberId(req.reference1MemberId());
        profile.setReference2Name(req.reference2Name());
        profile.setReference2MemberId(req.reference2MemberId());
        profile.setHeardAboutUs(req.heardAboutUs());
        profileRepository.save(profile);

        MembershipApplication application = applicationRepository.findByUser(user)
                .filter(a -> a.getStatus() == ApplicationStatus.REJECTED || a.getStatus() == ApplicationStatus.DRAFT)
                .orElse(MembershipApplication.builder()
                        .user(user)
                        .referenceNumber(generateReferenceNumber())
                        .build());

        application.setDocumentUrls(req.documentUrls() != null ? req.documentUrls() : List.of());
        application.setStatus(ApplicationStatus.DRAFT);
        applicationRepository.save(application);

        return ApplicationTrackDto.from(application, profile.getMemberId());
    }

    @Transactional
    public ApplicationTrackDto submitApplication() {
        User user = currentUser();
        MembershipApplication application = applicationRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("No application found. Please fill in your details first."));

        if (application.getStatus() != ApplicationStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT applications can be submitted. Current status: " + application.getStatus());
        }

        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setSubmittedAt(LocalDateTime.now());
        applicationRepository.save(application);

        notifyAdminsOfNewApplication(application, user);
        sendApplicantConfirmation(user.getEmail(), user.getFullName(), application.getReferenceNumber());

        return ApplicationTrackDto.from(application,
                profileRepository.findByUser(user).map(MemberProfile::getMemberId).orElse(null));
    }

    @Transactional(readOnly = true)
    public ApplicationTrackDto getMyApplication() {
        User user = currentUser();
        MembershipApplication app = applicationRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("You have not submitted a membership application."));
        String memberId = profileRepository.findByUser(user).map(MemberProfile::getMemberId).orElse(null);
        return ApplicationTrackDto.from(app, memberId);
    }

    @Transactional(readOnly = true)
    public ApplicationTrackDto trackByReference(String referenceNumber) {
        MembershipApplication app = applicationRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found for reference: " + referenceNumber));
        String memberId = app.getUser() != null
                ? profileRepository.findByUser(app.getUser()).map(MemberProfile::getMemberId).orElse(null)
                : null;
        return ApplicationTrackDto.from(app, memberId);
    }

    @Transactional
    public ApplicationTrackDto submitPublicApplication(PublicMembershipApplicationRequest req) {
        String firstName = TextNormalizer.titleCase(req.firstName());
        String middleName = TextNormalizer.titleCase(req.middleName());
        String lastName = TextNormalizer.titleCase(req.lastName());
        String city = TextNormalizer.titleCase(req.city());
        String address = req.street() + ", " + city + ", " + req.state().label() + " " + req.zipCode();
        MembershipApplication application = MembershipApplication.builder()
                .referenceNumber(generateReferenceNumber())
                .applicantName(firstName + " " + lastName)
                .applicantMiddleName(middleName)
                .applicantEmail(TextNormalizer.normalizeEmail(req.email()))
                .applicantPhone(req.phone())
                .applicantCounty(req.kenyaCounty())
                .applicantSubtribe(req.subtribe())
                .applicantEligibility(req.eligibility())
                .applicantAddress(address)
                .status(ApplicationStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .build();
        applicationRepository.save(application);
        notifyAdminsOfPublicApplication(application);
        sendApplicantConfirmation(application.getApplicantEmail(), application.getApplicantName(), application.getReferenceNumber());
        return ApplicationTrackDto.from(application, null);
    }

    // ------------------------------------------------------------------ Admin

    @Transactional(readOnly = true)
    public PagedResponse<AdminApplicationDto> listApplications(ApplicationStatus status, Pageable pageable, boolean isSuperAdmin) {
        Page<MembershipApplication> page = status != null
                ? applicationRepository.findAllByStatus(status, pageable)
                : applicationRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PagedResponse.of(page.map(a -> AdminApplicationDto.from(a, isSuperAdmin)));
    }

    @Transactional(readOnly = true)
    public AdminApplicationDto getApplication(UUID id, boolean isSuperAdmin) {
        return AdminApplicationDto.from(findApplicationById(id), isSuperAdmin);
    }

    /** Only REJECTED is accepted here now — accepting an application is done via {@link #sendForm}. */
    @Transactional
    public AdminApplicationDto review(UUID applicationId, AdminReviewRequest req, boolean isSuperAdmin) {
        User admin = currentUser();
        MembershipApplication application = findApplicationById(applicationId);

        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new BadRequestException("This application has already been " + application.getStatus().name().toLowerCase() + " and cannot be changed.");
        }

        if (req.decision() == ApprovalDecision.APPROVED) {
            throw new BadRequestException(
                    "Direct approval is no longer supported. Use \"Send Form\" to accept this application, " +
                    "then approve membership once the registration fee payment has been verified.");
        }

        // Record the decision for audit trail
        ApplicationApproval approval = ApplicationApproval.builder()
                .application(application)
                .admin(admin)
                .decision(req.decision())
                .comment(req.comment())
                .decidedAt(LocalDateTime.now())
                .build();
        approvalRepository.saveAndFlush(approval);

        applyRejection(application);
        applicationRepository.save(application);

        auditLogService.logAbout(admin, "APPLICATION_REJECTED", "MembershipApplication", application.getId(),
                applicantName(application), application.getReferenceNumber(),
                "Application " + application.getReferenceNumber() + " from " + applicantLabel(application)
                        + " rejected by " + admin.getFullName());

        return AdminApplicationDto.from(application, isSuperAdmin);
    }

    /**
     * Dismiss an application as invalid — the intended tool for a duplicate or a wrong-email
     * entry, where "reject" is both semantically wrong and (once the form has been sent)
     * unavailable. Any applicant account that was auto-created at send-form time and never
     * progressed is deleted here so its UNIQUE email/phone are freed and a corrected application
     * can go through. Refuses when there's real progress to lose (payment submitted, onboarding
     * finished, already an approved member, or the account is no longer a bare APPLICANT).
     */
    @Transactional
    public AdminApplicationDto voidApplication(UUID applicationId, boolean isSuperAdmin, String reason) {
        User admin = currentUser();
        MembershipApplication application = findApplicationById(applicationId);
        ApplicationStatus status = application.getStatus();

        if (status == ApplicationStatus.VOIDED) {
            throw new BadRequestException("This application has already been voided.");
        }
        if (status == ApplicationStatus.APPROVED) {
            throw new BadRequestException(
                    "This applicant is already an approved member — voiding the application would leave their "
                    + "membership inconsistent. Resolve this one manually.");
        }
        if (status == ApplicationStatus.PAYMENT_SUBMITTED
                || (status == ApplicationStatus.ONBOARDING_IN_PROGRESS && isOnboardingComplete(application))) {
            throw new BadRequestException(
                    "This applicant has already submitted payment or finished onboarding — voiding would discard "
                    + "real progress. Resolve this one manually.");
        }

        // Captured before we (maybe) detach the account below, so the audit line still names them.
        String who = applicantLabel(application);
        String whoName = applicantName(application);
        String whoRef = application.getReferenceNumber();

        User applicant = application.getUser();
        boolean removedAccount = false;
        if (applicant != null) {
            if (applicant.getRole() != UserRole.APPLICANT) {
                throw new BadRequestException(
                        "The account linked to this application is no longer a plain applicant (role: "
                        + applicant.getRole() + "). Void refused — resolve this one manually.");
            }
            // Detach first so the FK doesn't block the delete, then remove the bare account +
            // its empty profile to release the unique email/phone.
            application.setUser(null);
            applicationRepository.saveAndFlush(application);
            try {
                profileRepository.findByUser(applicant).ifPresent(p -> {
                    profileRepository.delete(p);
                    profileRepository.flush();
                });
                userRepository.delete(applicant);
                userRepository.flush();
                removedAccount = true;
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                throw new ConflictException(
                        "Couldn't remove the applicant's account automatically — it has other linked records. "
                        + "This one needs manual cleanup.");
            }
        }

        String note = (reason != null && !reason.isBlank()) ? reason.trim() : null;
        application.setStatus(ApplicationStatus.VOIDED);
        application.setReviewedAt(LocalDateTime.now());
        application.setRejectionReason(note != null ? note : "Voided by an administrator.");
        applicationRepository.save(application);

        auditLogService.logAbout(admin, "APPLICATION_VOIDED", "MembershipApplication", application.getId(),
                whoName, whoRef,
                "Application " + application.getReferenceNumber() + " from " + who + " voided by " + admin.getFullName()
                + (removedAccount ? " — linked applicant account removed" : "")
                + (note != null ? " — reason: " + note : ""));

        return AdminApplicationDto.from(application, isSuperAdmin);
    }

    /** "Name &lt;email&gt;" for an application — works for both public and logged-in applicants.
     * Used so audit-log lines name the person the action was about, not just a reference number. */
    private String applicantLabel(MembershipApplication app) {
        String name = app.getUser() != null ? app.getUser().getFullName() : app.getApplicantName();
        String email = app.getUser() != null ? app.getUser().getEmail() : app.getApplicantEmail();
        if (name == null || name.isBlank()) name = "unnamed applicant";
        return (email != null && !email.isBlank()) ? name + " <" + email + ">" : name;
    }

    /** Just the display name — for the structured audit targetLabel. */
    private String applicantName(MembershipApplication app) {
        String name = app.getUser() != null ? app.getUser().getFullName() : app.getApplicantName();
        return (name == null || name.isBlank()) ? "unnamed applicant" : name;
    }

    /** Same seven checkpoints as {@link #requireOnboardingComplete}, as a plain boolean. */
    private boolean isOnboardingComplete(MembershipApplication a) {
        return a.getEmailReverifiedAt() != null
                && a.getPhotoSubmittedAt() != null
                && a.getIdentityInfoSubmittedAt() != null
                && a.getAddressInfoSubmittedAt() != null
                && a.getKinContactsSubmittedAt() != null
                && a.getConstitutionAcceptedAt() != null
                && a.getBylawsAcceptedAt() != null;
    }

    /**
     * Admin accepts the application in principle: creates (or demotes) the applicant's
     * account to APPLICANT role and emails them onboarding login credentials. This does
     * NOT grant membership — see {@link #approveMembership}.
     *
     * waiveRegistrationFee marks the application as fee-exempt right now, at send-form time —
     * for a real-world member who was already part of the organization before the platform
     * existed. The onboarding wizard reads this flag and skips the Registration Fee step
     * entirely for them, so they never see a Stripe checkout prompt they shouldn't have to deal
     * with. A brand-new applicant leaves this false and goes through the normal paid flow.
     */
    @Transactional
    public AdminApplicationDto sendForm(UUID applicationId, boolean isSuperAdmin, boolean waiveRegistrationFee) {
        MembershipApplication application = findApplicationById(applicationId);

        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new BadRequestException(
                    "Only SUBMITTED applications can have the form sent. Current status: " + application.getStatus());
        }

        User user = application.getUser();
        String applicantEmail;
        String applicantFirstName;

        if (user == null) {
            // Public/anonymous applicant — create their account now, scoped to APPLICANT.
            String email = application.getApplicantEmail();
            if (email == null || email.isBlank()) {
                throw new BadRequestException("Cannot send the form — this application has no email address on record.");
            }
            email = email.toLowerCase().trim();
            // Both email and phone are UNIQUE on the users table. Check each up-front so the admin
            // gets a plain, actionable message instead of a raw DB constraint violation surfacing
            // as the generic "a record with this information already exists".
            if (userRepository.existsByEmail(email)) {
                throw new ConflictException(
                        "Cannot send the form — an account with the email " + email + " already exists. "
                        + "This applicant most likely has a duplicate application; void that one, then send the form from this one.");
            }
            String applicantPhone = application.getApplicantPhone();
            if (applicantPhone == null || applicantPhone.isBlank()) {
                throw new BadRequestException("Cannot send the form — this application has no phone number on record.");
            }
            if (userRepository.existsByPhone(applicantPhone)) {
                throw new ConflictException(
                        "Cannot send the form — an account with the phone number " + applicantPhone + " already exists. "
                        + "This applicant most likely has a duplicate application; void that one, then send the form from this one.");
            }

            String fullName = application.getApplicantName() != null ? application.getApplicantName().trim() : "Applicant";
            String[] parts = fullName.split(" ", 2);
            String firstName = parts[0];
            String lastName  = parts.length > 1 ? parts[1] : "";

            User newUser = User.builder()
                    .firstName(firstName)
                    .middleName(application.getApplicantMiddleName())
                    .lastName(lastName)
                    .email(email)
                    .phone(applicantPhone)
                    .password(passwordEncoder.encode(generateUnusablePassword()))
                    .mustSetPassword(true)
                    .role(UserRole.APPLICANT)
                    .emailVerified(true)
                    .active(true)
                    .build();
            newUser = userRepository.saveAndFlush(newUser);

            // Bare profile — identity/address/kin fields are collected during the
            // onboarding wizard; memberId/memberSince/tier are assigned at final approval.
            MemberProfile profile = MemberProfile.builder()
                    .user(newUser)
                    .build();
            profileRepository.save(profile);

            application.setUser(newUser);
            user = newUser;
            applicantEmail = email;
            applicantFirstName = firstName;
        } else {
            // Applied while logged in — demote to APPLICANT. Their existing password is left alone
            // (they already have a working one); they only need to be prompted to choose one of
            // their own if they never did via the activation flow.
            user.setRole(UserRole.APPLICANT);
            if (user.getPasswordSetAt() == null) {
                user.setMustSetPassword(true);
            }
            userRepository.save(user);
            applicantEmail = user.getEmail();
            applicantFirstName = user.getFirstName();
        }

        String loginToken = generateLoginToken();
        user.setOnboardingLoginToken(loginToken);
        user.setOnboardingLoginTokenExpiry(LocalDateTime.now().plusHours(ONBOARDING_LOGIN_TOKEN_HOURS));
        user.setOnboardingLoginTokenUses(0);
        userRepository.save(user);

        application.setStatus(ApplicationStatus.FORM_SENT);
        application.setFormSentAt(LocalDateTime.now());
        application.setReviewedAt(LocalDateTime.now());

        User admin = currentUser();
        if (waiveRegistrationFee) {
            application.setRegistrationFeeWaived(true);
            application.setRegistrationFeeWaivedAt(LocalDateTime.now());
            application.setRegistrationFeeWaivedBy(admin.getFullName());
        }
        applicationRepository.save(application);

        // No password is emailed any more: the applicant activates their account (link + one-time
        // code) and chooses their own. The legacy magic-login token above is still issued so onboarding
        // emails already sitting in inboxes, and the /login?token= handler, keep working.
        ActivationService.IssuedActivation activation = activationService.issue(user);
        emailService.sendActivationInvite(applicantEmail, applicantFirstName,
                siteUrl + "/activate?t=" + activation.rawToken(), activation.rawOtp(), ACTIVATION_TOKEN_HOURS);
        log.info("Form sent for application {} — applicant={}{}", application.getReferenceNumber(), applicantEmail,
                waiveRegistrationFee ? " (registration fee pre-waived)" : "");

        auditLogService.logAbout(admin, "FORM_SENT", "MembershipApplication", application.getId(),
                applicantName(application), application.getReferenceNumber(),
                "Onboarding form sent to " + applicantLabel(application)
                        + " (application " + application.getReferenceNumber() + ") by " + admin.getFullName());
        if (waiveRegistrationFee) {
            auditLogService.logAbout(admin, "REGISTRATION_FEE_WAIVED", "MembershipApplication", application.getId(),
                    applicantName(application), application.getReferenceNumber(),
                    "Registration fee pre-waived at send-form for " + applicantLabel(application)
                            + " (application " + application.getReferenceNumber() + ") by " + admin.getFullName());
        }

        return AdminApplicationDto.from(application, isSuperAdmin);
    }

    /**
     * Recovery path for an applicant who lost their onboarding email or let the setup link
     * expire mid-onboarding. Re-issues a fresh activation link + code (and the legacy magic-login
     * token) without resetting any onboarding progress already saved (email verification,
     * additional info, bylaws, programs). The applicant's password is NEVER touched -- an
     * applicant who already chose their own password keeps it.
     */
    @Transactional
    public AdminApplicationDto resendFormCredentials(UUID applicationId, boolean isSuperAdmin) {
        MembershipApplication application = findApplicationById(applicationId);

        if (application.getStatus() != ApplicationStatus.FORM_SENT
                && application.getStatus() != ApplicationStatus.ONBOARDING_IN_PROGRESS) {
            throw new BadRequestException(
                    "Can only resend onboarding credentials to an applicant currently mid-onboarding. Current status: " + application.getStatus());
        }

        User user = application.getUser();
        if (user == null) {
            throw new BadRequestException("No account exists yet for this application — use Send Form instead.");
        }

        // A-4 fix: the password is deliberately NOT reset here.
        String loginToken = generateLoginToken();
        user.setOnboardingLoginToken(loginToken);
        user.setOnboardingLoginTokenExpiry(LocalDateTime.now().plusHours(ONBOARDING_LOGIN_TOKEN_HOURS));
        user.setOnboardingLoginTokenUses(0);
        userRepository.save(user);

        ActivationService.IssuedActivation activation = activationService.issue(user);
        emailService.sendActivationInvite(user.getEmail(), user.getFirstName(),
                siteUrl + "/activate?t=" + activation.rawToken(), activation.rawOtp(), ACTIVATION_TOKEN_HOURS,
                user.getPasswordSetAt() != null);
        log.info("Account setup link resent for application {} — applicant={}", application.getReferenceNumber(), user.getEmail());

        User admin = currentUser();
        auditLogService.logAbout(admin, "FORM_CREDENTIALS_RESENT", "MembershipApplication", application.getId(),
                applicantName(application), application.getReferenceNumber(),
                "Account setup link re-sent to " + applicantLabel(application)
                        + " (application " + application.getReferenceNumber() + ") by " + admin.getFullName()
                        + " (password NOT reset)");

        return AdminApplicationDto.from(application, isSuperAdmin);
    }

    /**
     * Final step: grants full membership once the applicant's onboarding is complete and either
     * their registration fee payment has been verified, or an admin has explicitly waived it
     * (the migration path for real-world members who joined before the platform existed — they
     * still complete identity/address/next-of-kin and sign the constitution/bylaws, just skip
     * Stripe checkout). Flips the account's role from APPLICANT to MEMBER — same login
     * credentials, no new account issued.
     */
    @Transactional
    public AdminApplicationDto approveMembership(UUID applicationId, boolean isSuperAdmin, boolean waiveRegistrationFee) {
        MembershipApplication application = findApplicationById(applicationId);
        User admin = currentUser();

        User user = application.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("No applicant account linked to this application.");
        }

        boolean feePaid = paymentBasketRepository.existsByMemberIdAndStatusAndLines_Ledger(
                user.getId(), PaymentStatus.SUCCESS, PaymentBasketLedger.REGISTRATION_FEE);
        // Either an ad-hoc choice made right now, or already pre-waived back at send-form time
        // (see MembershipService#sendForm) — either way, a real payment on file always wins.
        boolean waiving = (waiveRegistrationFee || application.isRegistrationFeeWaived()) && !feePaid;

        if (waiving) {
            if (application.getStatus() != ApplicationStatus.ONBOARDING_IN_PROGRESS
                    && application.getStatus() != ApplicationStatus.PAYMENT_SUBMITTED) {
                throw new BadRequestException(
                        "Only applications that have started onboarding can be approved. Current status: " + application.getStatus());
            }
            requireOnboardingComplete(application);
        } else {
            if (application.getStatus() != ApplicationStatus.PAYMENT_SUBMITTED) {
                throw new BadRequestException(
                        "Only applications with a submitted registration payment can be approved. Current status: " + application.getStatus());
            }
            if (!feePaid) {
                throw new BadRequestException("No verified registration fee payment found for this applicant.");
            }
        }

        MemberProfile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for approved application."));
        profile.setMemberId(generateMemberId());
        profile.setMemberSince(LocalDate.now());
        if (profile.getMembershipTier() == null) {
            profile.setMembershipTier("Standard");
        }
        profileRepository.save(profile);

        user.setRole(UserRole.MEMBER);
        userRepository.save(user);

        membershipDuesService.createInitialDues(user);
        programApplicationService.makeApplicationsVisibleToCoordinators(user);

        // Captured before mutation — distinguishes "already waived at send-form" (skip the
        // duplicate stamp/audit-log entry below) from "waiving ad-hoc right now."
        boolean newlyWaived = waiving && !application.isRegistrationFeeWaived();

        application.setStatus(ApplicationStatus.APPROVED);
        application.setApprovedAt(LocalDateTime.now());
        if (newlyWaived) {
            application.setRegistrationFeeWaived(true);
            application.setRegistrationFeeWaivedAt(LocalDateTime.now());
            application.setRegistrationFeeWaivedBy(admin.getFullName());
        }
        applicationRepository.save(application);

        emailService.sendMembershipApproved(user.getEmail(), user.getFullName(), profile.getMemberId());
        notifyAdminsOfApproval(application, user, profile.getMemberId(), admin, waiving);
        log.info("Membership approved for application {} — memberId={}{}",
                application.getReferenceNumber(), profile.getMemberId(), waiving ? " (registration fee waived)" : "");

        auditLogService.logAbout(admin, "MEMBERSHIP_APPROVED", "MembershipApplication", application.getId(),
                user.getFullName(), profile.getMemberId(),
                "Membership approved for " + user.getFullName() + " (ref " + application.getReferenceNumber()
                        + ", memberId " + profile.getMemberId() + ") by " + admin.getFullName());
        if (newlyWaived) {
            auditLogService.logAbout(admin, "REGISTRATION_FEE_WAIVED", "MembershipApplication", application.getId(),
                    user.getFullName(), profile.getMemberId(),
                    "Registration fee waived for " + user.getFullName() + " (ref " + application.getReferenceNumber()
                            + ") by " + admin.getFullName());
        }

        return AdminApplicationDto.from(application, isSuperAdmin);
    }

    /** Everything applicants normally provide during onboarding except the registration fee
     * payment itself — the bar a waived-fee approval must still clear. */
    private void requireOnboardingComplete(MembershipApplication application) {
        List<String> missing = new java.util.ArrayList<>();
        if (application.getEmailReverifiedAt() == null) missing.add("email verification");
        if (application.getPhotoSubmittedAt() == null) missing.add("profile photo");
        if (application.getIdentityInfoSubmittedAt() == null) missing.add("identity details");
        if (application.getAddressInfoSubmittedAt() == null) missing.add("address");
        if (application.getKinContactsSubmittedAt() == null) missing.add("next-of-kin & emergency contacts");
        if (application.getConstitutionAcceptedAt() == null) missing.add("constitution acceptance");
        if (application.getBylawsAcceptedAt() == null) missing.add("bylaws acceptance");
        if (!missing.isEmpty()) {
            throw new BadRequestException(
                    "Cannot approve without a payment — applicant has not yet completed: " + String.join(", ", missing));
        }
    }


    // ------------------------------------------------------------------ Private

    private void applyRejection(MembershipApplication application) {
        application.setStatus(ApplicationStatus.REJECTED);
        application.setRejectionReason("Your membership application was reviewed and not approved by the board.");
        application.setReviewedAt(LocalDateTime.now());

        User applicant = application.getUser();
        if (applicant != null) {
            emailService.sendPlain(
                    applicant.getEmail(), applicant.getFullName(),
                    "Membership Application Update — Ushirika Welfare Organization",
                    "Dear " + applicant.getFirstName() + ",\n\n" +
                    "We regret to inform you that your membership application (ref: " +
                    application.getReferenceNumber() + ") has not been approved at this time.\n\n" +
                    "You are welcome to reapply. If you have questions, please contact our office.\n\n" +
                    "Regards,\nUshirika Welfare Organization"
            );
        } else if (application.getApplicantEmail() != null) {
            emailService.sendPlain(
                    application.getApplicantEmail(), application.getApplicantName(),
                    "Membership Enquiry Update — Ushirika Welfare Organization",
                    "Dear " + application.getApplicantName() + ",\n\n" +
                    "We have reviewed your membership enquiry (ref: " +
                    application.getReferenceNumber() + ") and are unable to proceed at this time.\n\n" +
                    "You are welcome to reapply. If you have questions, please contact our office.\n\n" +
                    "Regards,\nUshirika Welfare Organization"
            );
        }
        log.info("Membership application {} rejected.", application.getReferenceNumber());
    }

    /**
     * Random, never-disclosed placeholder stored as a new applicant's password. Nobody is ever told
     * it -- the account is reachable only via account activation or a password reset. Kept to 64
     * characters (two dash-less UUIDs) because BCrypt's hard input limit is 72 bytes and newer
     * Spring Security versions throw on anything longer.
     */
    private String generateUnusablePassword() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    /** One-time magic-login token — 32 random bytes, URL-safe, no padding. */
    private String generateLoginToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendApplicantConfirmation(String toEmail, String toName, String referenceNumber) {
        emailService.sendPlain(
                toEmail, toName,
                "We received your membership application — Ushirika Welfare Organization",
                """
                <div style="font-family:sans-serif;max-width:560px;margin:auto;color:#1a1a1a">
                  <h2 style="color:#007834">Thank you, %s!</h2>
                  <p>We have received your membership application and it is now under review by our committee.</p>
                  <p><strong>Your reference number is: %s</strong> — keep this for your records.</p>
                  <p>You'll receive another email as soon as a decision is made, with next steps if you're approved.</p>
                  <p>The committee will be in touch within 5 business days. If you have any questions in the
                     meantime, please reply to this email.</p>
                  <p>— Ushirika Welfare Organization</p>
                </div>
                """.formatted(toName, referenceNumber)
        );
    }

    private void notifyAdminsOfPublicApplication(MembershipApplication application) {
        userRepository.findAllByRoleIn(List.of(UserRole.ADMIN, UserRole.SUPERADMIN)).forEach(admin ->
                emailService.sendPlain(
                        admin.getEmail(), admin.getFullName(),
                        "New Public Membership Enquiry — Action Required",
                        "<p>Hello " + admin.getFirstName() + ",</p>" +
                        "<p>A new public membership enquiry requires your review.</p>" +
                        "<p><strong>Applicant:</strong> " + application.getApplicantName() + "<br>" +
                        "<strong>Email:</strong> " + application.getApplicantEmail() + "<br>" +
                        "<strong>Reference:</strong> " + application.getReferenceNumber() + "</p>" +
                        ctaButton(siteUrl + "/admin/applications", "Review Application") +
                        "<p>Ushirika Welfare Organization</p>"
                )
        );
    }

    private void notifyAdminsOfNewApplication(MembershipApplication application, User applicant) {
        userRepository.findAllByRoleIn(List.of(UserRole.ADMIN, UserRole.SUPERADMIN)).forEach(admin ->
                emailService.sendPlain(
                        admin.getEmail(), admin.getFullName(),
                        "New Membership Application — Action Required",
                        "<p>Hello " + admin.getFirstName() + ",</p>" +
                        "<p>A new membership application requires your review.</p>" +
                        "<p><strong>Applicant:</strong> " + applicant.getFullName() + "<br>" +
                        "<strong>Reference:</strong> " + application.getReferenceNumber() + "</p>" +
                        ctaButton(siteUrl + "/admin/applications", "Review Application") +
                        "<p>Ushirika Welfare Organization</p>"
                )
        );
    }

    /** Shared CTA button styling for admin/coordinator notification emails — matches the button
     *  already used in sendFormSentCredentials/sendMembershipApproved instead of a bare link. */
    private static String ctaButton(String url, String label) {
        return "<p style=\"margin:24px 0\"><a href=\"" + url + "\" " +
                "style=\"display:inline-block;background:#007834;color:#fff;padding:10px 20px;" +
                "border-radius:24px;text-decoration:none;font-weight:600\">" + label + "</a></p>";
    }

    /** The enquiry stage already notifies admins/superadmin (see above) — final approval never
     *  did, so nobody but the new member themselves heard that a membership actually went through. */
    private void notifyAdminsOfApproval(MembershipApplication application, User newMember, String memberId,
                                         User approvedBy, boolean feeWaived) {
        userRepository.findAllByRoleIn(List.of(UserRole.ADMIN, UserRole.SUPERADMIN)).forEach(admin ->
                emailService.sendPlain(
                        admin.getEmail(), admin.getFullName(),
                        "Membership Approved — " + application.getReferenceNumber(),
                        "<p>Hello " + admin.getFirstName() + ",</p>" +
                        "<p>A membership application was just approved.</p>" +
                        "<p><strong>Member:</strong> " + newMember.getFullName() + "<br>" +
                        "<strong>Member ID:</strong> " + memberId + "<br>" +
                        "<strong>Reference:</strong> " + application.getReferenceNumber() + "<br>" +
                        "<strong>Approved by:</strong> " + approvedBy.getFullName() +
                        (feeWaived ? "<br><strong>Registration fee:</strong> waived" : "") + "</p>" +
                        ctaButton(siteUrl + "/admin/members", "View Member") +
                        "<p>Ushirika Welfare Organization</p>"
                )
        );
    }

    private MembershipApplication findApplicationById(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }

    private String generateReferenceNumber() {
        return "UWF-APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateMemberId() {
        int year = LocalDate.now().getYear();
        long sequence = profileRepository.countByMemberIdNotNull() + 1;
        return "UW-%d-%04d".formatted(year, sequence);
    }

    private String serializeChildren(java.util.List<MembershipApplicationRequest.ChildRecord> children) {
        if (children == null || children.isEmpty()) return "[]";
        var sb = new StringBuilder("[");
        for (int i = 0; i < children.size(); i++) {
            var c = children.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(c.name() == null ? "" : c.name().replace("\"", "\\\""))
              .append("\",\"dateOfBirth\":\"").append(c.dateOfBirth() == null ? "" : c.dateOfBirth())
              .append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }
}
