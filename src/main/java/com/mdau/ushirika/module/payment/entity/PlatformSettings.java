package com.mdau.ushirika.module.payment.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Singleton row of platform-wide financial settings. Amounts editable by ADMIN/SUPERADMIN and
 * the finance coordinator roles (FINANCIAL_ADMIN/FINANCIAL_OFFICIAL) -- see
 * SecurityConfig's "/financial/**" matcher, which this settings controller lives under.
 */
@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettings extends BaseEntity {

    @Column(name = "registration_fee_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal registrationFeeAmount;

    /** "USD" or "KES" -- controls the default currency money is displayed in across the app.
     *  Money itself is always stored and charged in USD; this only affects the initial display
     *  state before a user's own session toggle takes over. */
    @Column(name = "default_display_currency", nullable = false, length = 3)
    private String defaultDisplayCurrency;

    /** Months a Benevolence enrollment spends in PROBATION after the $600 fee is fully paid,
     *  before the member may submit claims. Nullable, not defaulted at the column level -- this
     *  row already exists with data in production, and ddl-auto=update adding a NOT NULL column
     *  to a populated table would fail; PlatformSettingsService falls back to a default in code. */
    @Column(name = "benevolence_probation_months")
    private Integer benevolenceProbationMonths;
}
