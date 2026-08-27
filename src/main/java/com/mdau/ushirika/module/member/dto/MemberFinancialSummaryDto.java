package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.attendance.dto.FineDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** At-a-glance financial standing for one member -- backs the Members admin detail panel so an
 * admin doesn't have to leave the page and cross-reference Dues/Benevolence/Fines separately.
 * duesYear/duesDueDate/duesStatus let the admin see *why* duesBalance is what it is (which
 * year, due when, pending vs. actually overdue) instead of a bare dollar figure. */
public record MemberFinancialSummaryDto(
        BigDecimal duesBalance,
        Integer duesYear,
        LocalDate duesDueDate,
        String duesStatus,
        String benevolenceStatus,
        BigDecimal benevolenceBalance,
        List<FineDto> outstandingFines,
        BigDecimal outstandingFinesTotal
) {}
