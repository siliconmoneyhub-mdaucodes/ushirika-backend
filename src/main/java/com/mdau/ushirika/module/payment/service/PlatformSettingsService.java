package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.payment.entity.PlatformSettings;
import com.mdau.ushirika.module.payment.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Singleton settings row is seeded once by DataInitializer -- callers can assume it exists. */
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository repository;

    @Transactional(readOnly = true)
    public BigDecimal getRegistrationFeeAmount() {
        return settings().getRegistrationFeeAmount();
    }

    @Transactional
    public BigDecimal updateRegistrationFeeAmount(BigDecimal amount) {
        PlatformSettings settings = settings();
        settings.setRegistrationFeeAmount(amount);
        return repository.save(settings).getRegistrationFeeAmount();
    }

    private PlatformSettings settings() {
        return repository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Platform settings not initialized."));
    }
}
