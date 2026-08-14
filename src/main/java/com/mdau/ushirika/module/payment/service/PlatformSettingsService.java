package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.payment.entity.PlatformSettings;
import com.mdau.ushirika.module.payment.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

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
    private static final String DEFAULT_DISPLAY_CURRENCY = "USD";
    private static final Set<String> VALID_CURRENCIES = Set.of("USD", "KES");
    /** Deliberately narrower than most /financial/** access -- excludes FINANCIAL_OFFICIAL, since
     *  this is a platform-wide default, not a day-to-day finance operation. */
    private static final Set<UserRole> CAN_CHANGE_DEFAULT_CURRENCY =
            Set.of(UserRole.ADMIN, UserRole.FINANCIAL_ADMIN, UserRole.SUPERADMIN);

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

    @Transactional
    public String getDefaultDisplayCurrency() {
        return settings().getDefaultDisplayCurrency();
    }

    @Transactional
    public String updateDefaultDisplayCurrency(String currency) {
        if (currency == null || !VALID_CURRENCIES.contains(currency.toUpperCase())) {
            throw new BadRequestException("Currency must be USD or KES.");
        }
        User admin = currentUser();
        if (!CAN_CHANGE_DEFAULT_CURRENCY.contains(admin.getRole())) {
            throw new ForbiddenException("Only Admin, Financial Admin, or Superadmin can change the default display currency.");
        }
        PlatformSettings settings = settings();
        String previous = settings.getDefaultDisplayCurrency();
        settings.setDefaultDisplayCurrency(currency.toUpperCase());
        String updated = repository.save(settings).getDefaultDisplayCurrency();

        auditLogService.log(admin, "DEFAULT_CURRENCY_CHANGED", "PlatformSettings", settings.getId(),
                "Default display currency changed from " + previous + " to " + updated + " by " + admin.getFullName());

        return updated;
    }

    private PlatformSettings settings() {
        return repository.findAll().stream().findFirst()
                .orElseGet(() -> repository.save(PlatformSettings.builder()
                        .registrationFeeAmount(DEFAULT_REGISTRATION_FEE)
                        .defaultDisplayCurrency(DEFAULT_DISPLAY_CURRENCY)
                        .build()));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
