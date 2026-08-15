package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class StripeService {

    private final String secretKey;
    private final String webhookSecret;
    private final boolean devMode;
    private final boolean allowDevFallback;

    public StripeService(
            @Value("${app.stripe.secret-key:NOT_SET}") String secretKey,
            @Value("${app.stripe.webhook-secret:NOT_SET}") String webhookSecret,
            @Value("${app.stripe.allow-dev-fallback:false}") boolean allowDevFallback
    ) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.devMode = "NOT_SET".equals(secretKey);
        this.allowDevFallback = allowDevFallback;
    }

    @PostConstruct
    void init() {
        if (!devMode) {
            Stripe.apiKey = secretKey;
        }
    }

    /**
     * Creates a Stripe Checkout Session for a one-time USD payment with a single line item.
     * Convenience wrapper around {@link #createCheckoutSession(String, List, String, String, Map)}.
     */
    public StripeCheckoutResult createCheckoutSession(
            String email,
            BigDecimal amountUsd,
            String productName,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata
    ) {
        return createCheckoutSession(email, List.of(new LineItem(productName, amountUsd)), successUrl, cancelUrl, metadata);
    }

    /**
     * Creates a Stripe Checkout Session covering one or more line items in a single card charge.
     * Returns the session ID and hosted checkout URL. Amounts are in USD — converted to cents for Stripe.
     */
    public StripeCheckoutResult createCheckoutSession(
            String email,
            List<LineItem> lineItems,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata
    ) {
        if (lineItems == null || lineItems.isEmpty()) {
            throw new BadRequestException("At least one line item is required to start checkout.");
        }

        if (devMode) {
            if (!allowDevFallback) {
                throw new IllegalStateException(
                        "Stripe is not configured (STRIPE_SECRET_KEY missing) and dev-fallback simulation is disabled. "
                                + "Card payments cannot be processed until Stripe is configured.");
            }
            String fakeSessionId = "cs_dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            log.warn("[Stripe DEV] Simulating checkout session for email={} lineItems={}", email, lineItems);
            return new StripeCheckoutResult(fakeSessionId,
                    "https://checkout.stripe.com/dev/" + fakeSessionId);
        }

        SessionCreateParams.Builder builder = buildSessionParams(email, lineItems, successUrl, cancelUrl, metadata,
                SessionCreateParams.PaymentMethodType.CARD, SessionCreateParams.PaymentMethodType.CASHAPP);

        try {
            Session session = Session.create(builder.build());
            log.info("[Stripe] Checkout session created: id={} lineItems={} email={}",
                    session.getId(), lineItems.size(), email);
            return new StripeCheckoutResult(session.getId(), session.getUrl());
        } catch (StripeException e) {
            // Cash App Pay must be toggled on per-account in the Stripe Dashboard; until that's done,
            // Stripe rejects the session outright. Retry card-only so a missing Dashboard toggle can't
            // take down card checkout too.
            log.warn("[Stripe] Checkout session with Cash App Pay failed ({}) — retrying card-only. "
                    + "If this keeps happening, confirm Cash App Pay is enabled in the Stripe Dashboard.", e.getMessage());
            SessionCreateParams.Builder cardOnlyBuilder = buildSessionParams(email, lineItems, successUrl, cancelUrl,
                    metadata, SessionCreateParams.PaymentMethodType.CARD);
            try {
                Session session = Session.create(cardOnlyBuilder.build());
                log.info("[Stripe] Checkout session created (card-only fallback): id={} lineItems={} email={}",
                        session.getId(), lineItems.size(), email);
                return new StripeCheckoutResult(session.getId(), session.getUrl());
            } catch (StripeException fallbackException) {
                log.error("[Stripe] Failed to create checkout session: {}", fallbackException.getMessage());
                throw new BadRequestException("Payment initialization failed: " + fallbackException.getMessage());
            }
        }
    }

    private SessionCreateParams.Builder buildSessionParams(
            String email,
            List<LineItem> lineItems,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata,
            SessionCreateParams.PaymentMethodType... paymentMethodTypes
    ) {
        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(email)
                .setSuccessUrl(successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl);

        for (SessionCreateParams.PaymentMethodType type : paymentMethodTypes) {
            builder.addPaymentMethodType(type);
        }

        for (LineItem item : lineItems) {
            long amountCents = item.amountUsd().multiply(BigDecimal.valueOf(100)).longValue();
            builder.addLineItem(SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("usd")
                            .setUnitAmount(amountCents)
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(item.productName())
                                    .build())
                            .build())
                    .build());
        }

        if (metadata != null) {
            metadata.forEach(builder::putMetadata);
        }

        return builder;
    }

    public record LineItem(String productName, BigDecimal amountUsd) {}

    /**
     * Verifies the Stripe-Signature header and constructs the typed Event.
     * Throws SignatureVerificationException if the signature is invalid.
     */
    /**
     * Verifies the Stripe-Signature header and constructs the typed Event.
     * Returns null in dev mode (no real Stripe credentials — webhooks are untestable locally).
     * Throws SignatureVerificationException if the signature does not match in prod.
     */
    public Event constructWebhookEvent(String rawBody, String stripeSignature) throws SignatureVerificationException {
        if (devMode) {
            log.warn("[Stripe DEV] No webhook secret configured — ignoring inbound webhook");
            return null;
        }
        return Webhook.constructEvent(rawBody, stripeSignature, webhookSecret);
    }

    public record StripeCheckoutResult(String sessionId, String checkoutUrl) {}
}
