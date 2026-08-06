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
    void xlsx_producesValidZipPackage() {
        XlsxBuilder xlsx = XlsxBuilder.create("Test");
        populate(xlsx);
        byte[] bytes = xlsx.toBytes();
        assertTrue(bytes.length > 100);
        // XLSX is a ZIP archive — "PK" magic bytes
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
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
