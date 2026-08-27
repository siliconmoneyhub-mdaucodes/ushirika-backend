package com.mdau.ushirika.module.calendar.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.calendar.dto.CalendarItemDto;
import com.mdau.ushirika.module.calendar.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class MemberCalendarController {

    private final CalendarService calendarService;

    /**
     * Returns all calendar items (meetings, events, MGR payouts, dues, fines)
     * for the authenticated member in the given date window.
     * Defaults to current calendar month if no params supplied.
     */
    @GetMapping("/portal/calendar")
    public ApiResponse<List<CalendarItemDto>> getCalendar(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate start = from != null ? from : AppClock.today().withDayOfMonth(1);
        LocalDate end   = to   != null ? to   : start.withDayOfMonth(start.lengthOfMonth());
        return ApiResponse.ok(calendarService.getMyCalendar(start, end));
    }
}
