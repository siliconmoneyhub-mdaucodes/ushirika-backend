package com.mdau.ushirika.module.fx.dto;

import com.mdau.ushirika.module.fx.service.ExchangeRateService;

import java.time.Instant;

public record ExchangeRateDto(double usdToKes, Instant asOf) {
    public static ExchangeRateDto from(ExchangeRateService.Rate rate) {
        return new ExchangeRateDto(rate.usdToKes(), rate.asOf());
    }
}
