package com.mdau.ushirika.module.report.util;

/** Builds a UTF-8 CSV byte array with BOM so Excel opens it correctly. */
public final class CsvBuilder implements TableBuilder {

    private static final byte[] BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    private final StringBuilder sb = new StringBuilder();
    private boolean firstCol = true;

    private CsvBuilder() {}

    public static CsvBuilder create() { return new CsvBuilder(); }

    @Override
    public CsvBuilder header(String... cols) {
        for (String c : cols) col(c);
        newRow();
        return this;
    }

    @Override
    public CsvBuilder col(Object value) {
        if (!firstCol) sb.append(',');
        firstCol = false;
        String v = value == null ? "" : value.toString();
        // CSV injection guard: a cell that starts with =, +, -, @ or a tab gets parsed as a
        // formula by Excel/Sheets on open, which is a real risk for free-text fields that
        // ultimately came from user input (fine reasons, claim notes, loan purposes, etc.).
        // Restricted to actual String values -- numeric types (BigDecimal amounts, negative
        // balances) still go through as real negative numbers, not text-guarded strings.
        if (value instanceof String && needsFormulaGuard(v)) {
            v = "'" + v;
        }
        // Escape: wrap in quotes if the value contains comma, quote, or newline.
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            sb.append('"').append(v.replace("\"", "\"\"")).append('"');
        } else {
            sb.append(v);
        }
        return this;
    }

    private static boolean needsFormulaGuard(String v) {
        if (v.isEmpty()) return false;
        char c = v.charAt(0);
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t';
    }

    @Override
    public CsvBuilder newRow() {
        sb.append("\r\n");
        firstCol = true;
        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] body = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] out = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, out, 0, BOM.length);
        System.arraycopy(body, 0, out, BOM.length, body.length);
        return out;
    }
}
