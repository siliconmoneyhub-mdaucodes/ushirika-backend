package com.mdau.ushirika.module.event.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.event.dto.*;
import com.mdau.ushirika.module.event.enums.EventStatus;
import com.mdau.ushirika.module.event.enums.RegistrationStatus;
import com.mdau.ushirika.module.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/events")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','CAP_CONTENT')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Events — Admin", description = "Manage events and attendance")
public class AdminEventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "List all events (all statuses, paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<EventDto>>> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Events retrieved",
                eventService.listAllEvents(
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @PostMapping
    @Operation(summary = "Create a new event (starts as DRAFT)")
    public ResponseEntity<ApiResponse<EventDto>> create(@Valid @RequestBody EventRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Event created", eventService.createEvent(req)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update event details")
    public ResponseEntity<ApiResponse<EventDto>> update(
            @PathVariable UUID id, @Valid @RequestBody EventRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Event updated", eventService.updateEvent(id, req)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Transition event status (DRAFT → PUBLISHED → ONGOING → COMPLETED | CANCELLED)")
    public ResponseEntity<ApiResponse<EventDto>> updateStatus(
            @PathVariable UUID id, @RequestParam EventStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", eventService.updateStatus(id, status)));
    }

    @GetMapping("/{id}/registrations")
    @Operation(summary = "List registrations for an event, optionally filtered by status")
    public ResponseEntity<ApiResponse<PagedResponse<RegistrationDto>>> registrations(
            @PathVariable UUID id,
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Registrations retrieved",
                eventService.listRegistrations(id, status,
                        PageRequest.of(page, size, Sort.by("registeredAt").ascending()))));
    }

    @PatchMapping("/{eventId}/registrations/{registrationId}/attendance")
    @Operation(summary = "Mark a registration as ATTENDED or NO_SHOW. " +
               "Pass referenceCode in the body to look up by check-in QR code instead of ID.")
    public ResponseEntity<ApiResponse<RegistrationDto>> markAttendance(
            @PathVariable UUID eventId,
            @PathVariable UUID registrationId,
            @Valid @RequestBody AttendanceRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Attendance recorded",
                eventService.markAttendance(eventId, registrationId, req)));
    }
}
