package com.mdau.ushirika.module.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ushirika.module.donation.service.DonationService;
import com.mdau.ushirika.module.payment.service.ContributionService;
import com.mdau.ushirika.module.payment.service.PaymentBasketService;
import com.mdau.ushirika.module.payment.service.StripeService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
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
    private final ObjectMapper objectMapper;

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
                case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
                default -> log.info("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Failed to process Stripe webhook event={} id={}", event.getType(), event.getId(), e);
        }

        return ResponseEntity.ok().build();
    }

    private void handleSessionCompleted(Event event) throws com.stripe.exception.StripeException {
        Session session = resolveDataObject(event, Session.class, Session::retrieve);
        if (session == null) {
            log.error("Could not resolve Stripe Session from event id={}", event.getId());
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

    private void handlePaymentIntentSucceeded(Event event) throws com.stripe.exception.StripeException {
        PaymentIntent intent = resolveDataObject(event, PaymentIntent.class, PaymentIntent::retrieve);
        if (intent == null) {
            log.error("Could not resolve Stripe PaymentIntent from event id={}", event.getId());
            return;
        }

        paymentBasketService.handlePaymentIntentSucceeded(intent.getId());
    }

    /**
     * event.getDataObjectDeserializer().getObject() can silently return empty when the event's
     * serialized API version doesn't match this SDK's compiled model version -- confirmed live,
     * every webhook here failed with "Could not deserialize Stripe Session/PaymentIntent from
     * event" even though the signature verified fine and Stripe marked the delivery successful.
     * Falls back to pulling just the id out of the raw JSON and re-fetching the object fresh via
     * the API, which sidesteps the version mismatch entirely -- Stripe's own recommended pattern
     * for this exact failure mode.
     */
    private <T> T resolveDataObject(Event event, Class<T> type, StripeRetriever<T> retriever)
            throws com.stripe.exception.StripeException {
        Object deserialized = event.getDataObjectDeserializer().getObject().orElse(null);
        if (type.isInstance(deserialized)) {
            return type.cast(deserialized);
        }

        String rawJson = event.getDataObjectDeserializer().getRawJson();
        if (rawJson == null) return null;

        com.fasterxml.jackson.databind.JsonNode node;
        try {
            node = objectMapper.readTree(rawJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Could not parse raw Stripe event JSON for event id={}", event.getId(), e);
            return null;
        }
        if (!node.has("id")) return null;

        return retriever.retrieve(node.get("id").asText());
    }

    @FunctionalInterface
    private interface StripeRetriever<T> {
        T retrieve(String id) throws com.stripe.exception.StripeException;
    }
}
