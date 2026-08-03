package com.mdau.ushirika.module.attendance.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.attendance.dto.*;
import com.mdau.ushirika.module.attendance.service.AttendanceExcuseService;
import com.mdau.ushirika.module.attendance.service.FinePaymentService;
import com.mdau.ushirika.module.attendance.service.FineService;
import com.mdau.ushirika.module.attendance.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MemberAttendanceController {

    private final MeetingService meetingService;
    private final FineService fineService;
    private final FinePaymentService finePaymentService;
    private final AttendanceExcuseService excuseService;

    @GetMapping("/attendance/my")
    public ApiResponse<AttendanceSummaryDto> myAttendance() {
        return ApiResponse.ok(meetingService.getMyAttendanceSummary());
    }

    @PostMapping("/meetings/{id}/checkin")
    public ApiResponse<CheckInResultDto> checkIn(@PathVariable UUID id, @Valid @RequestBody MemberCheckInRequest req) {
        return ApiResponse.ok("Checked in.", meetingService.checkIn(id, req));
    }

    @PostMapping("/meetings/{id}/excuse")
    public ApiResponse<AttendanceExcuseDto> submitExcuse(@PathVariable UUID id, @Valid @RequestBody SubmitExcuseRequest req) {
        return ApiResponse.ok("Apology submitted.", excuseService.submitExcuse(id, req));
    }

    @GetMapping("/attendance/excuses/my")
    public ApiResponse<List<AttendanceExcuseDto>> myExcuses() {
        return ApiResponse.ok(excuseService.myExcuses());
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
