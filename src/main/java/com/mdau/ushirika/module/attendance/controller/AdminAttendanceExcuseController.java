package com.mdau.ushirika.module.attendance.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.attendance.dto.AttendanceExcuseDto;
import com.mdau.ushirika.module.attendance.dto.ExcuseDecisionRequest;
import com.mdau.ushirika.module.attendance.service.AttendanceExcuseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/attendance/excuses")
@RequiredArgsConstructor
public class AdminAttendanceExcuseController {

    private final AttendanceExcuseService excuseService;

    @GetMapping
    public ApiResponse<PagedResponse<AttendanceExcuseDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<AttendanceExcuseDto> result = excuseService.listExcuses(status, pageable);
        return ApiResponse.ok(PagedResponse.of(result));
    }

    @PatchMapping("/{id}/approve")
    public ApiResponse<AttendanceExcuseDto> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ExcuseDecisionRequest body) {
        String notes = body != null ? body.adminNotes() : null;
        return ApiResponse.ok("Apology approved.", excuseService.decide(id, true, notes));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<AttendanceExcuseDto> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) ExcuseDecisionRequest body) {
        String notes = body != null ? body.adminNotes() : null;
        return ApiResponse.ok("Apology rejected.", excuseService.decide(id, false, notes));
    }
}
