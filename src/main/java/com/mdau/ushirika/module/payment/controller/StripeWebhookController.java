package com.mdau.ushirika.module.payment.controller;

import com.mdau.ushirika.module.donation.service.DonationService;
import com.mdau.ushirika.module.payment.service.ContributionService;
import com.mdau.ushirika.module.payment.service.PaymentBasketService;
import com.mdau.ushirika.module.payment.service.StripeService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Stripe payment event receiver")
public class StripeWebhookController {

    private final StripeService stripeService;
    private final ContributionService contributionService;
    private final DonationService donationService;
    private final PaymentBasketService paymentBasketService;

    /**
     * Stripe sends all payment events here.
     * Raw body is required for HMAC-SHA256 signature verification.
     * Always returns 200 — Stripe retries on any non-2xx response.
     */
    @PostMapping
    @Operation(summary = "Stripe webhook receiver (internal — do not call manually)")
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody String rawBody
    ) {
        Event event;
        try {
            event = stripeService.constructWebhookEvent(rawBody, signature);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook rejected — invalid signature: {}", e.getMessage());
            return ResponseEntity.status(400).build();
        }

        if (event == null) {
            // Dev mode — no webhook secret configured
            return ResponseEntity.ok().build();
        }

        log.info("Stripe webhook received: type={} id={}", event.getType(), event.getId());

        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> handleSessionCompleted(event);
                default -> log.info("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Failed to process Stripe webhook event={} id={}", event.getType(), event.getId(), e);
        }

        return ResponseEntity.ok().build();
    }

    private void handleSessionCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);

        if (session == null) {
            log.error("Could not deserialize Stripe Session from event id={}", event.getId());
            return;
        }

        String purpose = session.getMetadata() != null
                ? session.getMetadata().getOrDefault("purpose", "CONTRIBUTION")
                : "CONTRIBUTION";

        switch (purpose) {
            case "CONTRIBUTION" -> contributionService.handleSessionCompleted(session);
            case "DONATION"     -> donationService.handleSessionCompleted(session);
            case "BASKET"       -> paymentBasketService.handleSessionCompleted(session);
            default -> log.warn("Unrecognized payment purpose in Stripe webhook metadata: {}", purpose);
        }
    }
}
