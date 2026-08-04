package com.mdau.ushirika.module.program.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.member.dto.BeneficiaryInfo;
import com.mdau.ushirika.module.program.enums.ProgramApplicationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An applicant's request, made during onboarding, to join a specific Program.
 * Stays invisible to the program's coordinator(s) until the applicant's overall
 * membership is approved — see MembershipService.approveMembership().
 */
@Entity
@Table(
    name = "program_applications",
    uniqueConstraints = @UniqueConstraint(name = "uq_program_application", columnNames = {"program_id", "applicant_id"}),
    indexes = {
        @Index(name = "idx_pa_program",           columnList = "program_id"),
        @Index(name = "idx_pa_applicant",          columnList = "applicant_id"),
        @Index(name = "idx_pa_program_status",     columnList = "program_id, status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pa_program"))
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pa_applicant"))
    private User applicant;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProgramApplicationStatus status = ProgramApplicationStatus.PENDING_MEMBERSHIP;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id", foreignKey = @ForeignKey(name = "fk_pa_reviewed_by"))
    private User reviewedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Amount the applicant already paid toward this program's enrollment fee during onboarding,
     * before the program's real enrollment record exists — transferred in on approval. */
    @Column(name = "prepaid_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal prepaidAmount = BigDecimal.ZERO;

    /** Filled in when a verified member applies directly from the portal — reuses the same
     * shape as the general onboarding beneficiaries list. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "beneficiaries", columnDefinition = "jsonb")
    @Builder.Default
    private List<BeneficiaryInfo> beneficiaries = new ArrayList<>();

    @Column(name = "notes", length = 1000)
    private String notes;
}
