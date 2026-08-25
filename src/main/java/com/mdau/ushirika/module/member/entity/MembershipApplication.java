package com.mdau.ushirika.module.member.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.member.dto.BeneficiaryInfo;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "membership_applications",
    indexes = {
        // Most frequent admin query: list by status
        @Index(name = "idx_ma_status",          columnList = "status"),
        // Member self-service: "do I have an active application?"
        @Index(name = "idx_ma_user_id",         columnList = "user_id"),
        @Index(name = "idx_ma_user_status",     columnList = "user_id, status"),
        // Dashboard sorting
        @Index(name = "idx_ma_submitted_at",    columnList = "submitted_at"),
        @Index(name = "idx_ma_created_at",      columnList = "created_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MembershipApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true,
                foreignKey = @ForeignKey(name = "fk_ma_user"))
    private User user;

    /** Populated for public (unauthenticated) submissions — no User account yet.
     *  Deliberately first+last only (no middle name folded in) — sendForm() splits this
     *  string back into firstName/lastName by first space, so keeping it two tokens keeps
     *  that split reliable. The middle name rides separately in applicantMiddleName. */
    @Column(name = "applicant_name", length = 200)
    private String applicantName;

    @Column(name = "applicant_middle_name", length = 100)
    private String applicantMiddleName;

    @Column(name = "applicant_email", length = 200)
    private String applicantEmail;

    @Column(name = "applicant_phone", length = 30)
    private String applicantPhone;

    @Column(name = "applicant_county", length = 100)
    private String applicantCounty;

    @Column(name = "applicant_subtribe", length = 100)
    private String applicantSubtribe;

    @Column(name = "applicant_eligibility", length = 50)
    private String applicantEligibility;

    @Column(name = "applicant_address", length = 500)
    private String applicantAddress;

    /** Public-facing tracking number — shown to applicant. */
    @Column(name = "reference_number", unique = true, nullable = false,
            updatable = false, length = 30)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    /** Supporting document URLs uploaded via Cloudinary before submission. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_urls", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> documentUrls = new ArrayList<>();

    /**
     * Generic rejection message shown to applicant.
     * Never reveals which admin rejected — anonymity preserved.
     */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Internal notes visible only to admins. */
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // ── Onboarding pipeline (between "Send Form" and final membership approval) ──

    /** When admin accepted the application in principle and sent onboarding credentials. */
    @Column(name = "form_sent_at")
    private LocalDateTime formSentAt;

    /** Extra documents uploaded during onboarding — distinct from the original apply-form documentUrls. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_info_document_urls", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> additionalInfoDocumentUrls = new ArrayList<>();

    /** Set once the additional-info step is submitted — document presence alone can no longer signal this, since uploads are optional. */
    @Column(name = "additional_info_submitted_at")
    private LocalDateTime additionalInfoSubmittedAt;

    /** How the applicant heard about the organization — free-form key from a fixed frontend dropdown. */
    @Column(name = "heard_about_us", length = 50)
    private String heardAboutUs;

    /** General-purpose beneficiaries captured once during onboarding. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "beneficiaries", columnDefinition = "jsonb")
    @Builder.Default
    private List<BeneficiaryInfo> beneficiaries = new ArrayList<>();

    /** One-time code sent to re-verify the applicant's email during onboarding (separate from account signup OTP). */
    @Column(name = "onboarding_email_otp", length = 6)
    private String onboardingEmailOtp;

    @Column(name = "onboarding_email_otp_expiry")
    private LocalDateTime onboardingEmailOtpExpiry;

    @Column(name = "email_reverified_at")
    private LocalDateTime emailReverifiedAt;

    @Column(name = "constitution_accepted_at")
    private LocalDateTime constitutionAcceptedAt;

    /** Manually-typed signature captured at acceptance -- name/initials/date, not just a
     * timestamp, per the org's request that this read as an actual signature. */
    @Column(name = "constitution_signature_name", length = 200)
    private String constitutionSignatureName;

    @Column(name = "constitution_signature_initials", length = 20)
    private String constitutionSignatureInitials;

    @Column(name = "constitution_signature_date")
    private LocalDate constitutionSignatureDate;

    @Column(name = "bylaws_accepted_at")
    private LocalDateTime bylawsAcceptedAt;

    @Column(name = "bylaws_signature_name", length = 200)
    private String bylawsSignatureName;

    @Column(name = "bylaws_signature_initials", length = 20)
    private String bylawsSignatureInitials;

    @Column(name = "bylaws_signature_date")
    private LocalDate bylawsSignatureDate;

    /** Set once the new onboarding Identity step (idNumber/DOB/gender/marital/occupation) is submitted. */
    @Column(name = "identity_info_submitted_at")
    private LocalDateTime identityInfoSubmittedAt;

    /** Set once the new onboarding Address step is submitted. */
    @Column(name = "address_info_submitted_at")
    private LocalDateTime addressInfoSubmittedAt;

    /** Set once both next-of-kin entries and both emergency contacts are submitted. */
    @Column(name = "kin_contacts_submitted_at")
    private LocalDateTime kinContactsSubmittedAt;

    @Column(name = "registration_submitted_at")
    private LocalDateTime registrationSubmittedAt;

    /** Set when an admin approves membership without a verified Stripe payment -- the
     * mass-onboarding path for real-world members who joined before the platform existed. */
    @Column(name = "registration_fee_waived", nullable = false)
    @Builder.Default
    private boolean registrationFeeWaived = false;

    @Column(name = "registration_fee_waived_at")
    private LocalDateTime registrationFeeWaivedAt;

    /** Name of the admin who waived the fee -- audit trail for accounting. */
    @Column(name = "registration_fee_waived_by", length = 200)
    private String registrationFeeWaivedBy;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApplicationApproval> approvals = new ArrayList<>();
}
