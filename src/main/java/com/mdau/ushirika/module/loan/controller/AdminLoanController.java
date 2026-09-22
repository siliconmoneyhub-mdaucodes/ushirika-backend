package com.mdau.ushirika.module.loan.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.loan.dto.*;
import com.mdau.ushirika.module.loan.enums.LoanStatus;
import com.mdau.ushirika.module.loan.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/loans")
@RequiredArgsConstructor
public class AdminLoanController {

    private final LoanService loanService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<LoanApplicationDto>>> listLoans(
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.listLoans(status, page, size, sort)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LoanApplicationDto>> getLoan(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.getLoanById(id)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<LoanApplicationDto>>> getLoansByUser(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.getLoansByUser(userId)));
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<ApiResponse<LoanApplicationDto>> reviewLoan(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewLoanRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Loan reviewed", loanService.reviewLoan(id, req)));
    }

    @PostMapping("/{id}/disburse")
    public ResponseEntity<ApiResponse<LoanApplicationDto>> disburseLoan(
            @PathVariable UUID id,
            @Valid @RequestBody DisburseLoanRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Loan disbursed", loanService.disburseLoan(id, req)));
    }

    @PostMapping("/repayments")
    public ResponseEntity<ApiResponse<LoanInstallmentDto>> recordRepayment(
            @Valid @RequestBody RecordRepaymentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Repayment recorded", loanService.recordRepayment(req)));
    }

    @PatchMapping("/installments/{id}/waive")
    public ResponseEntity<ApiResponse<LoanInstallmentDto>> waiveInstallment(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.ok("Installment waived",
                loanService.waiveInstallment(id, reason)));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<LoanApplicationDto>> markDefaulted(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        return ResponseEntity.ok(ApiResponse.ok("Loan marked as defaulted",
                loanService.markDefaulted(id, notes)));
    }
}
