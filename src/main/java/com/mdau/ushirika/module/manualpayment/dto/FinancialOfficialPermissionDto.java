package com.mdau.ushirika.module.manualpayment.dto;

import com.mdau.ushirika.module.manualpayment.entity.FinancialOfficialPermission;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.UUID;

public record FinancialOfficialPermissionDto(
    UUID id,
    UUID officialId,
    String officialName,
    String officialEmail,
    boolean canRecordPayments,
    boolean canApprovePayments,
    String grantedByName,
    String grantedByEmail,
    Instant grantedAt
) {
    public static FinancialOfficialPermissionDto from(FinancialOfficialPermission p) {
        return new FinancialOfficialPermissionDto(
            p.getId(),
            p.getOfficial().getId(),
            p.getOfficial().getFullName(),
            p.getOfficial().getEmail(),
            p.isCanRecordPayments(),
            p.isCanApprovePayments(),
            p.getGrantedBy().getFullName(),
            p.getGrantedBy().getEmail(),
            AppClock.serverInstant(p.getCreatedAt())
        );
    }
}
