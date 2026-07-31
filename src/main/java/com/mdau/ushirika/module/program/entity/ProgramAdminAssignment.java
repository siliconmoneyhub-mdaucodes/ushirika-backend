package com.mdau.ushirika.module.program.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Delegates management of one Program's detail fields (contribution amount,
 * frequency, rules, benefits, max beneficiaries) to a specific user — mirrors the
 * FinancialOfficialPermission delegation pattern rather than adding a new global
 * role, since a program admin's authority is scoped to their assigned program(s) only.
 */
@Entity
@Table(
    name = "program_admin_assignments",
    uniqueConstraints = @UniqueConstraint(name = "uq_program_admin", columnNames = {"program_id", "user_id"}),
    indexes = {
        @Index(name = "idx_paa_program", columnList = "program_id"),
        @Index(name = "idx_paa_user",    columnList = "user_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramAdminAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, foreignKey = @ForeignKey(name = "fk_paa_program"))
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_paa_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id", foreignKey = @ForeignKey(name = "fk_paa_assigned_by"))
    private User assignedBy;
}
