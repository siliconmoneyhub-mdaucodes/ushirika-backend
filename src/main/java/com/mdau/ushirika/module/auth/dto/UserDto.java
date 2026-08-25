package com.mdau.ushirika.module.auth.dto;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.Capability;
import com.mdau.ushirika.module.auth.enums.OfficialTitle;
import com.mdau.ushirika.module.auth.enums.UserRole;

import java.util.Set;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String firstName,
        String middleName,
        String lastName,
        String fullName,
        String phone,
        UserRole role,
        OfficialTitle officialTitle,
        boolean emailVerified,
        boolean active,
        /** Granular admin permissions attached independently of role — see Capability. Always
         *  the user's actual stored set, even for SUPERADMIN (who is granted every capability
         *  implicitly at the authorization layer regardless of what's stored here). */
        Set<Capability> capabilities
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getMiddleName(),
                user.getLastName(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getOfficialTitle(),
                user.isEmailVerified(),
                user.isActive(),
                user.getCapabilities()
        );
    }
}
