package com.mdau.ushirika.module.payment.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.payment.dto.PaymentLinkDto;
import com.mdau.ushirika.module.payment.dto.UpsertPaymentLinkRequest;
import com.mdau.ushirika.module.payment.enums.PaymentChannel;
import com.mdau.ushirika.module.payment.service.PaymentLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/payment-links")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_FINANCIAL_ADMIN','CAP_FINANCE_ADVANCED')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Payment Links — Admin", description = "Manage Zelle, Venmo, CashApp and Stripe payment handles")
public class AdminPaymentLinksController {

    private final PaymentLinkService service;

    @GetMapping
    @Operation(summary = "List all configured payment links (active and inactive)")
    public ResponseEntity<ApiResponse<List<PaymentLinkDto>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok("Payment links retrieved", service.listAll()));
    }

    @PutMapping("/{channel}")
    @Operation(summary = "Create or update the payment link for a channel (ZELLE, VENMO, CASHAPP, STRIPE)")
    public ResponseEntity<ApiResponse<PaymentLinkDto>> upsert(
            @PathVariable PaymentChannel channel,
            @Valid @RequestBody UpsertPaymentLinkRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                channel.name() + " payment link saved.", service.upsert(channel, req)));
    }

    @DeleteMapping("/{channel}")
    @Operation(summary = "Remove the payment link for a channel")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable PaymentChannel channel) {
        service.delete(channel);
        return ResponseEntity.ok(ApiResponse.ok(channel.name() + " payment link removed."));
    }
}
