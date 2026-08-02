package com.mdau.ushirika.module.payment.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.payment.dto.OutstandingBalancesDto;
import com.mdau.ushirika.module.payment.dto.PayBalancesRequest;
import com.mdau.ushirika.module.payment.dto.PaymentInitDto;
import com.mdau.ushirika.module.payment.service.PaymentBasketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What a member currently owes across every ledger, and the "pay my balances" checkout
 * that settles a selection of it in one Stripe session (card-only payment migration, Phase 2). */
@RestController
@RequestMapping("/payments/my")
@RequiredArgsConstructor
public class MemberPaymentsController {

    private final PaymentBasketService paymentBasketService;

    @GetMapping("/outstanding")
    public ResponseEntity<ApiResponse<OutstandingBalancesDto>> outstanding() {
        return ResponseEntity.ok(ApiResponse.ok(paymentBasketService.myOutstandingBalances()));
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<PaymentInitDto>> checkout(@Valid @RequestBody PayBalancesRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(paymentBasketService.startBalancesCheckout(
                req.lines(), req.successUrl(), req.cancelUrl())));
    }
}
