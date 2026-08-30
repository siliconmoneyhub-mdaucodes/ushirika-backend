package com.mdau.ushirika.module.report.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.report.dto.AttendanceComplianceReport;
import com.mdau.ushirika.module.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Report exports, split into per-domain authorization groups (Finance Visibility plan, Phase 5) --
 * these method-level checks are defense-in-depth behind SecurityConfig's matching per-path
 * requestMatchers, which are the layer that actually decides whether a request reaches this
 * controller at all. Both layers must be kept in sync when adding a new report.
 */
@RestController
@RequiredArgsConstructor
public class AdminReportController {

    private static final String MEMBERSHIP =
            "hasAnyAuthority('ROLE_SECRETARY','ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_LEADERSHIP','CAP_MEMBERS','CAP_APPLICATIONS')";
    private static final String DISCIPLINE =
            "hasAnyAuthority('ROLE_CHIEF_WHIP','ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_LEADERSHIP','CAP_DISCIPLINE','CAP_MEETINGS_ATTENDANCE')";
    private static final String ELECTIONS =
            "hasAnyAuthority('ROLE_COMPLIANCE','ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_LEADERSHIP','CAP_ELECTIONS')";
    private static final String FINANCE =
            "hasAnyAuthority('ROLE_FINANCIAL_ADMIN','ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_LEADERSHIP','CAP_FINANCE_DUES','CAP_FINANCE_ADVANCED')";
    private static final String SCHOLARSHIPS =
            "hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_LEADERSHIP','CAP_CONTENT','CAP_FINANCE_ADVANCED')";

    private final ReportService reportService;

    @GetMapping("/admin/reports/members.csv")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> membersCsv() {
        return csv(reportService.membersCsv(), "members_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/members.xlsx")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> membersXlsx() {
        return xlsx(reportService.membersXlsx(), "members_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/members.pdf")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> membersPdf() {
        return pdf(reportService.membersPdf(), "members_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/dues.csv")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> duesCsv(@RequestParam(required = false) Integer year) {
        return csv(reportService.duesCsv(year), duesFilename(year, "csv"));
    }

    @GetMapping("/admin/reports/dues.xlsx")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> duesXlsx(@RequestParam(required = false) Integer year) {
        return xlsx(reportService.duesXlsx(year), duesFilename(year, "xlsx"));
    }

    @GetMapping("/admin/reports/dues.pdf")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> duesPdf(@RequestParam(required = false) Integer year) {
        return pdf(reportService.duesPdf(year), duesFilename(year, "pdf"));
    }

    @GetMapping("/admin/reports/fines.csv")
    @PreAuthorize(DISCIPLINE)
    public ResponseEntity<byte[]> finesCsv() {
        return csv(reportService.finesCsv(), "fines_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/fines.xlsx")
    @PreAuthorize(DISCIPLINE)
    public ResponseEntity<byte[]> finesXlsx() {
        return xlsx(reportService.finesXlsx(), "fines_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/fines.pdf")
    @PreAuthorize(DISCIPLINE)
    public ResponseEntity<byte[]> finesPdf() {
        return pdf(reportService.finesPdf(), "fines_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/attendance.csv")
    @PreAuthorize(DISCIPLINE)
    public ResponseEntity<byte[]> attendanceCsv() {
        return csv(reportService.attendanceCsv(), "attendance_compliance_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/attendance.xlsx")
    @PreAuthorize(DISCIPLINE)
    public ResponseEntity<byte[]> attendanceXlsx() {
        return xlsx(reportService.attendanceXlsx(), "attendance_compliance_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/attendance.pdf")
    @PreAuthorize(DISCIPLINE)
    public ResponseEntity<byte[]> attendancePdf() {
        return pdf(reportService.attendancePdf(), "attendance_compliance_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/attendance")
    @PreAuthorize(DISCIPLINE)
    public ApiResponse<AttendanceComplianceReport> attendanceReport() {
        return ApiResponse.ok(reportService.attendanceReport());
    }

    @GetMapping("/admin/reports/mgr.csv")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> mgrCsv() {
        return csv(reportService.mgrCsv(), "mgr_contributions_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/mgr.xlsx")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> mgrXlsx() {
        return xlsx(reportService.mgrXlsx(), "mgr_contributions_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/mgr.pdf")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> mgrPdf() {
        return pdf(reportService.mgrPdf(), "mgr_contributions_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/finance.csv")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> financeSummaryCsv(
            @RequestParam(required = false) Integer year, @RequestParam(defaultValue = "12") int months) {
        return csv(reportService.financeSummaryCsv(year, months), "finance_summary_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/finance.xlsx")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> financeSummaryXlsx(
            @RequestParam(required = false) Integer year, @RequestParam(defaultValue = "12") int months) {
        return xlsx(reportService.financeSummaryXlsx(year, months), "finance_summary_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/finance.pdf")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> financeSummaryPdf(
            @RequestParam(required = false) Integer year, @RequestParam(defaultValue = "12") int months) {
        return pdf(reportService.financeSummaryPdf(year, months), "finance_summary_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/benevolence.csv")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> benevolenceClaimsCsv() {
        return csv(reportService.benevolenceClaimsCsv(), "benevolence_claims_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/benevolence.xlsx")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> benevolenceClaimsXlsx() {
        return xlsx(reportService.benevolenceClaimsXlsx(), "benevolence_claims_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/benevolence.pdf")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> benevolenceClaimsPdf() {
        return pdf(reportService.benevolenceClaimsPdf(), "benevolence_claims_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/loans.csv")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> loansCsv() {
        return csv(reportService.loansCsv(), "loans_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/loans.xlsx")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> loansXlsx() {
        return xlsx(reportService.loansXlsx(), "loans_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/loans.pdf")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> loansPdf() {
        return pdf(reportService.loansPdf(), "loans_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/scholarships.csv")
    @PreAuthorize(SCHOLARSHIPS)
    public ResponseEntity<byte[]> scholarshipsCsv() {
        return csv(reportService.scholarshipsCsv(), "scholarships_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/scholarships.xlsx")
    @PreAuthorize(SCHOLARSHIPS)
    public ResponseEntity<byte[]> scholarshipsXlsx() {
        return xlsx(reportService.scholarshipsXlsx(), "scholarships_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/scholarships.pdf")
    @PreAuthorize(SCHOLARSHIPS)
    public ResponseEntity<byte[]> scholarshipsPdf() {
        return pdf(reportService.scholarshipsPdf(), "scholarships_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/elections.csv")
    @PreAuthorize(ELECTIONS)
    public ResponseEntity<byte[]> electionsCsv() {
        return csv(reportService.electionsCsv(), "election_results_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/elections.xlsx")
    @PreAuthorize(ELECTIONS)
    public ResponseEntity<byte[]> electionsXlsx() {
        return xlsx(reportService.electionsXlsx(), "election_results_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/elections.pdf")
    @PreAuthorize(ELECTIONS)
    public ResponseEntity<byte[]> electionsPdf() {
        return pdf(reportService.electionsPdf(), "election_results_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/applications.csv")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> programApplicationsCsv() {
        return csv(reportService.programApplicationsCsv(), "program_applications_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/applications.xlsx")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> programApplicationsXlsx() {
        return xlsx(reportService.programApplicationsXlsx(), "program_applications_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/applications.pdf")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> programApplicationsPdf() {
        return pdf(reportService.programApplicationsPdf(), "program_applications_" + LocalDate.now() + ".pdf");
    }

    /** Deliberately a different endpoint from applications.* above -- that one is Program
     * Applications (custom programs only, see ReportService), a completely different domain from
     * the membership onboarding pipeline (enquiry -> form sent -> onboarding -> approved) here. */
    @GetMapping("/admin/reports/membership-applications.csv")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> membershipApplicationsCsv() {
        return csv(reportService.membershipApplicationsCsv(), "membership_applications_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/membership-applications.xlsx")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> membershipApplicationsXlsx() {
        return xlsx(reportService.membershipApplicationsXlsx(), "membership_applications_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/membership-applications.pdf")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> membershipApplicationsPdf() {
        return pdf(reportService.membershipApplicationsPdf(), "membership_applications_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/money-flow.csv")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> moneyFlowCsv() {
        return csv(reportService.moneyFlowCsv(), "money_flow_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/money-flow.xlsx")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> moneyFlowXlsx() {
        return xlsx(reportService.moneyFlowXlsx(), "money_flow_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/money-flow.pdf")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> moneyFlowPdf() {
        return pdf(reportService.moneyFlowPdf(), "money_flow_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/balances.csv")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> balancesCsv() {
        return csv(reportService.balancesCsv(), "balances_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/balances.xlsx")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> balancesXlsx() {
        return xlsx(reportService.balancesXlsx(), "balances_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/balances.pdf")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> balancesPdf() {
        return pdf(reportService.balancesPdf(), "balances_" + LocalDate.now() + ".pdf");
    }

    /** Distinct from balances.* above -- that one is computed net balances per program; this is
     * the actual physical-vs-expected bank reconciliation check history. */
    @GetMapping("/admin/reports/reconciliation.csv")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> reconciliationCsv() {
        return csv(reportService.reconciliationCsv(), "bank_reconciliation_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/reconciliation.xlsx")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> reconciliationXlsx() {
        return xlsx(reportService.reconciliationXlsx(), "bank_reconciliation_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/reconciliation.pdf")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> reconciliationPdf() {
        return pdf(reportService.reconciliationPdf(), "bank_reconciliation_" + LocalDate.now() + ".pdf");
    }

    @GetMapping("/admin/reports/officials.csv")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> officialsCsv() {
        return csv(reportService.officialsCsv(), "officials_" + LocalDate.now() + ".csv");
    }

    @GetMapping("/admin/reports/officials.xlsx")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> officialsXlsx() {
        return xlsx(reportService.officialsXlsx(), "officials_" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/admin/reports/officials.pdf")
    @PreAuthorize(MEMBERSHIP)
    public ResponseEntity<byte[]> officialsPdf() {
        return pdf(reportService.officialsPdf(), "officials_" + LocalDate.now() + ".pdf");
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
