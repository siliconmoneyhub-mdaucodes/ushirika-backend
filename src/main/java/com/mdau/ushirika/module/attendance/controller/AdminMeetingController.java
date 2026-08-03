package com.mdau.ushirika.module.attendance.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.attendance.dto.*;
import com.mdau.ushirika.module.attendance.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/meetings")
@RequiredArgsConstructor
public class AdminMeetingController {

    private final MeetingService meetingService;

    @GetMapping
    public ApiResponse<Page<MeetingDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(meetingService.listMeetings(PageRequest.of(page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MeetingDto>> create(@Valid @RequestBody CreateMeetingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Meeting scheduled.", meetingService.createMeeting(req)));
    }

    @GetMapping("/{id}")
    public ApiResponse<MeetingDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(meetingService.getMeeting(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<MeetingDto> update(@PathVariable UUID id,
                                           @RequestBody UpdateMeetingRequest req) {
        return ApiResponse.ok(meetingService.updateMeeting(id, req));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<MeetingDto> cancel(@PathVariable UUID id) {
        return ApiResponse.ok("Meeting cancelled.", meetingService.cancelMeeting(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<MeetingDto> complete(@PathVariable UUID id) {
        return ApiResponse.ok("Meeting completed. Unrecorded members marked absent.",
                meetingService.completeMeeting(id));
    }

    @GetMapping("/{id}/attendance")
    public ApiResponse<MeetingWithAttendanceDto> getAttendance(@PathVariable UUID id) {
        return ApiResponse.ok(meetingService.getMeetingWithAttendance(id));
    }

    @PostMapping("/{id}/attendance/bulk")
    public ApiResponse<List<AttendanceRecordDto>> recordBulk(
            @PathVariable UUID id,
            @Valid @RequestBody BulkAttendanceRequest req) {
        return ApiResponse.ok("Attendance recorded.", meetingService.recordBulkAttendance(id, req));
    }

    @GetMapping("/{id}/checkin-code")
    public ApiResponse<CheckInCodeDto> checkInCode(@PathVariable UUID id) {
        return ApiResponse.ok(meetingService.currentCheckInCode(id));
    }
}
