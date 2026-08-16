package com.mdau.ushirika.module.report.util;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a landscape A4 PDF table with the org's actual branding — logo, name, and a
 * generated-on date on page 1, a brand-green header row, light zebra striping for
 * readability on wide tables, and a "Page X of Y" footer on every page. Font stays small
 * (8pt data / 7.5pt header) since these reports can run wide (e.g. one column per meeting
 * on the attendance report) — this is a legibility constraint, not an oversight.
 */
public final class PdfBuilder implements TableBuilder {

    private static final String LOGO_RESOURCE = "/report-assets/ushirika-logo.png";
    private static final String ORG_NAME = "Ushirika Welfare Organization";

    private static final Color BRAND_GREEN = new Color(0x1F, 0x4E, 0x3D);
    private static final Color ROW_ALT = new Color(0xF2, 0xF7, 0xF4);
    private static final Color GRAY = new Color(0x6B, 0x72, 0x80);
    private static final Color BORDER_GRAY = new Color(0xDD, 0xDD, 0xDD);

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND_GREEN);
    private static final Font ORG_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, GRAY);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 7.5f, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, GRAY);

    private final String title;
    private final List<String> currentRowCells = new ArrayList<>();
    private PdfPTable table;
    private boolean headerRow = false;
    private int dataRowIndex = 0;

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
        Color bg = headerRow ? BRAND_GREEN : (dataRowIndex % 2 == 1 ? ROW_ALT : Color.WHITE);
        for (String cell : currentRowCells) {
            PdfPCell pdfCell = new PdfPCell(new Phrase(cell, font));
            pdfCell.setPadding(4);
            pdfCell.setBackgroundColor(bg);
            pdfCell.setBorderColor(BORDER_GRAY);
            pdfCell.setBorderWidth(0.5f);
            table.addCell(pdfCell);
        }
        currentRowCells.clear();
        if (!headerRow) dataRowIndex++;
        return this;
    }

    @Override
    public byte[] toBytes() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 90, 40);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new FooterEvent());
            document.open();

            Image logo = loadLogo();
            if (logo != null) {
                logo.scaleToFit(50, 50);
                logo.setAlignment(Image.ALIGN_LEFT);
                document.add(logo);
            }
            document.add(new Paragraph(ORG_NAME, ORG_FONT));
            document.add(new Paragraph(title, TITLE_FONT));
            document.add(new Paragraph(
                    "Generated " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                    ORG_FONT));
            document.add(new Paragraph(" "));
            if (table != null) document.add(table);
            document.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new RuntimeException("Failed to build PDF report", e);
        }
    }

    private static Image loadLogo() {
        try (InputStream in = PdfBuilder.class.getResourceAsStream(LOGO_RESOURCE)) {
            if (in == null) return null;
            return Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            return null; // Missing/corrupt logo shouldn't fail report generation.
        }
    }

    /** Draws "Page X of Y" + org name at the bottom of every page, and a faint centered logo
     * watermark behind the content on every page — the same letterhead treatment (same logo,
     * ~6% opacity, centered) used for the Constitution/Bylaws viewer on the onboarding site.
     * The "of Y" part needs a template placeholder since the total page count isn't known
     * until the document closes. */
    private static final class FooterEvent extends PdfPageEventHelper {
        private static final float WATERMARK_OPACITY = 0.06f;
        private static final float WATERMARK_SIZE = 260f;

        private PdfTemplate totalPagesTemplate;
        private final Image watermark = loadLogo();
        // writer.getPageNumber() reads one too high if called from onCloseDocument -- iText/OpenPDF
        // pre-opens a "phantom" next page internally after the real last page ends, and that
        // phantom page's number leaks into getPageNumber() even though the page itself is
        // discarded and never emitted (onStartPage fires for it, but onEndPage never does).
        // Tracking the number in onEndPage instead, which only fires for pages actually emitted,
        // avoids the off-by-one.
        private int lastPageNumber = 1;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPagesTemplate = writer.getDirectContent().createTemplate(50, 20);
        }

        @Override
        public void onStartPage(PdfWriter writer, Document document) {
            if (watermark == null) return;
            watermark.scaleToFit(WATERMARK_SIZE, WATERMARK_SIZE);
            float w = watermark.getScaledWidth();
            float h = watermark.getScaledHeight();
            float x = (document.getPageSize().getWidth() - w) / 2;
            float y = (document.getPageSize().getHeight() - h) / 2;

            PdfContentByte cb = writer.getDirectContentUnder();
            cb.saveState();
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(WATERMARK_OPACITY);
            gs.setStrokeOpacity(WATERMARK_OPACITY);
            cb.setGState(gs);
            cb.addImage(watermark, w, 0, 0, h, x, y);
            cb.restoreState();
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 20;
            cb.saveState();
            cb.setColorStroke(BORDER_GRAY);
            cb.setLineWidth(0.5f);
            cb.moveTo(document.left(), y + 14);
            cb.lineTo(document.right(), y + 14);
            cb.stroke();
            cb.restoreState();

            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(ORG_NAME, FOOTER_FONT),
                    document.left(), y, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber() + " of", FOOTER_FONT),
                    document.right() - 55, y, 0);
            cb.addTemplate(totalPagesTemplate, document.right() - 50, y);
            lastPageNumber = writer.getPageNumber();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            try {
                BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                totalPagesTemplate.beginText();
                totalPagesTemplate.setFontAndSize(bf, FOOTER_FONT.getSize());
                totalPagesTemplate.setTextMatrix(0, 0);
                totalPagesTemplate.showText(String.valueOf(lastPageNumber));
                totalPagesTemplate.endText();
            } catch (java.io.IOException | DocumentException e) {
                // Total page count is a nice-to-have footer detail -- don't fail the whole
                // report just because it couldn't be drawn.
            }
        }
    }
}
