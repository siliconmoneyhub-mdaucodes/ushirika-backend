package com.mdau.ushirika.module.member.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** Exactly two per MemberProfile, distinguished by position (1 or 2). */
@Entity
@Table(
    name = "member_emergency_contacts",
    indexes = @Index(name = "idx_ec_profile", columnList = "member_profile_id"),
    uniqueConstraints = @UniqueConstraint(name = "uq_ec_profile_position", columnNames = {"member_profile_id", "position"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyContact extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_profile_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_ec_profile"))
    private MemberProfile memberProfile;

    @Column(nullable = false)
    private short position;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 50)
    private String relationship;
}
