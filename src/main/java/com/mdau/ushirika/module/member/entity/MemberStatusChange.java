package com.mdau.ushirika.module.member.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.member.enums.MemberStatus;
import com.mdau.ushirika.module.member.enums.MemberStatusReason;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** One immutable row per active/membershipCeased transition -- createdAt (from BaseEntity) is
 * the change timestamp. Feeds the member's own status history, the portal notice explaining a
 * status change, and the Reports "departed members" section. */
@Entity
@Table(
    name = "member_status_changes",
    indexes = {
        @Index(name = "idx_status_change_user",       columnList = "user_id"),
        @Index(name = "idx_status_change_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberStatusChange extends BaseEntity {

    /** Raw UUID -- avoids eagerly loading the User entity, same pattern as ReinstatementRequest. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private MemberStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private MemberStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private MemberStatusReason reason;

    /** Null means the system made this change (a scheduler/automated rule), not a person. */
    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    @Column(name = "notes", length = 500)
    private String notes;
}
