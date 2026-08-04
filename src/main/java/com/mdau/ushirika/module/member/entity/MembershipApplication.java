package com.mdau.ushirika.module.member.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.member.dto.BeneficiaryInfo;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** Populated for public (unauthenticated) submissions — no User account yet. */
    @Column(name = "applicant_name", length = 200)
    private String applicantName;

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

    @Column(name = "bylaws_accepted_at")
    private LocalDateTime bylawsAcceptedAt;

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

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApplicationApproval> approvals = new ArrayList<>();
}
