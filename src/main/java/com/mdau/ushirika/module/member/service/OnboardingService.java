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
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.enums.Country;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.member.repository.MembershipApplicationRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.payment.dto.PaymentInitDto;
import com.mdau.ushirika.module.payment.enums.PaymentBasketLedger;
import com.mdau.ushirika.module.payment.enums.PaymentStatus;
import com.mdau.ushirika.module.payment.repository.PaymentBasketRepository;
import com.mdau.ushirika.module.payment.service.PaymentBasketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
    private final PaymentBasketRepository paymentBasketRepository;
    private final PaymentBasketService paymentBasketService;
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
    public OnboardingStatusDto acceptConstitution() {
        MembershipApplication application = findApplication(currentUser());

        boolean publishedExists = governingDocumentRepository.existsByStatusAndDocumentTypeIn(
                DocumentStatus.PUBLISHED, List.of(DocumentType.CONSTITUTION));
        if (!publishedExists) {
            throw new BadRequestException("No published constitution document is available yet.");
        }

        application.setConstitutionAcceptedAt(LocalDateTime.now());
        advanceToOnboarding(application);
        applicationRepository.save(application);
        return OnboardingStatusDto.from(application);
    }

    @Transactional
    public OnboardingStatusDto acceptBylaws() {
        MembershipApplication application = findApplication(currentUser());

        boolean publishedExists = governingDocumentRepository.existsByStatusAndDocumentTypeIn(
                DocumentStatus.PUBLISHED, List.of(DocumentType.BYLAWS));
        if (!publishedExists) {
            throw new BadRequestException("No published bylaws document is available yet.");
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
        requireCompleteProfile(user);
        // Document upload is disabled for now — no requirement here until the spec is finalized.
        if (application.getConstitutionAcceptedAt() == null) {
            throw new BadRequestException("Please read and accept the constitution before continuing.");
        }
        if (application.getBylawsAcceptedAt() == null) {
            throw new BadRequestException("Please read and accept the bylaws before continuing.");
        }
        if (!paymentBasketRepository.existsByMemberIdAndStatusAndLines_Ledger(
                user.getId(), PaymentStatus.SUCCESS, PaymentBasketLedger.REGISTRATION_FEE)) {
            throw new BadRequestException("Please complete your registration fee payment before submitting.");
        }

        application.setStatus(ApplicationStatus.PAYMENT_SUBMITTED);
        application.setRegistrationSubmittedAt(LocalDateTime.now());
        applicationRepository.save(application);

        log.info("Registration submitted for application {} — awaiting payment verification and final approval",
                application.getReferenceNumber());

        sendRegistrationCompleteEmail(user, application.getReferenceNumber());

        return OnboardingStatusDto.from(application);
    }

    private void sendRegistrationCompleteEmail(User user, String referenceNumber) {
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#1A4731">Registration Complete ✓</h2>
              <p>Hi %s,</p>
              <p>Your registration form and payment have been received. Your application
                 (reference <strong>%s</strong>) is now with the committee for final review.</p>
              <p>You'll get another email as soon as your membership is approved and your
                 portal access is activated.</p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>
            """.formatted(user.getFirstName(), referenceNumber);
        try {
            emailService.sendPlain(user.getEmail(), user.getFullName(),
                    "Registration Complete — Ushirika Welfare Organization", html);
        } catch (Exception e) {
            log.warn("Could not send registration-complete email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    /** Registration fee (fixed $100) + an optional amount toward a Benevolence application
     * already created in the "Programs" step — one combined Stripe Checkout session. */
    @Transactional
    public PaymentInitDto startRegistrationCheckout(BigDecimal benevolenceAmount, UUID benevolenceApplicationId,
                                                      String successUrl, String cancelUrl) {
        return paymentBasketService.startOnboardingCheckout(benevolenceAmount, benevolenceApplicationId, successUrl, cancelUrl);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** The single source of truth for "onboarding actually collected everything" —
     * replaces the old placeholder-value approach where a profile could look complete
     * without any of it being real. */
    private void requireCompleteProfile(User user) {
        MemberProfile p = profileRepository.findByUser(user)
                .orElseThrow(() -> new BadRequestException("Profile not found. Please complete the earlier onboarding steps first."));

        if (p.getIdNumber() == null || p.getDateOfBirth() == null || p.getGender() == null) {
            throw new BadRequestException("Please complete your identity details before continuing.");
        }
        if (isBlank(p.getStreet()) || isBlank(p.getCity()) || isBlank(p.getZipCode()) || p.getCountry() == null) {
            throw new BadRequestException("Please complete your address before continuing.");
        }
        if (p.getCountry() == Country.KENYA
                && (isBlank(p.getKenyaCounty()) || isBlank(p.getKenyaSubCounty()) || isBlank(p.getKenyaVillage()))) {
            throw new BadRequestException("Please complete your county, sub-county, and village before continuing.");
        }
        if (p.getCountry() == Country.UGANDA
                && (isBlank(p.getUgandaProvince()) || isBlank(p.getUgandaCounty()) || isBlank(p.getUgandaVillage()))) {
            throw new BadRequestException("Please complete your province, county, and village before continuing.");
        }
        if (p.getNextOfKin().size() != 2) {
            throw new BadRequestException("Please provide exactly two next-of-kin entries before continuing.");
        }
        if (p.getEmergencyContacts().size() != 2) {
            throw new BadRequestException("Please provide exactly two emergency contacts before continuing.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

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
