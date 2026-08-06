package com.mdau.ushirika.module.report.util;

/** Common shape for CSV/XLSX/PDF report builders — lets ReportService populate a
 * table once and export it in any of the three formats. */
public interface TableBuilder {

    /** Write the header row and move to the next row. */
    TableBuilder header(String... cols);

    /** Append a single cell value (null becomes empty). */
    TableBuilder col(Object value);

    /** End the current row. */
    TableBuilder newRow();

    byte[] toBytes();
}
