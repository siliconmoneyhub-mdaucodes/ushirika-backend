package com.mdau.ushirika.module.payment.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.payment.dto.PeerPaymentDto;
import com.mdau.ushirika.module.payment.enums.PeerPaymentStatus;
import com.mdau.ushirika.module.payment.service.PeerPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Historical — verify/reject is retired along with member self-reporting. Kept read-only
 * so admins can still look back at reports made before the card-only payment migration. */
@RestController
@RequestMapping("/admin/peer-payments")
@PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','FINANCIAL_ADMIN','FINANCIAL_OFFICIAL')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Peer Payments — Admin",
     description = "Historical member-reported Zelle / Venmo / CashApp payments (read-only)")
public class AdminPeerPaymentController {

    private final PeerPaymentService peerPaymentService;

    @GetMapping
    @Operation(summary = "List all peer payment reports, optionally filtered by status")
    public ResponseEntity<ApiResponse<PagedResponse<PeerPaymentDto>>> list(
            @RequestParam(required = false) PeerPaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Reports retrieved",
                peerPaymentService.listAll(status,
                    PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }
}
