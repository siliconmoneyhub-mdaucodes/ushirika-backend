package com.mdau.ushirika.module.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ushirika.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Without this, Spring Security's default entry point sends a bare, bodyless 403 for any
 * unauthenticated request (missing/expired/invalid JWT) instead of 401 — this runs in the
 * filter chain, before GlobalExceptionHandler ever sees it. The frontend's silent token-refresh
 * logic only triggers on 401, so that mismatch silently broke refresh-on-expiry for every route.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail("Authentication required. Please sign in again."));
    }
}
