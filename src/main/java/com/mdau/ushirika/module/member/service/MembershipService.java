package com.mdau.ushirika.module.member.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
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
import com.mdau.ushirika.module.payment.entity.PeerPayment;
import com.mdau.ushirika.module.payment.enums.PeerPaymentPurpose;
import com.mdau.ushirika.module.payment.enums.PeerPaymentStatus;
import com.mdau.ushirika.module.payment.repository.PeerPaymentRepository;
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
    private final PeerPaymentRepository peerPaymentRepository;
    private final ProgramApplicationService programApplicationService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    private static final int ONBOARDING_LOGIN_TOKEN_HOURS = 48;

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
        String address = req.street() + ", " + req.city() + ", " + req.state() + " " + req.zipCode();
        MembershipApplication application = MembershipApplication.builder()
                .referenceNumber(generateReferenceNumber())
                .applicantName(req.firstName() + " " + req.lastName())
                .applicantEmail(req.email())
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
        return AdminApplicationDto.from(application, isSuperAdmin);
    }

    /**
     * Admin accepts the application in principle: creates (or demotes) the applicant's
     * account to APPLICANT role and emails them onboarding login credentials. This does
     * NOT grant membership — see {@link #approveMembership}.
     */
    @Transactional
    public AdminApplicationDto sendForm(UUID applicationId, boolean isSuperAdmin) {
        MembershipApplication application = findApplicationById(applicationId);

        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new BadRequestException(
                    "Only SUBMITTED applications can have the form sent. Current status: " + application.getStatus());
        }

        String tempPassword = generateTempPassword();
        User user = application.getUser();
        String applicantEmail;
        String applicantFirstName;

        if (user == null) {
            // Public/anonymous applicant — create their account now, scoped to APPLICANT.
            String email = application.getApplicantEmail();
            if (email == null || email.isBlank()) {
                throw new BadRequestException("Cannot send form — no email on record for this application.");
            }
            email = email.toLowerCase().trim();
            if (userRepository.existsByEmail(email)) {
                throw new ConflictException("An account with email " + email + " already exists.");
            }

            String fullName = application.getApplicantName() != null ? application.getApplicantName().trim() : "Applicant";
            String[] parts = fullName.split(" ", 2);
            String firstName = parts[0];
            String lastName  = parts.length > 1 ? parts[1] : "";

            User newUser = User.builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(email)
                    .phone(application.getApplicantPhone())
                    .password(passwordEncoder.encode(tempPassword))
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
            // Applied while logged in — demote to APPLICANT and issue fresh onboarding credentials.
            user.setRole(UserRole.APPLICANT);
            user.setPassword(passwordEncoder.encode(tempPassword));
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
        applicationRepository.save(application);

        String continueUrl = siteUrl + "/login?token=" + loginToken;
        emailService.sendFormSentCredentials(applicantEmail, applicantFirstName, tempPassword, continueUrl);
        log.info("Form sent for application {} — applicant={}", application.getReferenceNumber(), applicantEmail);

        return AdminApplicationDto.from(application, isSuperAdmin);
    }

    /**
     * Recovery path for an applicant who lost their onboarding email, forgot the temp
     * password, or let the login link expire mid-onboarding. Issues a fresh temp password
     * and a fresh magic-login token without resetting any onboarding progress already saved
     * (email verification, additional info, bylaws, programs) — only credentials are reissued.
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

        String tempPassword = generateTempPassword();
        user.setPassword(passwordEncoder.encode(tempPassword));
        String loginToken = generateLoginToken();
        user.setOnboardingLoginToken(loginToken);
        user.setOnboardingLoginTokenExpiry(LocalDateTime.now().plusHours(ONBOARDING_LOGIN_TOKEN_HOURS));
        user.setOnboardingLoginTokenUses(0);
        userRepository.save(user);

        String continueUrl = siteUrl + "/login?token=" + loginToken;
        emailService.sendFormSentCredentials(user.getEmail(), user.getFirstName(), tempPassword, continueUrl);
        log.info("Onboarding credentials resent for application {} — applicant={}", application.getReferenceNumber(), user.getEmail());

        return AdminApplicationDto.from(application, isSuperAdmin);
    }

    /**
     * Final step: grants full membership once the applicant's onboarding is complete and their
     * registration fee payment has been verified. Flips the account's role from APPLICANT to MEMBER —
     * same login credentials, no new account issued.
     */
    @Transactional
    public AdminApplicationDto approveMembership(UUID applicationId, boolean isSuperAdmin) {
        MembershipApplication application = findApplicationById(applicationId);

        if (application.getStatus() != ApplicationStatus.PAYMENT_SUBMITTED) {
            throw new BadRequestException(
                    "Only applications with a submitted registration payment can be approved. Current status: " + application.getStatus());
        }

        User user = application.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("No applicant account linked to this application.");
        }

        PeerPayment registrationPayment = peerPaymentRepository
                .findFirstByMemberAndPurposeOrderByCreatedAtDesc(user, PeerPaymentPurpose.REGISTRATION_FEE)
                .orElseThrow(() -> new BadRequestException("No registration fee payment found for this applicant."));
        if (registrationPayment.getStatus() != PeerPaymentStatus.VERIFIED) {
            throw new BadRequestException(
                    "The registration fee payment must be verified before membership can be approved. " +
                    "Current payment status: " + registrationPayment.getStatus());
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

        application.setStatus(ApplicationStatus.APPROVED);
        application.setApprovedAt(LocalDateTime.now());
        applicationRepository.save(application);

        emailService.sendMembershipApproved(user.getEmail(), user.getFullName(), profile.getMemberId());
        log.info("Membership approved for application {} — memberId={}",
                application.getReferenceNumber(), profile.getMemberId());

        return AdminApplicationDto.from(application, isSuperAdmin);
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

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$!";
        SecureRandom rand = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(chars.charAt(rand.nextInt(chars.length())));
        return sb.toString();
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
                  <p><strong>Your reference number is: %s</strong></p>
                  <p>You can use this reference number to track your application status at any time by visiting
                     our website and clicking <em>Track Application</em>.</p>
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
                        "Hello " + admin.getFirstName() + ",\n\n" +
                        "A new public membership enquiry requires your review.\n\n" +
                        "Applicant: " + application.getApplicantName() + "\n" +
                        "Email: " + application.getApplicantEmail() + "\n" +
                        "Reference: " + application.getReferenceNumber() + "\n\n" +
                        "Log in to the admin portal to review this application.\n\n" +
                        "Ushirika Welfare Organization"
                )
        );
    }

    private void notifyAdminsOfNewApplication(MembershipApplication application, User applicant) {
        userRepository.findAllByRoleIn(List.of(UserRole.ADMIN, UserRole.SUPERADMIN)).forEach(admin ->
                emailService.sendPlain(
                        admin.getEmail(), admin.getFullName(),
                        "New Membership Application — Action Required",
                        "Hello " + admin.getFirstName() + ",\n\n" +
                        "A new membership application requires your review.\n\n" +
                        "Applicant: " + applicant.getFullName() + "\n" +
                        "Reference: " + application.getReferenceNumber() + "\n\n" +
                        "Log in to the admin portal to cast your vote.\n\n" +
                        "Ushirika Welfare Organization"
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
