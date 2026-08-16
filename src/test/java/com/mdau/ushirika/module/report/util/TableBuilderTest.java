package com.mdau.ushirika.module.report.util;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Covers the report builders against the scenarios a real admin's data can actually produce --
 * not just the happy path of two clean rows. Every report type in ReportService funnels through
 * exactly these three builders, so a bug caught here protects all ~14 report types at once. */
class TableBuilderTest {

    private void populate(TableBuilder table) {
        table.header("Name", "Amount", "Active");
        table.col("Alice").col(100).col(true).newRow();
        table.col("Bob").col(250.5).col(false).newRow();
    }

    @Test
    void csv_producesUtf8BomAndRows() {
        CsvBuilder csv = CsvBuilder.create();
        populate(csv);
        byte[] bytes = csv.toBytes();
        assertTrue(bytes.length > 0);
        // UTF-8 BOM
        assertEquals(0xEF, bytes[0] & 0xFF);
        assertEquals(0xBB, bytes[1] & 0xFF);
        assertEquals(0xBF, bytes[2] & 0xFF);
    }

    @Test
    void xlsx_producesValidZipPackage() throws java.io.IOException {
        XlsxBuilder xlsx = XlsxBuilder.create("Test");
        populate(xlsx);
        byte[] bytes = xlsx.toBytes();
        assertTrue(bytes.length > 100);
        // XLSX is a ZIP archive — "PK" magic bytes
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
        // Round-trip through POI itself -- the strongest available check that the title block,
        // styling, borders, freeze pane, and auto-filter didn't corrupt the workbook.
        try (var wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheetAt(0);
            assertEquals("Name", sheet.getRow(4).getCell(0).getStringCellValue()); // header row, after the 4 title rows
            assertEquals("Alice", sheet.getRow(5).getCell(0).getStringCellValue());
        }
    }

    @Test
    void pdf_producesValidPdfDocument() {
        PdfBuilder pdf = PdfBuilder.create("Test Report");
        populate(pdf);
        byte[] bytes = pdf.toBytes();
        assertTrue(bytes.length > 100);
        assertEquals("%PDF", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }

    // ── Real-world scenario: real users, not just "Alice"/"Bob" ────────────────────

    @Test
    void csv_specialCharactersAndUnicode_escapedCorrectly() {
        CsvBuilder csv = CsvBuilder.create();
        csv.header("Name", "Notes");
        csv.col("O'Brien, Jr.").col("Line one\nLine two \"quoted\"").newRow();
        csv.col("José Ñoño 😀").col("Δοκιμή — тест").newRow();
        String text = new String(csv.toBytes(), StandardCharsets.UTF_8);
        // Comma + quote inside a field forces quoting, with internal quotes doubled per RFC 4180.
        assertTrue(text.contains("\"O'Brien, Jr.\""));
        assertTrue(text.contains("\"Line one\nLine two \"\"quoted\"\"\""));
        // Unicode (accents, emoji, Greek, Cyrillic) survives untouched -- CSV is just UTF-8 text.
        assertTrue(text.contains("José Ñoño 😀"));
        assertTrue(text.contains("Δοκιμή — тест"));
    }

    @Test
    void csv_formulaInjection_stringValuesGuarded_numericValuesUntouched() {
        CsvBuilder csv = CsvBuilder.create();
        csv.header("Field", "Balance");
        // A free-text field starting with a formula-trigger character must not be executable
        // when the exported file is opened in Excel/Sheets.
        csv.col("=cmd|calc!A0").col(new BigDecimal("-500.00")).newRow();
        csv.col("+1-214-555-0142").col(new BigDecimal("1200.00")).newRow();
        String text = new String(csv.toBytes(), StandardCharsets.UTF_8);
        assertTrue(text.contains("'=cmd|calc!A0"), "expected the formula-trigger string to be apostrophe-guarded, got: " + text);
        assertTrue(text.contains("'+1-214-555-0142"), "expected the phone-like string to be apostrophe-guarded, got: " + text);
        // A real negative BigDecimal balance must remain a plain, unguarded numeric-looking token
        // -- the guard only applies to actual String values, not numbers that happen to start with "-".
        assertTrue(text.contains(",-500.00"));
        assertFalse(text.contains("'-500.00"));
    }

    @Test
    void csv_emptyDataset_onlyHeaderRowWritten() {
        CsvBuilder csv = CsvBuilder.create();
        csv.header("Name", "Amount");
        String text = new String(csv.toBytes(), StandardCharsets.UTF_8).replace("﻿", "");
        assertEquals("Name,Amount\r\n", text);
    }

    @Test
    void xlsx_emptyDataset_headerRowOnly_doesNotThrow() throws Exception {
        XlsxBuilder xlsx = XlsxBuilder.create("Empty");
        xlsx.header("Name", "Amount");
        byte[] bytes = xlsx.toBytes();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("Name", sheet.getRow(4).getCell(0).getStringCellValue());
            assertNull(sheet.getRow(5)); // no data rows at all
        }
    }

    @Test
    void xlsx_unicodeAndEmoji_roundTripCorrectly() throws Exception {
        XlsxBuilder xlsx = XlsxBuilder.create("Unicode");
        xlsx.header("Name");
        xlsx.col("José Ñoño 😀 Δοκιμή тест").newRow();
        byte[] bytes = xlsx.toBytes();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Cell cell = wb.getSheetAt(0).getRow(5).getCell(0);
            assertEquals("José Ñoño 😀 Δοκιμή тест", cell.getStringCellValue());
        }
    }

    @Test
    void xlsx_overlongCellValue_truncatedNotThrown() throws Exception {
        XlsxBuilder xlsx = XlsxBuilder.create("Overlong");
        xlsx.header("Notes");
        String huge = "x".repeat(40_000); // past Excel's 32,767-char hard limit
        assertDoesNotThrow(() -> xlsx.col(huge).newRow());
        byte[] bytes = xlsx.toBytes();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            String stored = wb.getSheetAt(0).getRow(5).getCell(0).getStringCellValue();
            assertTrue(stored.length() <= 32_767);
            assertTrue(stored.endsWith("...[truncated]"));
        }
    }

    @Test
    void xlsx_watermarkPictureEmbeddedWhenTableHasHeader() throws Exception {
        XlsxBuilder xlsx = XlsxBuilder.create("Watermarked");
        populate(xlsx);
        byte[] bytes = xlsx.toBytes();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertFalse(wb.getAllPictures().isEmpty(), "expected the logo watermark picture to be embedded");
        }
    }

    @Test
    void pdf_emptyDataset_headerRowOnly_doesNotThrow() throws Exception {
        PdfBuilder pdf = PdfBuilder.create("Empty Report");
        pdf.header("Name", "Amount");
        byte[] bytes = pdf.toBytes();
        PdfReader reader = new PdfReader(bytes);
        assertEquals(1, reader.getNumberOfPages());
        reader.close();
    }

    @Test
    void pdf_singlePage_footerShowsCorrectTotal() throws Exception {
        PdfBuilder pdf = PdfBuilder.create("Small Report");
        populate(pdf);
        byte[] bytes = pdf.toBytes();
        PdfReader reader = new PdfReader(bytes);
        assertEquals(1, reader.getNumberOfPages());
        String footer = new PdfTextExtractor(reader).getTextFromPage(1).replaceAll("\\s+", "");
        reader.close();
        // The "of" text and the page-count template are drawn as separate content-stream objects,
        // so the extractor doesn't reliably preserve the space between them -- squeeze whitespace
        // instead of asserting exact layout.
        assertTrue(footer.contains("Page1of1"), "expected 'Page 1 of 1', got: " + footer);
    }

    @Test
    void pdf_multiPage_footerShowsCorrectTotalOnLastPage() throws Exception {
        PdfBuilder pdf = PdfBuilder.create("Large Report");
        pdf.header("Name", "Amount");
        for (int i = 0; i < 80; i++) {
            pdf.col("Member " + i).col(i * 10).newRow();
        }
        byte[] bytes = pdf.toBytes();
        PdfReader reader = new PdfReader(bytes);
        int totalPages = reader.getNumberOfPages();
        assertTrue(totalPages > 1, "expected the 80-row table to spill across multiple pages");
        String lastPageFooter = new PdfTextExtractor(reader).getTextFromPage(totalPages).replaceAll("\\s+", "");
        reader.close();
        assertTrue(lastPageFooter.contains("Page" + totalPages + "of" + totalPages),
                "expected 'Page " + totalPages + " of " + totalPages + "', got: " + lastPageFooter);
    }

    @Test
    void pdf_nullAndSpecialCharacterCells_doNotThrow() {
        PdfBuilder pdf = PdfBuilder.create("Edge Cases");
        pdf.header("Name", "Notes");
        assertDoesNotThrow(() -> {
            pdf.col(null).col("commas, \"quotes\", and\nnewlines").newRow();
            pdf.col("José Ñoño").col("Δοκιμή тест 😀").newRow(); // outside Helvetica/CP1252 range
            pdf.toBytes();
        });
    }
}
