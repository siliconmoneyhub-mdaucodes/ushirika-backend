package com.mdau.ushirika.module.benevolence.util;

import com.mdau.ushirika.module.benevolence.dto.SeedBenevolenceEnrollmentRequest;
import com.mdau.ushirika.module.benevolence.dto.SubmitBeneficiariesRequest.BeneficiaryEntry;
import com.mdau.ushirika.module.benevolence.enums.EnrollmentStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the bulk Benevolence-seeding CSV -- one row per member, up to 6 flat "ben{n}_*" column
 * groups for beneficiaries (a beneficiary slot is included only if its firstName cell is
 * non-blank). See getTemplateCsv() for the exact header a spreadsheet must use.
 *
 * Each row is parsed independently and never throws past parseRows() -- a malformed row becomes
 * a ParsedRow with a parseError instead of aborting the whole file, so one bad row in a
 * 60-member spreadsheet doesn't block the other 59.
 */
public final class BenevolenceSeedCsvParser {

    private static final int MAX_BENEFICIARIES = 6;
    private static final String[] HEADER = {
            "memberEmail", "amountPaid", "status", "probationEndsAt",
    };
    private static final String[] BENEFICIARY_SUFFIXES = {
            "firstName", "lastName", "relationship", "phone", "dob",
    };

    private BenevolenceSeedCsvParser() {}

    public record ParsedRow(int rowNumber, String memberEmail, SeedBenevolenceEnrollmentRequest request, String parseError) {
        public static ParsedRow ok(int rowNumber, SeedBenevolenceEnrollmentRequest request) {
            return new ParsedRow(rowNumber, request.memberEmail(), request, null);
        }
        public static ParsedRow error(int rowNumber, String email, String error) {
            return new ParsedRow(rowNumber, email, null, error);
        }
    }

    public static List<ParsedRow> parse(byte[] csvBytes) {
        String text = new String(csvBytes, StandardCharsets.UTF_8);
        // Strip a UTF-8 BOM if present (common when a spreadsheet app exports "CSV UTF-8").
        if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);

        List<String> lines = new ArrayList<>();
        for (String line : text.split("\r\n|\n|\r")) {
            if (!line.isBlank()) lines.add(line);
        }
        if (lines.isEmpty()) return List.of();

        Map<String, Integer> colIndex = new HashMap<>();
        List<String> headerCells = splitCsvLine(lines.get(0));
        for (int i = 0; i < headerCells.size(); i++) {
            colIndex.put(headerCells.get(i).trim(), i);
        }

        List<ParsedRow> results = new ArrayList<>();
        for (int lineNo = 1; lineNo < lines.size(); lineNo++) {
            int rowNumber = lineNo + 1; // 1-indexed, matches what a spreadsheet shows including the header
            List<String> cells = splitCsvLine(lines.get(lineNo));
            String email = cell(cells, colIndex, "memberEmail");
            try {
                results.add(ParsedRow.ok(rowNumber, parseRow(cells, colIndex)));
            } catch (Exception e) {
                results.add(ParsedRow.error(rowNumber, email, e.getMessage()));
            }
        }
        return results;
    }

    private static SeedBenevolenceEnrollmentRequest parseRow(List<String> cells, Map<String, Integer> colIndex) {
        String email = requireCell(cells, colIndex, "memberEmail");
        BigDecimal amountPaid = new BigDecimal(requireCell(cells, colIndex, "amountPaid").trim());
        EnrollmentStatus status = EnrollmentStatus.valueOf(requireCell(cells, colIndex, "status").trim().toUpperCase());
        String probationRaw = cell(cells, colIndex, "probationEndsAt");
        LocalDate probationEndsAt = probationRaw == null || probationRaw.isBlank() ? null : LocalDate.parse(probationRaw.trim());

        List<BeneficiaryEntry> beneficiaries = new ArrayList<>();
        for (int n = 1; n <= MAX_BENEFICIARIES; n++) {
            String prefix = "ben" + n + "_";
            String firstName = cell(cells, colIndex, prefix + "firstName");
            if (firstName == null || firstName.isBlank()) continue;
            String lastName = cell(cells, colIndex, prefix + "lastName");
            String relationship = cell(cells, colIndex, prefix + "relationship");
            String phone = cell(cells, colIndex, prefix + "phone");
            String dobRaw = cell(cells, colIndex, prefix + "dob");
            LocalDate dob = dobRaw == null || dobRaw.isBlank() ? null : LocalDate.parse(dobRaw.trim());
            beneficiaries.add(new BeneficiaryEntry(
                    firstName.trim(),
                    lastName == null ? "" : lastName.trim(),
                    relationship == null ? "" : relationship.trim(),
                    phone == null ? "" : phone.trim(),
                    dob
            ));
        }
        if (beneficiaries.isEmpty()) {
            throw new IllegalArgumentException("At least one beneficiary (ben1_firstName etc.) is required.");
        }

        return new SeedBenevolenceEnrollmentRequest(email.trim(), amountPaid, status, probationEndsAt, beneficiaries);
    }

    private static String cell(List<String> cells, Map<String, Integer> colIndex, String column) {
        Integer i = colIndex.get(column);
        return i == null || i >= cells.size() ? null : cells.get(i);
    }

    private static String requireCell(List<String> cells, Map<String, Integer> colIndex, String column) {
        String v = cell(cells, colIndex, column);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing required value for column: " + column);
        return v;
    }

    /** The exact header row a spreadsheet must use, ready to hand back as a downloadable
     * starting-point template, with one filled example row (a single beneficiary; the remaining
     * beneficiary slots are left blank -- the same shape a row with just 1-5 beneficiaries takes). */
    public static String getTemplateCsv() {
        List<String> header = new ArrayList<>(List.of(HEADER));
        List<String> example = new ArrayList<>(List.of("jane@example.com", "600.00", "ELIGIBLE", "2026-01-15"));
        List<String> ben1 = List.of("John", "Doe", "Spouse", "+12145550100", "1985-03-20");

        for (int n = 1; n <= MAX_BENEFICIARIES; n++) {
            for (int s = 0; s < BENEFICIARY_SUFFIXES.length; s++) {
                header.add("ben" + n + "_" + BENEFICIARY_SUFFIXES[s]);
                example.add(n == 1 ? ben1.get(s) : "");
            }
        }
        return String.join(",", header) + "\n" + String.join(",", example) + "\n";
    }

    /** Minimal RFC4180-ish line splitter -- handles double-quoted fields with embedded commas and
     * escaped ("") quotes, which is what Excel/Sheets produce when a cell itself contains a comma. */
    private static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
