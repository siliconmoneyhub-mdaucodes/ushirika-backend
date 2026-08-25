package com.mdau.ushirika.module.mgr.dto;

import com.mdau.ushirika.module.mgr.entity.MgrSlot;
import com.mdau.ushirika.module.mgr.enums.SlotStatus;
import com.mdau.ushirika.module.member.entity.MemberProfile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record MgrSlotDto(
        UUID id,
        UUID cycleId,
        UUID userId,
        String memberName,
        String email,
        String memberId,
        String memberPhotoUrl,
        int slotNumber,
        Integer payoutMonth,
        Integer payoutOrder,
        LocalDate scheduledPayoutDate,
        BigDecimal payoutAmount,
        SlotStatus status,
        LocalDateTime drawnAt,
        LocalDateTime paidAt,
        String paymentReference,
        boolean receiptConfirmed,
        LocalDateTime receiptConfirmedAt,
        String receiptNotes,
        String adminNotes,
        List<MgrContributionDto> contributions
) {
    public static MgrSlotDto from(MgrSlot s, String memberId, String photoUrl,
                                   List<MgrContributionDto> contributions) {
        String fullName = s.getUser().getFullName();
        return new MgrSlotDto(
                s.getId(), s.getCycle().getId(), s.getUser().getId(),
                fullName, s.getUser().getEmail(), memberId, photoUrl,
                s.getSlotNumber(), s.getPayoutMonth(), s.getPayoutOrder(),
                s.getScheduledPayoutDate(), s.getPayoutAmount(), s.getStatus(),
                s.getDrawnAt(), s.getPaidAt(), s.getPaymentReference(),
                s.isReceiptConfirmed(), s.getReceiptConfirmedAt(), s.getReceiptNotes(),
                s.getAdminNotes(), contributions
        );
    }

    public static MgrSlotDto summary(MgrSlot s, String memberId, String photoUrl) {
        String fullName = s.getUser().getFullName();
        return new MgrSlotDto(
                s.getId(), s.getCycle().getId(), s.getUser().getId(),
                fullName, s.getUser().getEmail(), memberId, photoUrl,
                s.getSlotNumber(), s.getPayoutMonth(), s.getPayoutOrder(),
                s.getScheduledPayoutDate(), s.getPayoutAmount(), s.getStatus(),
                s.getDrawnAt(), s.getPaidAt(), s.getPaymentReference(),
                s.isReceiptConfirmed(), s.getReceiptConfirmedAt(), s.getReceiptNotes(),
                s.getAdminNotes(), null
        );
    }

    /** Public-safe view — excludes email. Used for the beneficiary reveal in member portal. */
    public static MgrSlotDto publicView(MgrSlot s, String memberId, String photoUrl) {
        String fullName = s.getUser().getFullName();
        return new MgrSlotDto(
                s.getId(), s.getCycle().getId(), s.getUser().getId(),
                fullName, null, memberId, photoUrl,
                s.getSlotNumber(), s.getPayoutMonth(), s.getPayoutOrder(),
                s.getScheduledPayoutDate(), s.getPayoutAmount(), s.getStatus(),
                s.getDrawnAt(), s.getPaidAt(), null,
                false, null, null, null, null
        );
    }
}
