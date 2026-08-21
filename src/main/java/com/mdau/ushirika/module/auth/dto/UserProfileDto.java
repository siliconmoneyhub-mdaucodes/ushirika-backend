package com.mdau.ushirika.module.auth.dto;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.Capability;
import com.mdau.ushirika.module.auth.enums.OfficialTitle;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.member.entity.MemberProfile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Full profile for the authenticated user — merges auth fields with the
 * optional MemberProfile row. Field names are intentionally aligned with
 * the frontend User interface so the fetch swap is zero-change on the client.
 */
public record UserProfileDto(
        UUID id,
        String memberId,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        /** Lowercase/snake_case mirror of {@link UserRole}, e.g. "financial_admin", "chief_whip". */
        String role,
        OfficialTitle officialTitle,
        boolean emailVerified,
        boolean active,
        /** "pending" | "inactive" | "active" | "suspended" | "ceased" */
        String status,
        boolean membershipCeased,
        /** ISO date string (YYYY-MM-DD) — null until membership is approved. */
        LocalDate joinedAt,
        /** City of residence — maps to MemberProfile.city. */
        String city,
        String photoUrl,
        /** Lowercase Capability names (e.g. "meetings_attendance") — SUPERADMIN gets every
         *  value listed explicitly here too, matching what getAuthorities() grants them. */
        List<String> capabilities,
        /** Raw MemberStatusReason enum name (e.g. "DUES_NONPAYMENT") for the most recent
         *  active/membershipCeased change, null if none has ever been tracked. See
         *  MemberStatusChangeService. */
        String currentStatusReason,
        LocalDateTime currentStatusChangedAt
) {
    /**
     * Backward-compatible overload — callers that don't have dues context
     * pass null for duesStatus (approved members will show "active").
     */
    public static UserProfileDto from(User user, MemberProfile profile) {
        return from(user, profile, null);
    }

    /**
     * Full overload used by endpoints that need accurate "inactive" status.
     * duesStatus: "PAID" | "WAIVED" | "PENDING" | "OVERDUE" | null
     */
    public static UserProfileDto from(User user, MemberProfile profile, String duesStatus) {
        String memberId  = profile != null ? profile.getMemberId()    : null;
        LocalDate joined = profile != null ? profile.getMemberSince() : null;
        String city      = profile != null ? profile.getCity()        : null;
        String photoUrl  = profile != null ? profile.getPhotoUrl()    : null;

        String status;
        if (user.isMembershipCeased()) {
            status = "ceased";
        } else if (!user.isActive()) {
            status = "suspended";
        } else if (user.getRole() == UserRole.APPLICANT) {
            // Mid-onboarding — not yet a member, not staff.
            status = "pending";
        } else if (user.getRole() != UserRole.MEMBER) {
            // Staff roles are always considered active regardless of dues
            status = "active";
        } else if (memberId == null) {
            // Approved email but application not yet reviewed
            status = "pending";
        } else if ("PAID".equals(duesStatus) || "WAIVED".equals(duesStatus)) {
            status = "active";
        } else if ("PENDING".equals(duesStatus) || "OVERDUE".equals(duesStatus)) {
            // Member approved but annual dues not yet paid
            status = "inactive";
        } else {
            // duesStatus null → no dues record exists yet; treat as inactive
            status = "inactive";
        }

        String role = switch (user.getRole()) {
            case MEMBER              -> "member";
            case ADMIN               -> "admin";
            case SUPERADMIN          -> "superadmin";
            case LEADERSHIP          -> "leadership";
            case APPLICANT           -> "applicant";
            case SECRETARY           -> "secretary";
            case CHIEF_WHIP          -> "chief_whip";
            case COMPLIANCE          -> "compliance";
            case FINANCIAL_ADMIN     -> "financial_admin";
            case FINANCIAL_OFFICIAL  -> "financial_official";
        };

        List<String> capabilities = user.getRole() == UserRole.SUPERADMIN
                ? Arrays.stream(Capability.values()).map(c -> c.name().toLowerCase()).toList()
                : user.getCapabilities().stream().map(c -> c.name().toLowerCase()).toList();

        return new UserProfileDto(
                user.getId(),
                memberId,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getFullName(),
                user.getPhone(),
                role,
                user.getOfficialTitle(),
                user.isEmailVerified(),
                user.isActive(),
                status,
                user.isMembershipCeased(),
                joined,
                city,
                photoUrl,
                capabilities,
                user.getCurrentStatusReason() != null ? user.getCurrentStatusReason().name() : null,
                user.getCurrentStatusChangedAt()
        );
    }
}
