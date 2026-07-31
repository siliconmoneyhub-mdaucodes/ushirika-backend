package com.mdau.ushirika.module.member.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.constitution.enums.DocumentStatus;
import com.mdau.ushirika.module.constitution.enums.DocumentType;
import com.mdau.ushirika.module.constitution.repository.GoverningDocumentRepository;
import com.mdau.ushirika.module.member.dto.AdditionalInfoRequest;
import com.mdau.ushirika.module.member.dto.OnboardingStatusDto;
import com.mdau.ushirika.module.member.dto.VerifyOnboardingEmailRequest;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.member.repository.MembershipApplicationRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.payment.enums.PeerPaymentPurpose;
import com.mdau.ushirika.module.payment.repository.PeerPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Applicant-facing onboarding steps between "Send Form" and final membership approval.
 * All endpoints here are restricted to the APPLICANT role (see SecurityConfig /onboarding/**).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final int OTP_EXPIRY_MINUTES = 15;

    private final MembershipApplicationRepository applicationRepository;
    private final MemberProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final GoverningDocumentRepository governingDocumentRepository;
    private final PeerPaymentRepository peerPaymentRepository;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public OnboardingStatusDto getStatus() {
        return OnboardingStatusDto.from(findApplication(currentUser()));
    }

    @Transactional
    public void requestEmailOtp() {
        User user = currentUser();
        MembershipApplication application = findApplication(user);

        String otp = generateOtp();
        application.setOnboardingEmailOtp(otp);
        application.setOnboardingEmailOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        applicationRepository.save(application);

        emailService.sendEmailVerificationOtp(user.getEmail(), user.getFirstName(), otp);
    }

    @Transactional
    public OnboardingStatusDto verifyEmailOtp(VerifyOnboardingEmailRequest req) {
        User user = currentUser();
        MembershipApplication application = findApplication(user);

        if (application.getOnboardingEmailOtp() == null || !application.getOnboardingEmailOtp().equals(req.otp())) {
            throw new BadRequestException("Invalid verification code");
        }
        if (LocalDateTime.now().isAfter(application.getOnboardingEmailOtpExpiry())) {
            throw new BadRequestException("Verification code has expired. Request a new one.");
        }

        application.setOnboardingEmailOtp(null);
        application.setOnboardingEmailOtpExpiry(null);
        application.setEmailReverifiedAt(LocalDateTime.now());
        advanceToOnboarding(application);
        applicationRepository.save(application);

        return OnboardingStatusDto.from(application);
    }

    @Transactional
    public OnboardingStatusDto submitAdditionalInfo(AdditionalInfoRequest req) {
        User user = currentUser();
        MembershipApplication application = findApplication(user);
        application.setAdditionalInfoDocumentUrls(req.documentUrls() != null ? req.documentUrls() : List.of());
        application.setHeardAboutUs(req.heardAboutUs());
        application.setBeneficiaries(req.beneficiaries() != null ? req.beneficiaries() : List.of());
        application.setAdditionalInfoSubmittedAt(LocalDateTime.now());
        advanceToOnboarding(application);
        applicationRepository.save(application);

        profileRepository.findByUser(user).ifPresent(profile -> {
            profile.setHeardAboutUs(req.heardAboutUs());
            profileRepository.save(profile);
        });

        return OnboardingStatusDto.from(application);
    }

    @Transactional
    public OnboardingStatusDto acceptBylaws() {
        MembershipApplication application = findApplication(currentUser());

        boolean publishedExists = governingDocumentRepository.existsByStatusAndDocumentTypeIn(
                DocumentStatus.PUBLISHED, List.of(DocumentType.BYLAWS, DocumentType.CONSTITUTION));
        if (!publishedExists) {
            throw new BadRequestException("No published bylaws or constitution document is available yet.");
        }

        application.setBylawsAcceptedAt(LocalDateTime.now());
        advanceToOnboarding(application);
        applicationRepository.save(application);
        return OnboardingStatusDto.from(application);
    }

    @Transactional
    public OnboardingStatusDto submitRegistration() {
        User user = currentUser();
        MembershipApplication application = findApplication(user);

        if (application.getEmailReverifiedAt() == null) {
            throw new BadRequestException("Please verify your email before continuing.");
        }
        // Document upload is disabled for now — no requirement here until the spec is finalized.
        if (application.getBylawsAcceptedAt() == null) {
            throw new BadRequestException("Please read and accept the bylaws and constitution before continuing.");
        }
        if (!peerPaymentRepository.existsByMemberAndPurpose(user, PeerPaymentPurpose.REGISTRATION_FEE)) {
            throw new BadRequestException("Please report your registration fee payment before submitting.");
        }

        application.setStatus(ApplicationStatus.PAYMENT_SUBMITTED);
        application.setRegistrationSubmittedAt(LocalDateTime.now());
        applicationRepository.save(application);

        log.info("Registration submitted for application {} — awaiting payment verification and final approval",
                application.getReferenceNumber());

        return OnboardingStatusDto.from(application);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void advanceToOnboarding(MembershipApplication application) {
        if (application.getStatus() == ApplicationStatus.FORM_SENT) {
            application.setStatus(ApplicationStatus.ONBOARDING_IN_PROGRESS);
        }
    }

    private MembershipApplication findApplication(User user) {
        return applicationRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("No application found for this account."));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }

    private String generateOtp() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }
}
