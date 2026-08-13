package com.mdau.ushirika.module.auth.enums;

import lombok.Getter;

/**
 * Organizational position — separate from UserRole.
 * A member can hold a title without having admin/approval power.
 * A SUPERADMIN assigns both role and title.
 */
@Getter
public enum OfficialTitle {
    EXECUTIVE_CHAIRMAN("Chairman"),
    EXECUTIVE_VICE_CHAIRMAN("Vice Chairman"),
    EXECUTIVE_SECRETARY("Secretary"),
    EXECUTIVE_VICE_SECRETARY("Vice Secretary"),
    EXECUTIVE_TREASURER("Treasurer"),
    EXECUTIVE_CHIEF_WHIP("Chief Whip"),
    BENEVOLENCE_COORDINATOR("Benevolence Coordinator");

    private final String displayName;

    OfficialTitle(String displayName) {
        this.displayName = displayName;
    }
}
