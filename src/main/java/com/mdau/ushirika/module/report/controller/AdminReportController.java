package com.mdau.ushirika.module.report.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.report.dto.AttendanceComplianceReport;
import com.mdau.ushirika.module.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping("/admin/reports/members.csv")
    public ResponseEntity<byte[]> membersCsv() {
        return csv(reportService.membersCsv(), "members_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/members.xlsx")
    public ResponseEntity<byte[]> membersXlsx() {
        return xlsx(reportService.membersXlsx(), "members_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/members.pdf")
    public ResponseEntity<byte[]> membersPdf() {
        return pdf(reportService.membersPdf(), "members_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/dues.csv")
    public ResponseEntity<byte[]> duesCsv(@RequestParam(required = false) Integer year) {
        return csv(reportService.duesCsv(year), duesFilename(year, "csv"));
    }

    @GetMapping("/admin/reports/dues.xlsx")
    public ResponseEntity<byte[]> duesXlsx(@RequestParam(required = false) Integer year) {
        return xlsx(reportService.duesXlsx(year), duesFilename(year, "xlsx"));
    }

    @GetMapping("/admin/reports/dues.pdf")
    public ResponseEntity<byte[]> duesPdf(@RequestParam(required = false) Integer year) {
        return pdf(reportService.duesPdf(year), duesFilename(year, "pdf"));
    }

    @GetMapping("/admin/reports/fines.csv")
    public ResponseEntity<byte[]> finesCsv() {
        return csv(reportService.finesCsv(), "fines_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/fines.xlsx")
    public ResponseEntity<byte[]> finesXlsx() {
        return xlsx(reportService.finesXlsx(), "fines_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/fines.pdf")
    public ResponseEntity<byte[]> finesPdf() {
        return pdf(reportService.finesPdf(), "fines_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/attendance.csv")
    public ResponseEntity<byte[]> attendanceCsv() {
        return csv(reportService.attendanceCsv(), "attendance_compliance_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/attendance.xlsx")
    public ResponseEntity<byte[]> attendanceXlsx() {
        return xlsx(reportService.attendanceXlsx(), "attendance_compliance_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/attendance.pdf")
    public ResponseEntity<byte[]> attendancePdf() {
        return pdf(reportService.attendancePdf(), "attendance_compliance_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/attendance")
    public ApiResponse<AttendanceComplianceReport> attendanceReport() {
        return ApiResponse.ok(reportService.attendanceReport());
    }

    @GetMapping("/admin/reports/mgr.csv")
    public ResponseEntity<byte[]> mgrCsv() {
        return csv(reportService.mgrCsv(), "mgr_contributions_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/mgr.xlsx")
    public ResponseEntity<byte[]> mgrXlsx() {
        return xlsx(reportService.mgrXlsx(), "mgr_contributions_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/mgr.pdf")
    public ResponseEntity<byte[]> mgrPdf() {
        return pdf(reportService.mgrPdf(), "mgr_contributions_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/finance.csv")
    public ResponseEntity<byte[]> financeSummaryCsv(
            @RequestParam(required = false) Integer year, @RequestParam(defaultValue = "12") int months) {
        return csv(reportService.financeSummaryCsv(year, months), "finance_summary_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/finance.xlsx")
    public ResponseEntity<byte[]> financeSummaryXlsx(
            @RequestParam(required = false) Integer year, @RequestParam(defaultValue = "12") int months) {
        return xlsx(reportService.financeSummaryXlsx(year, months), "finance_summary_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/finance.pdf")
    public ResponseEntity<byte[]> financeSummaryPdf(
            @RequestParam(required = false) Integer year, @RequestParam(defaultValue = "12") int months) {
        return pdf(reportService.financeSummaryPdf(year, months), "finance_summary_" + LocalDate.now() + ".pdf");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String duesFilename(Integer year, String ext) {
        return year != null ? "dues_" + year + "." + ext : "dues_all_" + LocalDate.now() + "." + ext;
    }

    private static ResponseEntity<byte[]> csv(byte[] data, String filename) {
        return download(data, filename, "text/csv; charset=UTF-8");
    }

    private static ResponseEntity<byte[]> xlsx(byte[] data, String filename) {
        return download(data, filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    private static ResponseEntity<byte[]> pdf(byte[] data, String filename) {
        return download(data, filename, "application/pdf");
    }

    private static ResponseEntity<byte[]> download(byte[] data, String filename, String contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(data.length)
                .body(data);
    }
}
