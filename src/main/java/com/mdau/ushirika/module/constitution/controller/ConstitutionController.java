package com.mdau.ushirika.module.constitution.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.constitution.dto.GoverningDocumentDto;
import com.mdau.ushirika.module.constitution.service.ConstitutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public/constitution")
@RequiredArgsConstructor
public class ConstitutionController {

    private final ConstitutionService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GoverningDocumentDto>>> listPublished() {
        return ResponseEntity.ok(ApiResponse.ok("Governing documents retrieved", service.listPublished()));
    }
}
