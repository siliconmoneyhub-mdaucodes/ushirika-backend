package com.mdau.ushirika.module.auth.dto;

import java.util.List;

/** Public description of the password rules. Keep in lockstep with the frontend's
 *  src/lib/passwordPolicy.ts and with {@code PasswordPolicy#validate}. */
public record PasswordPolicyDto(int minLength, int maxLength,
                                boolean requireLetter, boolean requireDigit,
                                List<String> rules) {}
