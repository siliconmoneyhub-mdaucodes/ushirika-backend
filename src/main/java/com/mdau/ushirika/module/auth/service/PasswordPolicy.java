package com.mdau.ushirika.module.auth.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.module.auth.dto.PasswordPolicyDto;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The one place password rules live. Deliberately simple: length 8-72, at least one letter and one
 * digit, nothing derived from the user's own name/email, and not a well-known bad password. No
 * forced uppercase or symbol.
 *
 * Keep in lockstep with the frontend mirror in src/lib/passwordPolicy.ts (ushirika-connect-main).
 * 72 is bcrypt's hard input limit -- anything longer would be silently truncated.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    /** Name/email fragments shorter than this are ignored by the "contains your name" rule, so a
     *  short name like "Al" doesn't reject every password containing those two letters. */
    private static final int MIN_FRAGMENT_LENGTH = 3;

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password12", "password123", "passw0rd", "p@ssw0rd",
            "12345678", "123456789", "1234567890", "11111111", "00000000",
            "qwerty123", "qwertyui", "qwerty12", "1q2w3e4r", "abc12345", "abcd1234",
            "ushirika", "ushirika1", "ushirika123", "ushirika2026", "welcome1", "welcome123",
            "letmein1", "letmein123", "iloveyou", "iloveyou1", "changeme", "changeme1",
            "admin123", "admin1234", "monkey123", "dragon123", "football1", "sunshine1");

    private PasswordPolicy() {}

    /** @throws BadRequestException with a user-facing message on the first failed rule. */
    public static void validate(String raw, String email, String firstName, String lastName) {
        if (raw == null || raw.length() < MIN_LENGTH) {
            throw new BadRequestException("Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (raw.length() > MAX_LENGTH) {
            throw new BadRequestException("Password must be no more than " + MAX_LENGTH + " characters.");
        }
        boolean letter = raw.chars().anyMatch(Character::isLetter);
        boolean digit = raw.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw new BadRequestException("Your password needs at least one letter and one number.");
        }

        String lower = raw.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            throw new BadRequestException("That password is too common. Please choose something less predictable.");
        }

        String local = null;
        if (email != null && !email.isBlank()) {
            int at = email.indexOf('@');
            local = (at > 0 ? email.substring(0, at) : email).trim();
        }
        for (String fragment : new String[]{local, firstName, lastName}) {
            if (fragment == null) continue;
            String f = fragment.trim().toLowerCase(Locale.ROOT);
            if (f.length() >= MIN_FRAGMENT_LENGTH && lower.contains(f)) {
                throw new BadRequestException("Please don't use your name or email address in your password.");
            }
        }
    }

    public static PasswordPolicyDto describe() {
        return new PasswordPolicyDto(MIN_LENGTH, MAX_LENGTH, true, true, List.of(
                "At least " + MIN_LENGTH + " characters",
                "At least one letter and one number",
                "Doesn't contain your name or email address",
                "Not a commonly used password"));
    }
}
