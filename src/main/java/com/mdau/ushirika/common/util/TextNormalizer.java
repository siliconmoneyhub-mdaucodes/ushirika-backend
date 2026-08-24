package com.mdau.ushirika.common.util;

/** Shared input normalization applied server-side, on save, regardless of what the client sent
 *  or whether it even went through the frontend's own formatting -- the frontend's live
 *  formatting is a UX nicety, this is the actual correctness guarantee. */
public final class TextNormalizer {

    private TextNormalizer() {}

    /**
     * "JOHN DOE" / "john doe" -&gt; "John Doe". Capitalizes the letter after any whitespace,
     * hyphen, or apostrophe so "mary o'brien" -&gt; "Mary O'Brien" and "smith-jones" ->
     * "Smith-Jones". Deliberately simple -- it won't get culturally-specific lowercase
     * particles right (e.g. "van der berg"), but that's a smaller failure mode than the raw
     * ALL-CAPS/all-lowercase input it replaces.
     */
    public static String titleCase(String input) {
        if (input == null) return null;
        String trimmed = input.strip();
        if (trimmed.isEmpty()) return trimmed;

        StringBuilder result = new StringBuilder(trimmed.length());
        boolean capitalizeNext = true;
        for (char c : trimmed.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-' || c == '\'') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

    /** Trims and lowercases an email so uniqueness checks and storage never diverge on case. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase();
    }
}
