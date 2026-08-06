package com.mdau.ushirika.module.dashboard.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.dashboard.dto.ComplianceDashboardDto;
import com.mdau.ushirika.module.dashboard.dto.DashboardSummaryDto;
import com.mdau.ushirika.module.dashboard.dto.DisciplineDashboardDto;
import com.mdau.ushirika.module.dashboard.dto.MonthlySeriesDto;
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
    @Operation(summary = "Full KPI snapshot — members, welfare, scholarships, finance, events, content, notifications")
    public ResponseEntity<ApiResponse<DashboardSummaryDto>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Dashboard loaded", dashboardService.getDashboard()));
    }

    @GetMapping("/admin/reports/financial")
    @Operation(summary = "Monthly contributions and donations for the last N months (default 12)",
               description = "Returns two time-series arrays: contributions and donations. Suitable for bar/line charts.")
    public ResponseEntity<ApiResponse<MonthlySeriesDto>> financialReport(
            @RequestParam(defaultValue = "12") int months
    ) {
        if (months < 1 || months > 60) months = 12;
        return ResponseEntity.ok(ApiResponse.ok("Financial report generated",
                dashboardService.getFinancialSeries(months)));
    }

    @GetMapping("/admin/reports/welfare")
    @Operation(summary = "Welfare requests broken down by category and status")
    public ResponseEntity<ApiResponse<WelfareBreakdownDto>> welfareReport() {
        return ResponseEntity.ok(ApiResponse.ok("Welfare report generated",
                dashboardService.getWelfareBreakdown()));
    }

    @GetMapping("/admin/reports/scholarships")
    @Operation(summary = "Scholarship applications broken down by program and status")
    public ResponseEntity<ApiResponse<ScholarshipBreakdownDto>> scholarshipReport() {
        return ResponseEntity.ok(ApiResponse.ok("Scholarship report generated",
                dashboardService.getScholarshipBreakdown()));
    }

    @GetMapping("/admin/dashboard/records")
    @PreAuthorize("hasAnyRole('SECRETARY','ADMIN','SUPERADMIN')")
    @Operation(summary = "Records-tier dashboard — membership counts and meeting schedule (Secretary role)")
    public ResponseEntity<ApiResponse<RecordsDashboardDto>> recordsDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Records dashboard loaded", dashboardService.getRecordsDashboard()));
    }

    @GetMapping("/admin/dashboard/discipline")
    @PreAuthorize("hasAnyRole('CHIEF_WHIP','ADMIN','SUPERADMIN')")
    @Operation(summary = "Discipline-tier dashboard — attendance, fines, and excuse requests (Chief Whip role)")
    public ResponseEntity<ApiResponse<DisciplineDashboardDto>> disciplineDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Discipline dashboard loaded", dashboardService.getDisciplineDashboard()));
    }

    @GetMapping("/admin/dashboard/compliance")
    @PreAuthorize("hasAnyRole('COMPLIANCE','ADMIN','SUPERADMIN')")
    @Operation(summary = "Compliance-tier dashboard — governing documents, reinstatements, and audit trail (Compliance role)")
    public ResponseEntity<ApiResponse<ComplianceDashboardDto>> complianceDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Compliance dashboard loaded", dashboardService.getComplianceDashboard()));
    }
}
