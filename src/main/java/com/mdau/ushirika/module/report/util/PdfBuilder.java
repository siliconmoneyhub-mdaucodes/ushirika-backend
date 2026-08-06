package com.mdau.ushirika.module.report.util;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Builds a landscape A4 PDF table — small font since these reports can run wide
 * (e.g. one column per meeting on the attendance report). */
public final class PdfBuilder implements TableBuilder {

    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 8, Font.BOLD);
    private static final Font CELL_FONT   = new Font(Font.HELVETICA, 8, Font.NORMAL);

    private final String title;
    private final List<String> currentRowCells = new ArrayList<>();
    private PdfPTable table;
    private boolean headerRow = false;

    private PdfBuilder(String title) {
        this.title = title;
    }

    public static PdfBuilder create(String title) {
        return new PdfBuilder(title);
    }

    @Override
    public PdfBuilder header(String... cols) {
        table = new PdfPTable(cols.length);
        table.setWidthPercentage(100);
        headerRow = true;
        for (String c : cols) col(c);
        newRow();
        headerRow = false;
        return this;
    }

    @Override
    public PdfBuilder col(Object value) {
        currentRowCells.add(value == null ? "" : value.toString());
        return this;
    }

    @Override
    public PdfBuilder newRow() {
        Font font = headerRow ? HEADER_FONT : CELL_FONT;
        for (String cell : currentRowCells) {
            PdfPCell pdfCell = new PdfPCell(new Phrase(cell, font));
            pdfCell.setPadding(4);
            if (headerRow) pdfCell.setBackgroundColor(new Color(230, 230, 230));
            table.addCell(pdfCell);
        }
        currentRowCells.clear();
        return this;
    }

    @Override
    public byte[] toBytes() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 36, 24);
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph(title, new Font(Font.HELVETICA, 14, Font.BOLD)));
            document.add(new Paragraph(" "));
            if (table != null) document.add(table);
            document.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new RuntimeException("Failed to build PDF report", e);
        }
    }
}
