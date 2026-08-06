package com.mdau.ushirika.module.report.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;

/** Builds a single-sheet .xlsx workbook — bold header row, auto-sized columns,
 * numbers written as real numeric cells so totals/sorting work in Excel. */
public final class XlsxBuilder implements TableBuilder {

    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final Sheet sheet;
    private final CellStyle headerStyle;
    private int rowIndex = 0;
    private int colIndex = 0;
    private int maxCols = 0;
    private Row currentRow;
    private boolean headerRow = false;

    private XlsxBuilder(String sheetName) {
        this.sheet = workbook.createSheet(sheetName);
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        this.headerStyle = workbook.createCellStyle();
        this.headerStyle.setFont(boldFont);
    }

    public static XlsxBuilder create(String sheetName) {
        return new XlsxBuilder(sheetName);
    }

    @Override
    public XlsxBuilder header(String... cols) {
        headerRow = true;
        for (String c : cols) col(c);
        newRow();
        headerRow = false;
        return this;
    }

    @Override
    public XlsxBuilder col(Object value) {
        if (currentRow == null) currentRow = sheet.createRow(rowIndex);
        Cell cell = currentRow.createCell(colIndex);
        writeValue(cell, value);
        if (headerRow) cell.setCellStyle(headerStyle);
        colIndex++;
        maxCols = Math.max(maxCols, colIndex);
        return this;
    }

    @Override
    public XlsxBuilder newRow() {
        rowIndex++;
        colIndex = 0;
        currentRow = null;
        return this;
    }

    @Override
    public byte[] toBytes() {
        for (int i = 0; i < maxCols; i++) sheet.autoSizeColumn(i);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build XLSX report", e);
        }
    }

    private static void writeValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number n) {
            cell.setCellValue(n instanceof BigDecimal bd ? bd.doubleValue() : n.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b ? "Yes" : "No");
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
