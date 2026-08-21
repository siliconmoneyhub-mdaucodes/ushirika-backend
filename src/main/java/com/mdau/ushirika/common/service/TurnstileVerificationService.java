package com.mdau.ushirika.common.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Verifies Cloudflare Turnstile CAPTCHA tokens on public forms (membership enquiry, contact) --
 * IP rate limiting alone doesn't stop a slow or distributed spam run. Skips verification entirely
 * when TURNSTILE_SECRET_KEY isn't set, the same dev-fallback pattern as WhatsAppCloudApiService,
 * so the backend can deploy this ahead of the frontend widget without blocking real submissions;
 * enforcement only turns on once the env var is actually set.
 */
@Slf4j
@Service
public class TurnstileVerificationService {

    private final RestClient restClient;
    private final String secretKey;
    private final boolean configured;

    public TurnstileVerificationService(
            RestClient.Builder restClientBuilder,
            @Value("${app.turnstile.secret-key:NOT_SET}") String secretKey
    ) {
        this.secretKey = secretKey;
        this.configured = !"NOT_SET".equals(secretKey);
        this.restClient = restClientBuilder
                .baseUrl("https://challenges.cloudflare.com/turnstile/v0/siteverify")
                .build();
    }

    /** No-ops while unconfigured. Once configured, a missing, invalid, or expired token throws
     * BadRequestException -- callers don't need their own null-check first. */
    public void verify(String token) {
        if (!configured) return;
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Please complete the verification challenge before submitting.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);

        try {
            TurnstileResponse result = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TurnstileResponse.class);
            if (result == null || !result.success()) {
                throw new BadRequestException("Verification failed — please try again.");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Turnstile verification request failed: {}", e.getMessage());
            throw new BadRequestException("Could not verify — please try again.");
        }
    }

    private record TurnstileResponse(boolean success) {}
}
