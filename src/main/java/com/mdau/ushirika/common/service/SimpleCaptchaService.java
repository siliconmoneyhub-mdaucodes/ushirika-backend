package com.mdau.ushirika.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ushirika.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Self-hosted proof-of-work verification for public forms — no third-party service, nothing
 * stored server-side. The browser has to burn real CPU time finding a nonce before a submission
 * is accepted, which is what actually raises the cost of automated abuse; a plain arithmetic
 * question (the previous version of this class) can be solved by a five-line regex with zero
 * compute cost, so it only stopped bots too lazy to look at the form at all.
 *
 * How it works: generate() hands out a random challenge string, a difficulty (number of leading
 * hex-zero characters required), and an expiry, all HMAC-signed into a stateless token. The
 * client searches for a nonce such that sha256(challenge + nonce) starts with `difficulty` hex
 * zeros, which takes real, difficulty-scaled work and no shortcuts other than doing the hashing.
 * verify() just recomputes the hash once and checks the prefix — cheap for us, expensive for them
 * at scale. The honeypot field is unrelated and still catches bots that fill in every input
 * blindly without executing JS at all.
 *
 * This does not stop a determined, hand-written bot targeting this one form specifically — no
 * client-side puzzle can. It's combined with per-endpoint rate limiting (see
 * ContactController/MembershipController) as the actual hard cap on abuse volume.
 */
@Slf4j
@Service
public class SimpleCaptchaService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long CHALLENGE_TTL_MS = 5 * 60 * 1000; // 5 minutes to solve and submit
    private static final int MAX_NONCE_LENGTH = 64;

    private final SecretKeySpec key;
    private final int difficulty;

    public SimpleCaptchaService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.captcha.difficulty:4}") int difficulty) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.difficulty = difficulty;
    }

    public record Challenge(String token, String challenge, int difficulty) {}

    private record Payload(String challenge, int difficulty, long exp) {}

    public Challenge generate() {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        String challenge = HexFormat.of().formatHex(raw);
        long exp = System.currentTimeMillis() + CHALLENGE_TTL_MS;
        String token = sign(new Payload(challenge, difficulty, exp));
        return new Challenge(token, challenge, difficulty);
    }

    /** Throws BadRequestException on any failure — invalid/expired/tampered token, unsolved
     *  proof-of-work, or a filled-in honeypot. Callers don't need their own checks first. */
    public void verify(String token, String nonce, String honeypot) {
        if (honeypot != null && !honeypot.isBlank()) {
            log.warn("CAPTCHA honeypot triggered — treating as a bot submission");
            throw new BadRequestException("Verification failed — please try again.");
        }
        if (token == null || token.isBlank() || nonce == null || nonce.isBlank()) {
            throw new BadRequestException("Please wait for verification to finish before submitting.");
        }
        if (nonce.length() > MAX_NONCE_LENGTH) {
            throw new BadRequestException("Invalid verification response.");
        }

        Payload payload = unsign(token);

        if (System.currentTimeMillis() > payload.exp()) {
            throw new BadRequestException("Verification expired — please try again.");
        }
        if (!hasLeadingZeros(sha256Hex(payload.challenge() + nonce), payload.difficulty())) {
            throw new BadRequestException("Verification failed — please try again.");
        }
    }

    private boolean hasLeadingZeros(String hex, int count) {
        if (hex.length() < count) return false;
        for (int i = 0; i < count; i++) {
            if (hex.charAt(i) != '0') return false;
        }
        return true;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
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
            if (!MessageDigest.isEqual(expectedSig, givenSig)) {
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
