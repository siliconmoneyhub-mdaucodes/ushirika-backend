package com.mdau.ushirika.module.constitution.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.constitution.dto.GoverningDocumentDto;
import com.mdau.ushirika.module.constitution.dto.GoverningDocumentRequest;
import com.mdau.ushirika.module.constitution.service.ConstitutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/constitution")
@RequiredArgsConstructor
public class AdminConstitutionController {

    private final ConstitutionService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GoverningDocumentDto>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok("Governing documents retrieved", service.listAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GoverningDocumentDto>> create(@Valid @RequestBody GoverningDocumentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Document created", service.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GoverningDocumentDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody GoverningDocumentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Document updated", service.update(id, req)));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<GoverningDocumentDto>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Document published", service.publish(id)));
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<ApiResponse<GoverningDocumentDto>> unpublish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Document unpublished", service.unpublish(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Document deleted"));
    }
}
