package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.attendance.dto.FineDto;

import java.math.BigDecimal;
import java.util.List;

/** At-a-glance financial standing for one member -- backs the Members admin detail panel so an
 * admin doesn't have to leave the page and cross-reference Dues/Benevolence/Fines separately. */
public record MemberFinancialSummaryDto(
        BigDecimal duesBalance,
        String benevolenceStatus,
        BigDecimal benevolenceBalance,
        List<FineDto> outstandingFines,
        BigDecimal outstandingFinesTotal
) {}
