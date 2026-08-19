package com.mdau.ushirika.module.notification.service;

/**
 * The WhatsApp-eligible notification categories, per the agreed channel-split policy: time-
 * sensitive/action-required notifications go via WhatsApp (with email always also sent while the
 * channel is unproven); long-form/record-keeping notifications (announcements, receipts,
 * newsletter, application-status updates) stay email-only and never appear here.
 *
 * Each category maps to exactly one WhatsApp template (see NotificationDispatcher.TEMPLATES).
 * Categories are kept deliberately specific rather than sharing one generic "here's a message"
 * template -- Meta's Utility-category review flagged an earlier generic template as Marketing-like
 * because its body was mostly a free-form variable; a template whose fixed text states the actual
 * account event (an application, a specific cycle, a specific payout) reads unambiguously as
 * Utility instead.
 */
public enum NotificationCategory {
    /** Dues, fines, MGR contributions, benevolence replenishment -- anything "$X due on Y". */
    PAYMENT_REMINDER,
    /** Meeting/event 24h and 6h upcoming reminders. */
    MEETING_EVENT_REMINDER,
    /** Benevolence/MGR Send Form notices -- "your program application is ready to complete." */
    APPLICATION_READY,
    /** MGR automated per-cycle waitlist opt-in ask -- "cycle X is open, join or keep waiting?" */
    CYCLE_INVITE,
    /** MGR payout disbursed -- "please confirm receipt." */
    PAYOUT_CONFIRMATION,
    /** MGR monthly draw results (who was drawn this month, to all cycle members). */
    MGR_DRAW_RESULT,
    /** Election voting-closes reminders. */
    ELECTION_REMINDER,
    /** Welfare claim status updates (submitted/approved/rejected/disbursed). */
    WELFARE_CLAIM_UPDATE
}
