package com.mdau.ushirika.module.welfare.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
    name = "welfare_categories",
    indexes = {
        @Index(name = "idx_wc_active", columnList = "active")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WelfareCategory extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Optional cap on how much a member can request per application.
     * Null means no cap enforced at application level.
     */
    @Column(name = "max_amount", precision = 12, scale = 2)
    private BigDecimal maxAmount;

    /** Welfare Fund is USD-only -- see WelfareService, which has no path that lets this be
     * set to anything else. Kept as a column rather than removed so a currency field is
     * already there if this org ever needs multi-currency welfare support. */
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
