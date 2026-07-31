package com.mdau.ushirika.module.payment.dto;

import com.mdau.ushirika.module.payment.entity.PeerPayment;
import com.mdau.ushirika.module.payment.enums.PaymentMode;
import com.mdau.ushirika.module.payment.enums.PeerPaymentPurpose;
import com.mdau.ushirika.module.payment.enums.PeerPaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PeerPaymentDto(
    UUID id,
    UUID memberId,
    String memberName,
    String memberEmail,
    BigDecimal amount,
    String currency,
    PaymentMode paymentMode,
    String memberTxReference,
    /** Present only in admin responses — hidden from member-facing endpoints. */
    String adminTxReference,
    String period,
    PeerPaymentPurpose purpose,
    String notes,
    String proofImageUrl,
    /** What the member claims — compare against createdAt and the screenshot's own date during verification. */
    LocalDate paymentDate,
    PeerPaymentStatus status,
    String rejectionReason,
    String verifiedByName,
    LocalDateTime verifiedAt,
    LocalDateTime createdAt
) {
    public static PeerPaymentDto from(PeerPayment p) {
        return new PeerPaymentDto(
            p.getId(),
            p.getMember() != null ? p.getMember().getId() : null,
            p.getMember() != null ? p.getMember().getFullName() : null,
            p.getMember() != null ? p.getMember().getEmail() : null,
            p.getAmount(),
            p.getCurrency(),
            p.getPaymentMode(),
            p.getMemberTxReference(),
            p.getAdminTxReference(),
            p.getPeriod(),
            p.getPurpose(),
            p.getNotes(),
            p.getProofImageUrl(),
            p.getPaymentDate(),
            p.getStatus(),
            p.getRejectionReason(),
            p.getVerifiedBy() != null ? p.getVerifiedBy().getFullName() : null,
            p.getVerifiedAt(),
            p.getCreatedAt()
        );
    }

    /** Member-safe view — strips admin TX reference. */
    public static PeerPaymentDto memberView(PeerPayment p) {
        PeerPaymentDto full = from(p);
        return new PeerPaymentDto(
            full.id(), full.memberId(), full.memberName(), full.memberEmail(),
            full.amount(), full.currency(), full.paymentMode(),
            full.memberTxReference(),
            null,   // adminTxReference not exposed to member
            full.period(), full.purpose(), full.notes(), full.proofImageUrl(), full.paymentDate(), full.status(), full.rejectionReason(),
            full.verifiedByName(), full.verifiedAt(), full.createdAt()
        );
    }
}
