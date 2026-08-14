package com.mdau.ushirika.module.fx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * USD-to-KES conversion rate for display purposes only -- MGR (and anything else money-related)
 * is always stored and charged in USD; this exists so members and admins can see the friendlier
 * KES-converted figure without that figure ever becoming the number that actually gets charged.
 * Fetches from a free, no-key public rate API and caches in memory; never throws -- a stale or
 * missing rate falls back to the last good value, or a hardcoded approximation on first boot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private static final String RATE_API_URL = "https://open.er-api.com/v6/latest/USD";
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final double FALLBACK_USD_TO_KES = 130.0;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile double cachedRate = FALLBACK_USD_TO_KES;
    private volatile Instant cachedAt = null;
    // Tracks the last fetch *attempt* (success or failure) separately from the last successful
    // one, so a down rate API doesn't get hit on every single request -- failures still back off
    // for a full CACHE_TTL window and just keep serving the fallback/last-good rate meanwhile.
    private volatile Instant lastAttemptAt = null;

    public record Rate(double usdToKes, Instant asOf) {}

    public Rate getUsdToKesRate() {
        if (lastAttemptAt == null || Duration.between(lastAttemptAt, Instant.now()).compareTo(CACHE_TTL) > 0) {
            refresh();
        }
        return new Rate(cachedRate, cachedAt != null ? cachedAt : Instant.now());
    }

    private synchronized void refresh() {
        // Re-check inside the lock -- another thread may have already refreshed while this one waited.
        if (lastAttemptAt != null && Duration.between(lastAttemptAt, Instant.now()).compareTo(CACHE_TTL) <= 0) {
            return;
        }
        lastAttemptAt = Instant.now();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RATE_API_URL))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("ExchangeRateService: rate API returned HTTP {}, keeping previous rate", response.statusCode());
                return;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode kes = root.path("rates").path("KES");
            if (!kes.isNumber()) {
                log.warn("ExchangeRateService: rate API response had no numeric KES rate, keeping previous rate");
                return;
            }
            cachedRate = kes.asDouble();
            cachedAt = Instant.now();
        } catch (Exception e) {
            log.warn("ExchangeRateService: failed to fetch USD/KES rate, keeping previous rate: {}", e.getMessage());
        }
    }
}
