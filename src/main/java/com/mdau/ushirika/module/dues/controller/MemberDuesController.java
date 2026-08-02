package com.mdau.ushirika.module.dues.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.dues.dto.DuesPaymentDto;
import com.mdau.ushirika.module.dues.dto.MembershipDueDto;
import com.mdau.ushirika.module.dues.service.MembershipDuesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MemberDuesController {

    private final MembershipDuesService duesService;

    @GetMapping("/dues/my")
    public ResponseEntity<ApiResponse<List<MembershipDueDto>>> getMyDues() {
        return ResponseEntity.ok(ApiResponse.ok("Dues fetched", duesService.getMyDues()));
    }

    @GetMapping("/dues/my/installments")
    public ResponseEntity<ApiResponse<PagedResponse<DuesPaymentDto>>> getMyInstallments(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Installments fetched",
                duesService.getMyInstallments(
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }
}
