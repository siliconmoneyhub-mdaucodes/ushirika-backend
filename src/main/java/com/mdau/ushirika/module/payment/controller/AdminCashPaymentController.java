package com.mdau.ushirika.module.payment.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.payment.dto.AdminCardEntryRequest;
import com.mdau.ushirika.module.payment.dto.AdminCashPaymentRequest;
import com.mdau.ushirika.module.payment.dto.CardPaymentResultDto;
import com.mdau.ushirika.module.payment.dto.MemberBalanceDto;
import com.mdau.ushirika.module.payment.dto.PaymentInitDto;
import com.mdau.ushirika.module.payment.service.PaymentAllocationService;
import com.mdau.ushirika.module.payment.service.PaymentBasketService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * An admin processing a cash payment they physically received from a member. There is no
 * "record it and move on" path anymore — the admin must complete a real Stripe Checkout
 * session (their own card) before the member is credited. Sits under /financial/** so the
 * same roles who could record manual payments before (Financial Admin, delegated Financial
 * Official, plus Admin/Superadmin) can still process cash.
 */
@RestController
@RequestMapping("/financial/cash-payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cash Payments — Admin", description = "Process a cash payment received from a member via a real Stripe checkout")
public class AdminCashPaymentController {

    private final PaymentBasketService paymentBasketService;
    private final PaymentAllocationService paymentAllocationService;
    private final UserRepository userRepository;

    @PostMapping("/checkout")
    @Operation(summary = "Start a Stripe checkout for a cash amount received from a member — the admin pays, the member is credited")
    public ResponseEntity<ApiResponse<PaymentInitDto>> checkout(@Valid @RequestBody AdminCashPaymentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(paymentBasketService.startAdminCashCheckout(req)));
    }

    @PostMapping("/card-entry")
    @Operation(summary = "Charge a member's card entered directly by an admin (Stripe Elements, tokenized client-side)")
    public ResponseEntity<ApiResponse<CardPaymentResultDto>> cardEntry(@Valid @RequestBody AdminCardEntryRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(paymentBasketService.startAdminCardEntry(req)));
    }

    @GetMapping("/members/{memberId}/balance")
    @Operation(summary = "A member's current balance — positive means credit, negative means still owing")
    public ResponseEntity<ApiResponse<MemberBalanceDto>> memberBalance(@PathVariable UUID memberId) {
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        return ResponseEntity.ok(ApiResponse.ok(paymentAllocationService.getBalance(member)));
    }
}
