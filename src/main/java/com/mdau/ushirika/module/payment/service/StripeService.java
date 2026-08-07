package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
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

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setCustomerEmail(email)
                .setSuccessUrl(successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl);

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

        try {
            Session session = Session.create(builder.build());
            log.info("[Stripe] Checkout session created: id={} lineItems={} email={}",
                    session.getId(), lineItems.size(), email);
            return new StripeCheckoutResult(session.getId(), session.getUrl());
        } catch (StripeException e) {
            log.error("[Stripe] Failed to create checkout session: {}", e.getMessage());
            throw new BadRequestException("Payment initialization failed: " + e.getMessage());
        }
    }

    public record LineItem(String productName, BigDecimal amountUsd) {}

    /**
     * Charges a card that was tokenized client-side via Stripe Elements (paymentMethodId) --
     * used for admin-entered card payments (the member's card, relayed by phone/in person,
     * never typed into a form on our backend). Confirms immediately; if the card requires 3D
     * Secure the returned result has status "requires_action" and a clientSecret the frontend
     * must pass to stripe.confirmCardPayment() to finish.
     */
    public PaymentIntentResult createAndConfirmPaymentIntent(
            BigDecimal amountUsd,
            String paymentMethodId,
            String description,
            Map<String, String> metadata
    ) {
        if (devMode) {
            if (!allowDevFallback) {
                throw new IllegalStateException(
                        "Stripe is not configured (STRIPE_SECRET_KEY missing) and dev-fallback simulation is disabled. "
                                + "Card payments cannot be processed until Stripe is configured.");
            }
            String fakeId = "pi_dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            log.warn("[Stripe DEV] Simulating PaymentIntent for paymentMethodId={} amount={}", paymentMethodId, amountUsd);
            return new PaymentIntentResult(fakeId, "succeeded", null);
        }

        long amountCents = amountUsd.multiply(BigDecimal.valueOf(100)).longValue();
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency("usd")
                .setPaymentMethod(paymentMethodId)
                .setConfirm(true)
                .setDescription(description)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                .build());

        if (metadata != null) {
            metadata.forEach(builder::putMetadata);
        }

        try {
            PaymentIntent intent = PaymentIntent.create(builder.build());
            log.info("[Stripe] PaymentIntent created: id={} status={} amount={}",
                    intent.getId(), intent.getStatus(), amountUsd);
            return new PaymentIntentResult(intent.getId(), intent.getStatus(), intent.getClientSecret());
        } catch (StripeException e) {
            log.error("[Stripe] Failed to create/confirm PaymentIntent: {}", e.getMessage());
            throw new BadRequestException("Card charge failed: " + e.getMessage());
        }
    }

    public record PaymentIntentResult(String paymentIntentId, String status, String clientSecret) {}

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
