package com.mdau.ushirika.module.fx.controller;

import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.fx.dto.ExchangeRateDto;
import com.mdau.ushirika.module.fx.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** USD/KES display rate -- MGR (and anything else money-related) stays stored and charged in
 *  USD; this only backs the KES-converted view. */
@RestController
@RequestMapping("/public/exchange-rate")
@RequiredArgsConstructor
public class PublicExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public ResponseEntity<ApiResponse<ExchangeRateDto>> getRate() {
        return ResponseEntity.ok(ApiResponse.ok(ExchangeRateDto.from(exchangeRateService.getUsdToKesRate())));
    }
}
