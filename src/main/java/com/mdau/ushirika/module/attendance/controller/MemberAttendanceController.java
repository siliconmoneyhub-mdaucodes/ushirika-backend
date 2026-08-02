package com.mdau.ushirika.module.attendance.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.attendance.dto.*;
import com.mdau.ushirika.module.attendance.service.FinePaymentService;
import com.mdau.ushirika.module.attendance.service.FineService;
import com.mdau.ushirika.module.attendance.service.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MemberAttendanceController {

    private final MeetingService meetingService;
    private final FineService fineService;
    private final FinePaymentService finePaymentService;

    @GetMapping("/attendance/my")
    public ApiResponse<AttendanceSummaryDto> myAttendance() {
        return ApiResponse.ok(meetingService.getMyAttendanceSummary());
    }

    @GetMapping("/fines/my")
    public ApiResponse<List<FineDto>> myFines() {
        return ApiResponse.ok(fineService.getMyFines());
    }

    // ── Fine payment history (read-only) ───────────────────────────────────────

    @GetMapping("/fines/my/payments")
    public ApiResponse<List<FinePaymentDto>> myFinePayments() {
        return ApiResponse.ok(finePaymentService.myPayments());
    }
}
