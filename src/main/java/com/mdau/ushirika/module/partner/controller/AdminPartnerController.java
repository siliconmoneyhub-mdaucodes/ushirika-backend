package com.mdau.ushirika.module.partner.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.partner.dto.PartnerDto;
import com.mdau.ushirika.module.partner.dto.SavePartnerRequest;
import com.mdau.ushirika.module.partner.service.PartnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/partners")
@RequiredArgsConstructor
public class AdminPartnerController {

    private final PartnerService partnerService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PartnerDto>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(partnerService.listAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PartnerDto>> create(@Valid @RequestBody SavePartnerRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Partner added.", partnerService.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PartnerDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody SavePartnerRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Partner updated.", partnerService.update(id, req)));
    }

    @PostMapping("/{id}/logo")
    public ResponseEntity<ApiResponse<PartnerDto>> uploadLogo(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Logo uploaded.", partnerService.uploadLogo(id, file)));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<PartnerDto>> toggle(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Visibility toggled.", partnerService.toggle(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        partnerService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Partner removed."));
    }
}
