package com.mdau.ushirika.common.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.service.SimpleCaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/captcha")
@RequiredArgsConstructor
@Tag(name = "Captcha", description = "Self-hosted proof-of-work verification challenge for public forms")
public class CaptchaController {

    private final SimpleCaptchaService captchaService;

    @GetMapping("/challenge")
    @Operation(summary = "Get a fresh verification challenge to attach to a public form submission")
    public ResponseEntity<ApiResponse<SimpleCaptchaService.Challenge>> challenge() {
        return ResponseEntity.ok(ApiResponse.ok(captchaService.generate()));
    }
}
