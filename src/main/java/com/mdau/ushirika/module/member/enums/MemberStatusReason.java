package com.mdau.ushirika.module.member.enums;

/** Why a member's active/membershipCeased status changed -- previously untracked, so a departed
 * member's record carried no explanation anywhere the member or a report could see. */
public enum MemberStatusReason {
    DUES_NONPAYMENT,
    FINE_NONPAYMENT,
    ATTENDANCE_CEASED,
    ADMIN_MANUAL,
    VOLUNTARY_EXIT,
    TERMINATED,
    REINSTATED
}
