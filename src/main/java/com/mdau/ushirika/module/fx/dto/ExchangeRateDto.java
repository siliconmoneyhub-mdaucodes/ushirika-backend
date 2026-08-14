package com.mdau.ushirika.module.fx.dto;

import com.mdau.ushirika.module.fx.service.ExchangeRateService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public record ExchangeRateDto(double usdToKes, LocalDateTime asOf) {
    public static ExchangeRateDto from(ExchangeRateService.Rate rate) {
        return new ExchangeRateDto(rate.usdToKes(), LocalDateTime.ofInstant(rate.asOf(), ZoneOffset.UTC));
    }
}
