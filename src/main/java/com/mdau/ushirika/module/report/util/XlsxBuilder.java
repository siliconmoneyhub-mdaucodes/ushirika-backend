package com.mdau.ushirika.module.report.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private static final String LOGO_RESOURCE = "/report-assets/ushirika-logo.png";
    // POI has no per-shape opacity control for XSSF pictures, so the fade is baked directly into
    // the PNG's own alpha channel instead -- Excel renders real PNG transparency correctly, which
    // gets the same letterhead effect as the PDF's low-opacity watermark. Higher than the PDF's
    // 6% since Excel's gridlines and denser UI wash out very faint watermarks more than a PDF page.
    private static final float WATERMARK_OPACITY = 0.10f;
    private static final int WATERMARK_TARGET_PX = 260;
    // Excel's own default cell metrics, used only to roughly center the watermark over the table.
    private static final int DEFAULT_COL_WIDTH_PX = 64;
    private static final int DEFAULT_ROW_HEIGHT_PX = 20;

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
            addWatermark();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build XLSX report", e);
        }
    }

    /** Drops a faint, roughly-centered logo over the data area — the same letterhead treatment
     * (same logo, low opacity) used for the PDF reports and the Constitution/Bylaws viewer on the
     * onboarding site. Best-effort: a missing/corrupt logo resource just skips the watermark
     * rather than failing the whole report, same policy as PdfBuilder's loadLogo(). */
    private void addWatermark() {
        byte[] png = buildWatermarkPng();
        if (png == null) return;
        try {
            int pictureIdx = workbook.addPicture(png, Workbook.PICTURE_TYPE_PNG);
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();

            int colSpan = Math.max(1, WATERMARK_TARGET_PX / DEFAULT_COL_WIDTH_PX);
            int rowSpan = Math.max(1, WATERMARK_TARGET_PX / DEFAULT_ROW_HEIGHT_PX);
            int centerCol = Math.max(0, (maxCols - colSpan) / 2);
            int centerRow = Math.max(headerRowIndex + 1, headerRowIndex + 1 + (dataRowCount - rowSpan) / 2);
            anchor.setCol1(centerCol);
            anchor.setRow1(centerRow);

            Picture picture = drawing.createPicture(anchor, pictureIdx);
            picture.resize(1.0);
        } catch (Exception e) {
            // A watermark placement failure shouldn't take down the whole report export.
        }
    }

    private static byte[] buildWatermarkPng() {
        try (InputStream in = XlsxBuilder.class.getResourceAsStream(LOGO_RESOURCE)) {
            if (in == null) return null;
            BufferedImage src = ImageIO.read(in);
            if (src == null) return null;

            double scale = Math.min(
                    (double) WATERMARK_TARGET_PX / src.getWidth(),
                    (double) WATERMARK_TARGET_PX / src.getHeight());
            int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(src.getHeight() * scale));

            BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();

            BufferedImage faded = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = scaled.getRGB(x, y);
                    int alpha = argb >>> 24;
                    int fadedAlpha = (int) Math.round(alpha * WATERMARK_OPACITY);
                    faded.setRGB(x, y, (fadedAlpha << 24) | (argb & 0x00FFFFFF));
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(faded, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null; // Missing/corrupt logo shouldn't fail report generation.
        }
    }

    // Excel's own hard limit -- a value past this throws IllegalArgumentException deep inside POI
    // and would fail the *entire* export over a single oversized cell. None of this app's real
    // free-text fields (fine reasons, claim notes, etc.) come close, but truncating defensively
    // here means one unexpectedly long value degrades gracefully instead of a vague 500.
    private static final int EXCEL_MAX_CELL_TEXT = 32_767;

    private static void writeValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number n) {
            cell.setCellValue(n instanceof BigDecimal bd ? bd.doubleValue() : n.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b ? "Yes" : "No");
        } else {
            String text = value.toString();
            if (text.length() > EXCEL_MAX_CELL_TEXT) {
                text = text.substring(0, EXCEL_MAX_CELL_TEXT - 15) + "...[truncated]";
            }
            cell.setCellValue(text);
        }
    }
}
