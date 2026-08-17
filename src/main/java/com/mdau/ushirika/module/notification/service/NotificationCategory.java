package com.mdau.ushirika.module.notification.service;

/**
 * The WhatsApp-eligible notification categories, per the agreed channel-split policy: time-
 * sensitive/action-required notifications go via WhatsApp (with email always also sent while the
 * channel is unproven); long-form/record-keeping notifications (announcements, receipts,
 * newsletter, application-status updates) stay email-only and never appear here.
 */
public enum NotificationCategory {
    /** Dues, fines, MGR contributions, benevolence replenishment -- anything "$X due on Y". */
    PAYMENT_REMINDER,
    /** Meeting/event 24h and 6h upcoming reminders. */
    MEETING_EVENT_REMINDER,
    /** Benevolence/MGR Send Form, cycle-invite ask, enrolled/waitlisted confirmations -- anything
     * asking the member to go take an action in their portal. */
    PROGRAM_ACTION_REQUIRED,
    /** MGR monthly draw results and payout notifications. */
    MGR_DRAW_RESULT,
    /** Election reminders / live vote notices. */
    ELECTION_REMINDER,
    /** Welfare claim status updates. */
    WELFARE_CLAIM_UPDATE
}
