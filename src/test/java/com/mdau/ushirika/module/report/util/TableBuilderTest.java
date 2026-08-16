package com.mdau.ushirika.module.report.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        try (var wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
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
}
