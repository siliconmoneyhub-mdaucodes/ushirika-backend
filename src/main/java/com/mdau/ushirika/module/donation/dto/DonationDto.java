package com.mdau.ushirika.module.donation.dto;

import com.mdau.ushirika.module.donation.entity.Donation;
import com.mdau.ushirika.module.donation.enums.DonationStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DonationDto(
        UUID id,
        UUID campaignId,
        String campaignTitle,
        String donorName,
        String donorEmail,
        BigDecimal amount,
        String currency,
        String message,
        String stripeSessionId,
        DonationStatus status,
        Instant donatedAt
) {
    public static DonationDto from(Donation d) {
        return new DonationDto(
                d.getId(),
                d.getCampaign() != null ? d.getCampaign().getId() : null,
                d.getCampaign() != null ? d.getCampaign().getTitle() : "General Donation",
                d.getDonorName(),
                d.getDonorEmail(),
                d.getAmount(),
                d.getCurrency(),
                d.getMessage(),
                d.getStripeSessionId(),
                d.getStatus(),
                AppClock.serverInstant(d.getDonatedAt())
        );
    }
}
