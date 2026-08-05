package com.mdau.ushirika.module.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ushirika.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Same rationale as RestAuthenticationEntryPoint: role-based matcher rejections
 * (authorizeHttpRequests path rules) are enforced in the filter chain and never reach
 * GlobalExceptionHandler's @ExceptionHandler(AccessDeniedException.class) — that handler only
 * fires for @PreAuthorize failures thrown inside a controller call. This keeps the response body
 * consistent with the rest of the API for both cases.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail("Access denied"));
    }
}
