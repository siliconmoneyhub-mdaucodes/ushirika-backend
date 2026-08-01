package com.mdau.ushirika.module.payment.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.payment.dto.OutstandingBalancesDto;
import com.mdau.ushirika.module.payment.service.PaymentBasketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of what a member currently owes across every ledger — the source of truth
 * for the future "pay my balances" checkout (card-only payment migration, Phase 1/2).
 * Building the checkout endpoint itself is deferred until those phases validate amounts
 * against this data before calling PaymentBasketService.startCheckout.
 */
@RestController
@RequestMapping("/payments/my")
@RequiredArgsConstructor
public class MemberPaymentsController {

    private final PaymentBasketService paymentBasketService;

    @GetMapping("/outstanding")
    public ResponseEntity<ApiResponse<OutstandingBalancesDto>> outstanding() {
        return ResponseEntity.ok(ApiResponse.ok(paymentBasketService.myOutstandingBalances()));
    }
}
