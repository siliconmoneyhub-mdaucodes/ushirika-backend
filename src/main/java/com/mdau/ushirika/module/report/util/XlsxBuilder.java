package com.mdau.ushirika.module.report.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Builds a single-sheet .xlsx workbook — brand-green header row (white bold text, real
 * borders), a title block above the table (org name / report name / generated date), the
 * header row frozen so it stays visible on scroll, and an auto-filter dropdown on every
 * column. Numbers are still written as real numeric cells so totals/sorting work in Excel. */
public final class XlsxBuilder implements TableBuilder {

    private static final String ORG_NAME = "Ushirika Welfare Organization";
    private static final byte[] BRAND_GREEN_RGB = {(byte) 0x1F, (byte) 0x4E, (byte) 0x3D};
    private static final byte[] ROW_ALT_RGB = {(byte) 0xF2, (byte) 0xF7, (byte) 0xF4};
    private static final byte[] WHITE_RGB = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] BORDER_GRAY_RGB = {(byte) 0xDD, (byte) 0xDD, (byte) 0xDD};

    /** Rows above the data table: org name, report title, generated date, one blank spacer. */
    private static final int TITLE_ROWS = 4;

    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final Sheet sheet;
    private final CellStyle headerStyle;
    private final CellStyle dataStyle;
    private final CellStyle altRowStyle;
    private int rowIndex = TITLE_ROWS;
    private int colIndex = 0;
    private int maxCols = 0;
    private int headerRowIndex = -1;
    private int dataRowCount = 0;
    private Row currentRow;
    private boolean headerRow = false;

    private XlsxBuilder(String sheetName, String reportTitle) {
        this.sheet = workbook.createSheet(sanitizeSheetName(sheetName));

        CellStyle grayStyle = workbook.createCellStyle();
        XSSFFont grayFont = (XSSFFont) workbook.createFont();
        grayFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        grayStyle.setFont(grayFont);

        Row orgRow = sheet.createRow(0);
        Cell orgCell = orgRow.createCell(0);
        orgCell.setCellValue(ORG_NAME);
        orgCell.setCellStyle(grayStyle);

        CellStyle titleStyle = workbook.createCellStyle();
        XSSFFont titleFont = (XSSFFont) workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setColor(new XSSFColor(BRAND_GREEN_RGB, null));
        titleStyle.setFont(titleFont);

        Row titleRow = sheet.createRow(1);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(reportTitle);
        titleCell.setCellStyle(titleStyle);

        Row genRow = sheet.createRow(2);
        Cell genCell = genRow.createCell(0);
        genCell.setCellValue("Generated " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        genCell.setCellStyle(grayStyle);

        XSSFFont headerFont = (XSSFFont) workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(new XSSFColor(WHITE_RGB, null));
        this.headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        ((XSSFCellStyle) headerStyle).setFillForegroundColor(new XSSFColor(BRAND_GREEN_RGB, null));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(headerStyle);

        this.dataStyle = workbook.createCellStyle();
        setBorders(dataStyle);

        this.altRowStyle = workbook.createCellStyle();
        altRowStyle.cloneStyleFrom(dataStyle);
        ((XSSFCellStyle) altRowStyle).setFillForegroundColor(new XSSFColor(ROW_ALT_RGB, null));
        altRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    public static XlsxBuilder create(String sheetName) {
        return new XlsxBuilder(sheetName, sheetName);
    }

    private void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        XSSFColor borderColor = new XSSFColor(BORDER_GRAY_RGB, null);
        ((XSSFCellStyle) style).setTopBorderColor(borderColor);
        ((XSSFCellStyle) style).setBottomBorderColor(borderColor);
        ((XSSFCellStyle) style).setLeftBorderColor(borderColor);
        ((XSSFCellStyle) style).setRightBorderColor(borderColor);
    }

    private static String sanitizeSheetName(String name) {
        String cleaned = name.replaceAll("[\\\\/*\\[\\]:?]", " ").trim();
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : (cleaned.isEmpty() ? "Report" : cleaned);
    }

    @Override
    public XlsxBuilder header(String... cols) {
        headerRow = true;
        headerRowIndex = rowIndex;
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
        cell.setCellStyle(headerRow ? headerStyle : (dataRowCount % 2 == 1 ? altRowStyle : dataStyle));
        colIndex++;
        maxCols = Math.max(maxCols, colIndex);
        return this;
    }

    @Override
    public XlsxBuilder newRow() {
        rowIndex++;
        colIndex = 0;
        currentRow = null;
        if (!headerRow) dataRowCount++;
        return this;
    }

    @Override
    public byte[] toBytes() {
        for (int i = 0; i < maxCols; i++) sheet.autoSizeColumn(i);

        if (headerRowIndex >= 0 && maxCols > 0) {
            // Freeze everything above and including the header row so it stays visible on scroll.
            sheet.createFreezePane(0, headerRowIndex + 1);
            sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, headerRowIndex, 0, maxCols - 1));
        }

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
