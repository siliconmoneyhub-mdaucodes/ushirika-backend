package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.payment.entity.PlatformSettings;
import com.mdau.ushirika.module.payment.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Self-healing singleton settings row: created lazily on first real use rather than eagerly at
 * startup via DataInitializer. Eager seeding was tried first and crashed the whole app -- for a
 * brand-new table, ddl-auto=update's schema creation didn't reliably complete before
 * ApplicationRunner beans fired, unlike adding columns to an already-existing table. Lazy
 * creation sidesteps that ordering problem entirely: by the time any request reaches here, the
 * app has fully started and the schema is guaranteed to exist.
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private static final BigDecimal DEFAULT_REGISTRATION_FEE = new BigDecimal("120.00");

    private final PlatformSettingsRepository repository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public BigDecimal getRegistrationFeeAmount() {
        return settings().getRegistrationFeeAmount();
    }

    @Transactional
    public BigDecimal updateRegistrationFeeAmount(BigDecimal amount) {
        PlatformSettings settings = settings();
        BigDecimal previous = settings.getRegistrationFeeAmount();
        settings.setRegistrationFeeAmount(amount);
        BigDecimal updated = repository.save(settings).getRegistrationFeeAmount();

        User admin = currentUser();
        auditLogService.log(admin, "REGISTRATION_FEE_CHANGED", "PlatformSettings", settings.getId(),
                "Registration fee changed from $" + previous + " to $" + amount + " by " + admin.getFullName());

        return updated;
    }

    private PlatformSettings settings() {
        return repository.findAll().stream().findFirst()
                .orElseGet(() -> repository.save(PlatformSettings.builder()
                        .registrationFeeAmount(DEFAULT_REGISTRATION_FEE)
                        .build()));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
