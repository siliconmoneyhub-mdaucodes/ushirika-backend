package com.mdau.ushirika.module.payment.dto;

import java.math.BigDecimal;

/** netBalance > 0 means the member is paid ahead (credit); < 0 means they still owe that much
 * even after applying any credit on file. */
public record MemberBalanceDto(
        BigDecimal creditAmount,
        BigDecimal totalOutstanding,
        BigDecimal netBalance
) {
    public static MemberBalanceDto of(BigDecimal creditAmount, BigDecimal totalOutstanding) {
        return new MemberBalanceDto(creditAmount, totalOutstanding, creditAmount.subtract(totalOutstanding));
    }
}
