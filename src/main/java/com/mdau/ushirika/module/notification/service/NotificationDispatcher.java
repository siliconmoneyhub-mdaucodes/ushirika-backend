package com.mdau.ushirika.module.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Central chokepoint for the WhatsApp side of notifications. Existing per-scheduler email/SMS/
 * in-app sends are left as-is -- their content is too bespoke per call-site to usefully
 * centralize -- but the WhatsApp template name and the send-failure handling were starting to be
 * duplicated verbatim across every scheduler, so that part lives here instead: one place that
 * knows which category maps to which approved template, and one place with the try/catch.
 *
 * Currently additive, not a true primary/fallback relationship -- every WhatsApp-eligible
 * category also keeps sending its existing email (and SMS, where wired) unconditionally, since
 * this channel is still unproven in production. Once WhatsApp delivery is confirmed reliable, a
 * category can be flipped to WhatsApp-primary/email-fallback by changing its call site to only
 * send email when dispatchWhatsApp() indicates no send happened (see the boolean return).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final WhatsAppService whatsAppService;

    /** category -> approved WhatsApp template name. A category with no entry here has no
     * approved template yet -- dispatchWhatsApp() no-ops for it instead of failing, so call-sites
     * don't need to check readiness themselves. Add an entry the moment a template clears Meta
     * review; no call-site changes needed. */
    private static final Map<NotificationCategory, String> TEMPLATES = Map.of(
            NotificationCategory.PAYMENT_REMINDER, "payment_reminder"
            // MEETING_EVENT_REMINDER -> event_reminder, APPLICATION_READY -> application_ready,
            // CYCLE_INVITE -> cycle_invite, PAYOUT_CONFIRMATION -> payout_confirmation,
            // MGR_DRAW_RESULT -> mgr_draw_result, ELECTION_REMINDER -> election_reminder,
            // WELFARE_CLAIM_UPDATE -> claim_update: none approved yet -- add here once each
            // clears review (Map.of() above needs switching to Map.ofEntries(...) once more than
            // one entry is active).
    );

    /**
     * Sends the WhatsApp side of a notification, if this category has an approved template and
     * the member has a phone number. Fire-and-forget; failures are logged, never thrown.
     * Returns true if a send was actually attempted (template exists + phone present) -- callers
     * that want WhatsApp-primary/email-fallback behavior can use this to decide whether to still
     * send email; today, every call-site sends email unconditionally regardless of this value.
     */
    public boolean dispatchWhatsApp(NotificationCategory category, String phone, String recipientName, List<String> bodyParams) {
        if (phone == null) return false;
        String templateName = TEMPLATES.get(category);
        if (templateName == null) return false;
        try {
            whatsAppService.sendTemplate(phone, recipientName, templateName, bodyParams);
            return true;
        } catch (Exception e) {
            log.warn("WhatsApp dispatch failed for category={} phone={}: {}", category, phone, e.getMessage());
            return false;
        }
    }
}
