package com.mdau.ushirika.module.forum.dto;

import com.mdau.ushirika.module.forum.entity.ForumPost;
import com.mdau.ushirika.module.forum.enums.ForumPostStatus;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.common.util.AppClock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ForumPostDto(
        UUID id,
        String memberName,
        String memberMemberId,
        String memberPhotoUrl,
        String title,
        String body,
        List<String> mediaUrls,
        String videoUrl,
        ForumPostStatus status,
        String adminNotes,
        boolean featured,
        String reviewedByName,
        Instant reviewedAt,
        Instant approvedAt,
        Instant createdAt
) {
    public static ForumPostDto from(ForumPost p, MemberProfile profile) {
        return new ForumPostDto(
                p.getId(),
                p.getMember().getFullName(),
                profile != null ? profile.getMemberId() : null,
                profile != null ? profile.getPhotoUrl() : null,
                p.getTitle(),
                p.getBody(),
                p.getMediaUrls(),
                p.getVideoUrl(),
                p.getStatus(),
                p.getAdminNotes(),
                p.isFeatured(),
                p.getReviewedBy() != null ? p.getReviewedBy().getFullName() : null,
                AppClock.serverInstant(p.getReviewedAt()),
                AppClock.serverInstant(p.getApprovedAt()),
                AppClock.serverInstant(p.getCreatedAt())
        );
    }
}
