package com.mdau.ushirika.module.payment.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.payment.dto.PaymentBasketSummaryDto;
import com.mdau.ushirika.module.payment.service.PaymentBasketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * SUPERADMIN-only test tool — lets a checkout be completed as if Stripe had confirmed it,
 * without a real card or a real webhook. Exists to test the card-only payment flows
 * (onboarding checkout, pay-my-balances) end to end, including downstream allocation and
 * confirmation emails, without moving real money. Not reachable by ADMIN or any member.
 */
@RestController
@RequestMapping("/admin/payments/simulate")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class AdminPaymentSimulationController {

    private final PaymentBasketService paymentBasketService;

    @GetMapping("/baskets")
    public ResponseEntity<ApiResponse<List<PaymentBasketSummaryDto>>> listPending() {
        return ResponseEntity.ok(ApiResponse.ok(paymentBasketService.listPendingBaskets()));
    }

    @PostMapping("/baskets/{id}/success")
    public ResponseEntity<ApiResponse<Void>> simulateSuccess(@PathVariable UUID id) {
        paymentBasketService.simulateSuccess(id);
        return ResponseEntity.ok(ApiResponse.ok("Basket marked as paid (simulated) and allocated to its ledgers."));
    }
}
