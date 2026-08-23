package com.mdau.ushirika.module.admin.controller;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.admin.service.TestDataCleanupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time tool for clearing the pre-launch test/QA data reviewed and confirmed with the org's
 * superadmin -- see TestDataCleanupService for exactly what's matched and why. /superadmin/**
 * is already SUPERADMIN-only via SecurityConfig; the extra @PreAuthorize here plus the required
 * confirm phrase on execute are deliberate belt-and-suspenders for an action this irreversible.
 */
@RestController
@RequestMapping("/superadmin/test-data-cleanup")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Superadmin — Test Data Cleanup", description = "One-time pre-launch test data removal")
public class TestDataCleanupController {

    private static final String CONFIRM_PHRASE = "DELETE_TEST_DATA";

    private final TestDataCleanupService service;

    public record ExecuteRequest(String confirm) {}
    public record AdhocRequest(List<String> emails, String confirm) {}
    public record CleanupResult(Map<String, Long> byTable, long total) {}

    @GetMapping("/preview")
    @Operation(summary = "Count what a cleanup run would delete, without changing anything")
    public ResponseEntity<ApiResponse<CleanupResult>> preview() {
        LinkedHashMap<String, Long> counts = service.preview();
        return ResponseEntity.ok(ApiResponse.ok("Preview complete", toResult(counts)));
    }

    @PostMapping("/execute")
    @Operation(summary = "Actually delete the confirmed test data (requires confirm: \"DELETE_TEST_DATA\")")
    public ResponseEntity<ApiResponse<CleanupResult>> execute(@RequestBody ExecuteRequest req) {
        if (req.confirm() == null || !req.confirm().equals(CONFIRM_PHRASE)) {
            throw new BadRequestException("Send {\"confirm\": \"" + CONFIRM_PHRASE + "\"} to run this.");
        }
        LinkedHashMap<String, Long> deleted = service.execute();
        return ResponseEntity.ok(ApiResponse.ok("Test data deleted", toResult(deleted)));
    }

    /** Same machinery, scoped to a specific email or two instead of the hardcoded 16-account
     *  batch -- for cleaning up a one-off test account right after a live feature test, without
     *  touching or re-running the main list. */
    @PostMapping("/preview-adhoc")
    @Operation(summary = "Count what would be deleted for a specific ad-hoc list of test emails")
    public ResponseEntity<ApiResponse<CleanupResult>> previewAdhoc(@RequestBody AdhocRequest req) {
        if (req.emails() == null || req.emails().isEmpty()) {
            throw new BadRequestException("Provide at least one email to preview.");
        }
        LinkedHashMap<String, Long> counts = service.preview(req.emails());
        return ResponseEntity.ok(ApiResponse.ok("Preview complete", toResult(counts)));
    }

    @PostMapping("/execute-adhoc")
    @Operation(summary = "Delete data for a specific ad-hoc list of test emails (requires confirm: \"DELETE_TEST_DATA\")")
    public ResponseEntity<ApiResponse<CleanupResult>> executeAdhoc(@RequestBody AdhocRequest req) {
        if (req.emails() == null || req.emails().isEmpty()) {
            throw new BadRequestException("Provide at least one email to delete.");
        }
        if (req.confirm() == null || !req.confirm().equals(CONFIRM_PHRASE)) {
            throw new BadRequestException("Send confirm: \"" + CONFIRM_PHRASE + "\" to run this.");
        }
        LinkedHashMap<String, Long> deleted = service.execute(req.emails());
        return ResponseEntity.ok(ApiResponse.ok("Test data deleted", toResult(deleted)));
    }

    private CleanupResult toResult(LinkedHashMap<String, Long> counts) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new CleanupResult(counts, total);
    }
}
