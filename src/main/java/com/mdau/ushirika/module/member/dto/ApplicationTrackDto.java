package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;

/** Safe public/member view — no admin names, no internal notes. */
public record ApplicationTrackDto(
        String referenceNumber,
        ApplicationStatus status,
        Instant submittedAt,
        Instant reviewedAt,
        Instant approvedAt,
        String rejectionReason,
        String memberId
) {
    public static ApplicationTrackDto from(MembershipApplication app, String memberId) {
        return new ApplicationTrackDto(
                app.getReferenceNumber(),
                app.getStatus(),
                AppClock.serverInstant(app.getSubmittedAt()),
                AppClock.serverInstant(app.getReviewedAt()),
                AppClock.serverInstant(app.getApprovedAt()),
                app.getRejectionReason(),
                memberId
        );
    }
}
