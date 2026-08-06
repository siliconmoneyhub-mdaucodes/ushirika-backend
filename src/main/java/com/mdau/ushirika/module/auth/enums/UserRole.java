package com.mdau.ushirika.module.auth.enums;

/**
 * SUPERADMIN        — CEO-level. Manages admin roster, approval thresholds, all config.
 * ADMIN             — Approving official (Secretary General, Treasurer, etc.). All must approve for APPROVED state.
 * FINANCIAL_ADMIN   — Manages all manual payments. Can record, approve/reject, and delegate capabilities to officials.
 * FINANCIAL_OFFICIAL — Can record manual payments. Approval/rejection rights are individually delegated by FINANCIAL_ADMIN.
 * LEADERSHIP        — Read-only view of all admin data. Cannot mutate any records. Assigned to org leadership observers
 *                     (e.g. Chairperson, Vice Chairperson) who need a broad picture but not day-to-day mutation rights.
 * SECRETARY         — Records-keeping: meetings (schedule, edit, take attendance), and read-only membership/member
 *                     records. Does not see financial data or decide fee waivers.
 * CHIEF_WHIP        — Discipline: attendance, fine waivers, and excuse approvals/rejections. Shares the Meetings
 *                     workspace with SECRETARY since attendance-taking and fines are the same day-to-day workflow.
 * COMPLIANCE        — Governance: publishing/editing the constitution & bylaws, deciding reinstatement petitions,
 *                     and a read-only view of the audit trail.
 * MEMBER            — Regular or official member. Can apply, pay, RSVP. May hold an OfficialTitle without approval power.
 * APPLICANT         — Accepted-in-principle applicant completing onboarding (account setup, extra info, bylaws,
 *                     registration fee). Restricted to the /onboarding/** endpoints until membership is approved,
 *                     at which point the same account's role flips to MEMBER.
 */
public enum UserRole {
    SUPERADMIN,
    ADMIN,
    FINANCIAL_ADMIN,
    FINANCIAL_OFFICIAL,
    LEADERSHIP,
    SECRETARY,
    CHIEF_WHIP,
    COMPLIANCE,
    MEMBER,
    APPLICANT
}
