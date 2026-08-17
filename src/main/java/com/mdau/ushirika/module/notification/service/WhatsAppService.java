package com.mdau.ushirika.module.notification.service;

import java.util.List;

/**
 * Pluggable WhatsApp abstraction. Implemented by WhatsAppCloudApiService.
 * Only sends pre-approved message templates -- WhatsApp does not permit business-initiated
 * free-form text outside an active 24-hour customer-service window, so unlike SmsService/
 * EmailService there is deliberately no plain-text send() method here.
 */
public interface WhatsAppService {

    /**
     * Sends an approved WhatsApp message template to a single phone number.
     * Phone must include a country code (E.164 or with a leading '+' -- digits are extracted).
     * templateName must exactly match a template already approved in Meta Business Manager;
     * bodyParams fill its {{1}}, {{2}}, ... placeholders in order.
     * Fire-and-forget — runs async, does not throw.
     */
    void sendTemplate(String phone, String recipientName, String templateName, List<String> bodyParams);
}
