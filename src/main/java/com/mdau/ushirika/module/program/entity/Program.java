package com.mdau.ushirika.module.program.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.payment.enums.ContributionFrequency;
import com.mdau.ushirika.module.program.enums.ProgramStatus;
import com.mdau.ushirika.module.program.enums.ProgramType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin-managed catalog entry for a joinable member program (MGR, Benevolence, etc.).
 * Basic info (name, shortDescription, type) is set by whoever creates the program.
 * The detail fields below are owned by the assigned program admin(s) — see
 * {@link ProgramAdminAssignment} — though a global ADMIN/SUPERADMIN can also edit them.
 */
@Entity
@Table(
    name = "programs",
    indexes = {
        @Index(name = "idx_program_status", columnList = "status"),
        @Index(name = "idx_program_slug",   columnList = "slug", unique = true)
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Program extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, length = 160, unique = true)
    private String slug;

    @Column(name = "short_description", nullable = false, length = 500)
    private String shortDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ProgramType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private ProgramStatus status = ProgramStatus.DRAFT;

    // ── Fields owned by the assigned program admin ──────────────────────────────

    @Column(name = "contribution_amount", precision = 12, scale = 2)
    private BigDecimal contributionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_frequency", length = 15)
    private ContributionFrequency contributionFrequency;

    /** Program-specific bylaws/rules — separate from the org-wide constitution. */
    @Column(name = "rules", columnDefinition = "TEXT")
    private String rules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefits", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> benefits = new ArrayList<>();

    @Column(name = "max_beneficiaries")
    private Integer maxBeneficiaries;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", foreignKey = @ForeignKey(name = "fk_program_created_by"))
    private User createdByUser;
}
