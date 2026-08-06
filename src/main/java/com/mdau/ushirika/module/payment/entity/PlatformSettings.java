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
}
