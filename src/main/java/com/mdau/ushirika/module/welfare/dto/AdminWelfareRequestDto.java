package com.mdau.ushirika.module.welfare.dto;

import com.mdau.ushirika.module.auth.dto.UserDto;
import com.mdau.ushirika.module.member.enums.ApprovalDecision;
import com.mdau.ushirika.module.welfare.entity.WelfareRequest;
import com.mdau.ushirika.module.welfare.entity.WelfareRequestApproval;
import com.mdau.ushirika.module.welfare.enums.WelfareRequestStatus;
import com.mdau.ushirika.common.util.AppClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full admin/superadmin view — includes internal notes and per-admin votes. */
public record AdminWelfareRequestDto(
        UUID id,
        String referenceNumber,
        UserDto member,
        WelfareCategoryDto category,
        WelfareRequestStatus status,
        BigDecimal amountRequested,
        String currency,
        String description,
        List<String> documentUrls,
        String rejectionReason,
        String adminNotes,
        Instant submittedAt,
        Instant reviewedAt,
        Instant approvedAt,
        List<ApprovalSummary> approvals,
        WelfareRequestTrackDto.DisbursementSummary disbursement
) {

    public record ApprovalSummary(
            UUID id,
            String adminName,       // null for peer admins (anonymity)
            ApprovalDecision decision,
            String comment,         // null for peer admins
            Instant decidedAt
    ) {
        public static ApprovalSummary forSuperAdmin(WelfareRequestApproval a) {
            return new ApprovalSummary(a.getId(), a.getAdmin().getFullName(),
                    a.getDecision(), a.getComment(), AppClock.serverInstant(a.getDecidedAt()));
        }

        public static ApprovalSummary forAdmin(WelfareRequestApproval a) {
            return new ApprovalSummary(a.getId(), null,
                    a.getDecision(), null, AppClock.serverInstant(a.getDecidedAt()));
        }
    }

    public static AdminWelfareRequestDto from(WelfareRequest r, boolean isSuperAdmin) {
        List<ApprovalSummary> approvalSummaries = r.getApprovals().stream()
                .map(a -> isSuperAdmin
                        ? ApprovalSummary.forSuperAdmin(a)
                        : ApprovalSummary.forAdmin(a))
                .toList();

        WelfareRequestTrackDto.DisbursementSummary ds = null;
        if (r.getDisbursement() != null) {
            var d = r.getDisbursement();
            ds = new WelfareRequestTrackDto.DisbursementSummary(
                    d.getAmountDisbursed(), d.getCurrency(),
                    d.getMethod().name(), AppClock.serverInstant(d.getDisbursedAt())
            );
        }

        return new AdminWelfareRequestDto(
                r.getId(), r.getReferenceNumber(),
                UserDto.from(r.getMember()),
                WelfareCategoryDto.from(r.getCategory()),
                r.getStatus(), r.getAmountRequested(), r.getCurrency(),
                r.getDescription(), r.getDocumentUrls(),
                r.getRejectionReason(),
                isSuperAdmin ? r.getAdminNotes() : null,
                AppClock.serverInstant(r.getSubmittedAt()), AppClock.serverInstant(r.getReviewedAt()), AppClock.serverInstant(r.getApprovedAt()),
                approvalSummaries, ds
        );
    }
}
