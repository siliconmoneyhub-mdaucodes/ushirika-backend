package com.mdau.ushirika.module.partner.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.partner.dto.PartnerDto;
import com.mdau.ushirika.module.partner.service.PartnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public/partners")
@RequiredArgsConstructor
public class PublicPartnerController {

    private final PartnerService partnerService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PartnerDto>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(partnerService.listPublic()));
    }
}
