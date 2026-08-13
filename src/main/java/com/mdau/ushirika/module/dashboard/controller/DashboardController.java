package com.mdau.ushirika.module.dashboard.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.dashboard.dto.ComplianceDashboardDto;
import com.mdau.ushirika.module.dashboard.dto.ContentDashboardDto;
import com.mdau.ushirika.module.dashboard.dto.DashboardSummaryDto;
import com.mdau.ushirika.module.dashboard.dto.DisciplineDashboardDto;
import com.mdau.ushirika.module.dashboard.dto.ElectionsDashboardDto;
import com.mdau.ushirika.module.dashboard.dto.FinanceDashboardDto;
import com.mdau.ushirika.module.dashboard.dto.MonthlySeriesDto;
import com.mdau.ushirika.module.dashboard.dto.NotificationsDashboardDto;
import com.mdau.ushirika.module.dashboard.dto.RecordsDashboardDto;
import com.mdau.ushirika.module.dashboard.dto.ScholarshipBreakdownDto;
import com.mdau.ushirika.module.dashboard.dto.WelfareBreakdownDto;
import com.mdau.ushirika.module.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Dashboard & Reports", description = "KPI summary and financial reports for the admin portal")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasAnyAuthority('ROLE_FINANCIAL_ADMIN','ROLE_ADMIN','ROLE_SUPERADMIN','CAP_FINANCE_DUES','CAP_FINANCE_ADVANCED')")
    @Operation(summary = "Full KPI snapshot — members, welfare, scholarships, finance, events, content, notifications")
    public ResponseEntity<ApiResponse<DashboardSummaryDto>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Dashboard loaded", dashboardService.getDashboard()));
    }

    @GetMapping("/admin/reports/financial")
    @PreAuthorize("hasAnyAuthority('ROLE_FINANCIAL_ADMIN','ROLE_ADMIN','ROLE_SUPERADMIN','CAP_FINANCE_DUES','CAP_FINANCE_ADVANCED')")
    @Operation(summary = "Monthly contributions and donations for the last N months (default 12)",
               description = "Returns two time-series arrays: contributions and donations. Suitable for bar/line charts.")
    public ResponseEntity<ApiResponse<MonthlySeriesDto>> financialReport(
            @RequestParam(defaultValue = "12") int months
    ) {
        if (months < 1 || months > 60) months = 12;
        return ResponseEntity.ok(ApiResponse.ok("Financial report generated",
                dashboardService.getFinancialSeries(months)));
    }

    @GetMapping("/admin/reports/finance")
    @PreAuthorize("hasAnyAuthority('ROLE_FINANCIAL_ADMIN','ROLE_ADMIN','ROLE_SUPERADMIN','CAP_FINANCE_DUES','CAP_FINANCE_ADVANCED')")
    @Operation(summary = "Cross-department finance health — dues collection rate, benevolence payout trend, loan portfolio, MGR totals",
               description = "year scopes the dues collection-rate figure (default current year); months scopes the benevolence/MGR trend charts (default 12).")
    public ResponseEntity<ApiResponse<FinanceDashboardDto>> financeDashboard(
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "12") int months
    ) {
        int resolvedYear = year != null ? year : java.time.LocalDate.now().getYear();
        if (months < 1 || months > 60) months = 12;
        return ResponseEntity.ok(ApiResponse.ok("Finance dashboard loaded",
                dashboardService.getFinanceDashboard(resolvedYear, months)));
    }

    @GetMapping("/admin/reports/welfare")
    @PreAuthorize("hasAnyAuthority('ROLE_FINANCIAL_ADMIN','ROLE_ADMIN','ROLE_SUPERADMIN','CAP_FINANCE_DUES','CAP_FINANCE_ADVANCED')")
    @Operation(summary = "Welfare requests broken down by category and status")
    public ResponseEntity<ApiResponse<WelfareBreakdownDto>> welfareReport() {
        return ResponseEntity.ok(ApiResponse.ok("Welfare report generated",
                dashboardService.getWelfareBreakdown()));
    }

    @GetMapping("/admin/reports/scholarships")
    @PreAuthorize("hasAnyAuthority('ROLE_FINANCIAL_ADMIN','ROLE_ADMIN','ROLE_SUPERADMIN','CAP_FINANCE_DUES','CAP_FINANCE_ADVANCED')")
    @Operation(summary = "Scholarship applications broken down by program and status")
    public ResponseEntity<ApiResponse<ScholarshipBreakdownDto>> scholarshipReport() {
        return ResponseEntity.ok(ApiResponse.ok("Scholarship report generated",
                dashboardService.getScholarshipBreakdown()));
    }

    @GetMapping("/admin/dashboard/records")
    @PreAuthorize("hasAnyAuthority('ROLE_SECRETARY','ROLE_ADMIN','ROLE_SUPERADMIN','CAP_APPLICATIONS','CAP_MEMBERS','CAP_MEETINGS_ATTENDANCE')")
    @Operation(summary = "Records-tier dashboard — membership counts and meeting schedule (Secretary role)")
    public ResponseEntity<ApiResponse<RecordsDashboardDto>> recordsDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Records dashboard loaded", dashboardService.getRecordsDashboard()));
    }

    @GetMapping("/admin/dashboard/discipline")
    @PreAuthorize("hasAnyAuthority('ROLE_CHIEF_WHIP','ROLE_ADMIN','ROLE_SUPERADMIN','CAP_DISCIPLINE')")
    @Operation(summary = "Discipline-tier dashboard — attendance, fines, and excuse requests (Chief Whip role)")
    public ResponseEntity<ApiResponse<DisciplineDashboardDto>> disciplineDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Discipline dashboard loaded", dashboardService.getDisciplineDashboard()));
    }

    @GetMapping("/admin/dashboard/compliance")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPLIANCE','ROLE_ADMIN','ROLE_SUPERADMIN','CAP_CONSTITUTION','CAP_REINSTATEMENT')")
    @Operation(summary = "Compliance-tier dashboard — governing documents, reinstatements, and audit trail (Compliance role)")
    public ResponseEntity<ApiResponse<ComplianceDashboardDto>> complianceDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Compliance dashboard loaded", dashboardService.getComplianceDashboard()));
    }

    @GetMapping("/admin/dashboard/notifications")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','CAP_NOTIFICATIONS')")
    @Operation(summary = "Notifications-capability dashboard — delivery activity over the last 7 days")
    public ResponseEntity<ApiResponse<NotificationsDashboardDto>> notificationsDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Notifications dashboard loaded", dashboardService.getNotificationsDashboard()));
    }

    @GetMapping("/admin/dashboard/content")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','CAP_CONTENT')")
    @Operation(summary = "Content-capability dashboard — articles, media, and events")
    public ResponseEntity<ApiResponse<ContentDashboardDto>> contentDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Content dashboard loaded", dashboardService.getContentDashboard()));
    }

    @GetMapping("/admin/dashboard/elections")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','CAP_ELECTIONS')")
    @Operation(summary = "Elections-capability dashboard — the current election, if any")
    public ResponseEntity<ApiResponse<ElectionsDashboardDto>> electionsDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Elections dashboard loaded", dashboardService.getElectionsDashboard()));
    }
}
