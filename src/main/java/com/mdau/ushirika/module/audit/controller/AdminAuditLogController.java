package com.mdau.ushirika.module.audit.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.common.util.SortParams;
import com.mdau.ushirika.module.audit.dto.AuditLogDto;
import com.mdau.ushirika.module.audit.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/audit-logs")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Read-only view of significant admin actions")
public class AdminAuditLogController {

    private final AuditLogRepository auditLogRepository;

    private static final java.util.Set<String> AUDIT_SORTABLE_FIELDS =
            java.util.Set.of("actorName", "action", "entityType", "targetLabel", "createdAt");

    @GetMapping
    @Operation(summary = "List audit log entries with optional filters")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> list(
            @RequestParam(required = false) UUID   actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort
    ) {
        Sort resolved = SortParams.parse(sort, AUDIT_SORTABLE_FIELDS, Sort.by("createdAt").descending());
        Page<AuditLogDto> result = auditLogRepository
                .findWithFilters(actorId, action, entityType, PageRequest.of(page, size, resolved))
                .map(AuditLogDto::from);

        return ResponseEntity.ok(ApiResponse.ok("Audit logs retrieved", PagedResponse.of(result)));
    }
}
