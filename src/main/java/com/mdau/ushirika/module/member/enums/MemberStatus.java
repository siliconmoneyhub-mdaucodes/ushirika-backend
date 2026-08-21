package com.mdau.ushirika.module.member.enums;

/** Derived from User.active/membershipCeased -- CEASED is the terminal/severe state
 * (membershipCeased=true), INACTIVE is a lesser, recoverable state (active=false only),
 * ACTIVE is everything else. See MemberStatusChangeService.statusOf(). */
public enum MemberStatus {
    ACTIVE,
    INACTIVE,
    CEASED
}
