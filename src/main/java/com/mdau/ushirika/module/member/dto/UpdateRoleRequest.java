package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.auth.enums.Capability;
import com.mdau.ushirika.module.auth.enums.OfficialTitle;
import com.mdau.ushirika.module.auth.enums.UserRole;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateRoleRequest(

        @NotNull(message = "Role is required")
        UserRole role,

        /** Official organizational title — can be set independently of role.
         *  A MEMBER can hold a title (e.g. BENEVOLENCE_COORDINATOR) without admin approval powers. */
        OfficialTitle officialTitle,

        /** Granular admin permissions attached independently of role — null/omitted means
         *  "leave capabilities unchanged" is NOT assumed; the service always replaces the full
         *  set, so pass the complete desired set (or an empty set to clear all). */
        Set<Capability> capabilities
) {}
