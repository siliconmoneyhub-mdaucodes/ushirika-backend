package com.mdau.ushirika.module.contact.controller;

import com.mdau.ushirika.common.exception.TooManyRequestsException;
import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.common.service.SimpleCaptchaService;
import com.mdau.ushirika.common.util.ClientIpResolver;
import com.mdau.ushirika.module.contact.dto.ContactMessageRequest;
import com.mdau.ushirika.module.contact.dto.ContactMessageSubmittedDto;
import com.mdau.ushirika.module.contact.service.ContactMessageService;
import com.mdau.ushirika.module.contact.service.ContactRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ContactRateLimiter rateLimiter;

    @PostMapping
    public ResponseEntity<ApiResponse<ContactMessageSubmittedDto>> submit(
            @Valid @RequestBody ContactMessageRequest req, HttpServletRequest httpReq) {
        if (!rateLimiter.tryConsume(ClientIpResolver.resolve(httpReq))) {
            throw new TooManyRequestsException("Too many messages sent recently — please try again later.");
        }
        captchaService.verify(req.captchaToken(), req.captchaNonce(), req.honeypot());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Message received", service.submit(req)));
    }
}
