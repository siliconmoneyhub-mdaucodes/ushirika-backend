package com.mdau.ushirika.module.contact.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.service.SimpleCaptchaService;
import com.mdau.ushirika.module.contact.dto.ContactMessageRequest;
import com.mdau.ushirika.module.contact.dto.ContactMessageSubmittedDto;
import com.mdau.ushirika.module.contact.service.ContactMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/contact")
@RequiredArgsConstructor
public class ContactController {

    private final ContactMessageService service;
    private final SimpleCaptchaService captchaService;

    @PostMapping
    public ResponseEntity<ApiResponse<ContactMessageSubmittedDto>> submit(
            @Valid @RequestBody ContactMessageRequest req) {
        captchaService.verify(req.captchaToken(), req.captchaAnswer(), req.honeypot());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Message received", service.submit(req)));
    }
}
