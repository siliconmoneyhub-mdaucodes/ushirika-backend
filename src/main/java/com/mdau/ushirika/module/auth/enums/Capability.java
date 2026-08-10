package com.mdau.ushirika.module.auth.enums;

import lombok.Getter;

/**
 * A granular admin permission, independently attachable to any user regardless of their
 * {@link UserRole}. SUPERADMIN implicitly holds every capability (see User#getAuthorities());
 * for everyone else, capabilities are an additive grant on top of whatever their role already
 * allows — assigning one to, say, a plain MEMBER lets them into that one admin domain without
 * promoting their role at all. Each value mirrors an existing admin URL-domain in
 * SecurityConfig so nothing new is invented, just made independently assignable.
 */
@Getter
public enum Capability {
    APPLICATIONS("Applications", "Review, send onboarding forms, and approve membership applications"),
    MEMBERS("Members", "View and manage the member directory"),
    MEETINGS_ATTENDANCE("Meetings & Attendance", "Schedule meetings, take attendance, run QR check-in"),
    DISCIPLINE("Discipline", "Manage fines and decide attendance excuse requests"),
    FINANCE_DUES("Dues & Contributions", "Record and track dues and contribution payments, process cash payments"),
    FINANCE_ADVANCED("Advanced Finance", "Payment links, benevolence, MGR, loans, and financial reports"),
    CONSTITUTION("Constitution & Bylaws", "Publish and edit governing documents"),
    REINSTATEMENT("Reinstatement", "Decide member reinstatement petitions"),
    AUDIT_LOG("Audit Log", "View the platform's audit trail"),
    NOTIFICATIONS("Notifications", "Broadcast and manage member notifications"),
    ELECTIONS("Elections", "Manage elections, seats, and candidacies"),
    CONTENT("Content", "Manage events, news, gallery, stories, scholarships, donations, partners, and leadership"),
    USER_MANAGEMENT("User Management", "Create and edit officials, assign roles and capabilities");

    private final String displayName;
    private final String description;

    Capability(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
