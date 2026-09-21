package com.mdau.ushirika.module.auth.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    private static void ok(String pw) {
        assertDoesNotThrow(() -> PasswordPolicy.validate(pw, "brian.otieno@example.com", "Brian", "Otieno"), pw);
    }

    private static String reject(String pw) {
        return assertThrows(BadRequestException.class,
                () -> PasswordPolicy.validate(pw, "brian.otieno@example.com", "Brian", "Otieno"), pw).getMessage();
    }

    @Test
    void acceptsLetterAndDigit_noUppercaseOrSymbolNeeded() {
        ok("harambee2026");
        ok("tumefika7");
        ok("a1b2c3d4");
    }

    @Test
    void rejectsTooShort() {
        assertTrue(reject("abc1234").contains("at least 8"));
        assertTrue(reject("").contains("at least 8"));
    }

    @Test
    void rejectsNull() {
        assertThrows(BadRequestException.class, () -> PasswordPolicy.validate(null, "a@b.com", "A", "B"));
    }

    @Test
    void rejectsTooLong_bcryptLimit() {
        assertTrue(reject("a1".repeat(37)).contains("no more than 72"));
        ok("a1".repeat(36)); // exactly 72
    }

    @Test
    void requiresBothALetterAndADigit() {
        assertTrue(reject("onlyletters").contains("letter and one number"));
        assertTrue(reject("1234567890123").contains("letter and one number"));
        assertTrue(reject("!!!!!!!!!!").contains("letter and one number"));
    }

    @Test
    void rejectsCommonPasswords_caseInsensitively() {
        assertTrue(reject("Password1").contains("too common"));
        assertTrue(reject("QWERTY123").contains("too common"));
        assertTrue(reject("Ushirika1").contains("too common"));
        assertTrue(reject("Welcome1").contains("too common"));
    }

    @Test
    void rejectsPasswordsContainingNameOrEmailLocalPart() {
        assertTrue(reject("Brian2026!").contains("name or email"));
        assertTrue(reject("mr-OTIENO-77").contains("name or email"));
        assertTrue(reject("xbrian.otienox9").contains("name or email"));
    }

    @Test
    void shortNameFragmentsDoNotOverReject() {
        // "Al" is below the fragment threshold, so a password that merely contains "al" is fine.
        assertDoesNotThrow(() -> PasswordPolicy.validate("normal-pass1", "al@example.com", "Al", "Li"));
    }

    @Test
    void nullNamesAreTolerated() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("harambee2026", null, null, null));
    }

    @Test
    void describe_matchesTheEnforcedLimits() {
        var d = PasswordPolicy.describe();
        assertEquals(8, d.minLength());
        assertEquals(72, d.maxLength());
        assertTrue(d.requireLetter());
        assertTrue(d.requireDigit());
        assertFalse(d.rules().isEmpty());
    }
}
