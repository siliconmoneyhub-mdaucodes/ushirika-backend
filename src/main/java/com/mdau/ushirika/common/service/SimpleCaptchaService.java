package com.mdau.ushirika.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ushirika.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Self-hosted replacement for the earlier Cloudflare Turnstile integration — a small arithmetic
 * challenge ("What is 6 + 3?") backed by a signed, stateless token, plus a honeypot field and a
 * minimum-elapsed-time check. No third-party service, no per-request cost, nothing to configure
 * beyond the app already having a secret key.
 *
 * How it stops the actual threat (bulk/scripted form spam, not a targeted human attacker):
 *   - A challenge's two operands and expiry are HMAC-signed into the token itself
 *     (base64(payload) + "." + base64(signature)) — nothing is stored server-side, and a client
 *     can't forge a token or tamper with the operands without invalidating the signature.
 *   - A blind mass-mailer bot that POSTs every &lt;form&gt; it finds without executing JS never
 *     requests a challenge at all, so captchaToken/captchaAnswer are absent -> rejected outright.
 *   - A bot that does fetch the challenge but answers instantly gets caught by the minimum-age
 *     check (real humans take at least ~1.5s to read a question and type a one-digit answer).
 *   - The honeypot field (verify(..., honeypot)) is never visible to a real user but is commonly
 *     auto-filled by generic form-filling bots -- any non-blank value fails verification.
 * None of this stops a targeted, hand-written attack against this one form specifically -- that
 * was never the threat model IP rate limiting + this class are defending against.
 */
@Slf4j
@Service
public class SimpleCaptchaService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long CHALLENGE_TTL_MS = 5 * 60 * 1000;   // 5 minutes to answer
    private static final long MIN_ANSWER_AGE_MS = 1500;           // faster than this is not a human

    private final SecretKeySpec key;

    public SimpleCaptchaService(@Value("${app.jwt.secret}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public record Challenge(String token, String question) {}

    private record Payload(int a, int b, long exp) {}

    public Challenge generate() {
        int a = 2 + RANDOM.nextInt(8);   // 2..9
        int b = 2 + RANDOM.nextInt(8);   // 2..9
        long exp = System.currentTimeMillis() + CHALLENGE_TTL_MS;
        String token = sign(new Payload(a, b, exp));
        return new Challenge(token, "What is " + a + " + " + b + "?");
    }

    /** Throws BadRequestException on any failure — invalid/expired/tampered token, wrong answer,
     *  answered too fast, or a filled-in honeypot. Callers don't need their own checks first. */
    public void verify(String token, Integer answer, String honeypot) {
        if (honeypot != null && !honeypot.isBlank()) {
            log.warn("CAPTCHA honeypot triggered — treating as a bot submission");
            throw new BadRequestException("Verification failed — please try again.");
        }
        if (token == null || token.isBlank() || answer == null) {
            throw new BadRequestException("Please complete the verification challenge before submitting.");
        }

        Payload payload = unsign(token);
        long now = System.currentTimeMillis();
        long issuedAt = payload.exp() - CHALLENGE_TTL_MS;

        if (now > payload.exp()) {
            throw new BadRequestException("Verification expired — please try again.");
        }
        if (now - issuedAt < MIN_ANSWER_AGE_MS) {
            log.warn("CAPTCHA answered implausibly fast ({} ms) — treating as a bot submission", now - issuedAt);
            throw new BadRequestException("Verification failed — please try again.");
        }
        if (answer != payload.a() + payload.b()) {
            throw new BadRequestException("That answer isn't quite right — please try again.");
        }
    }

    private String sign(Payload payload) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(payload);
            String body = Base64.getUrlEncoder().withoutPadding().encodeToString(json);
            byte[] sig = newMac().doFinal(json);
            String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
            return body + "." + sigB64;
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign CAPTCHA challenge", e);
        }
    }

    private Payload unsign(String token) {
        try {
            String[] parts = token.split("\\.", 2);
            if (parts.length != 2) throw new BadRequestException("Invalid verification token.");
            byte[] json = Base64.getUrlDecoder().decode(parts[0]);
            byte[] expectedSig = newMac().doFinal(json);
            byte[] givenSig = Base64.getUrlDecoder().decode(parts[1]);
            if (!java.security.MessageDigest.isEqual(expectedSig, givenSig)) {
                throw new BadRequestException("Invalid verification token.");
            }
            return MAPPER.readValue(json, Payload.class);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Invalid verification token.");
        }
    }

    private Mac newMac() {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac;
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize CAPTCHA signing key", e);
        }
    }
}
