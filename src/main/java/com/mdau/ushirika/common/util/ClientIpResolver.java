package com.mdau.ushirika.common.util;

import jakarta.servlet.http.HttpServletRequest;

/** Shared client-IP resolution for rate limiters — Railway sits in front of the app as a
 *  reverse proxy, so the real client address arrives via X-Forwarded-For rather than
 *  the socket's remote address. */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a chain of IPs; leftmost is the original client
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
