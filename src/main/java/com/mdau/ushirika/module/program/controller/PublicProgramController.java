package com.mdau.ushirika.module.program.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.program.dto.PublicProgramDto;
import com.mdau.ushirika.module.program.service.ProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Programs an applicant/member can browse and join — MGR, Benevolence, etc. */
@RestController
@RequestMapping("/public/programs")
@RequiredArgsConstructor
public class PublicProgramController {

    private final ProgramService programService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicProgramDto>>> listActive() {
        return ResponseEntity.ok(ApiResponse.ok(programService.listActivePrograms()));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<PublicProgramDto>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(programService.getActiveProgramBySlug(slug)));
    }
}
