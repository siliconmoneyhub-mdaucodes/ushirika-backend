package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.member.enums.MemberStatusReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Bulk counterpart to SetActiveRequest -- same required-notes/reason rules, applied to every
 * user in userIds via the same per-user AdminUserService.setActive() path (so every existing
 * guard -- can't touch SUPERADMIN, can't deactivate yourself, grace-period reset on reactivate,
 * audit logging, notification -- applies identically to each member in the batch). */
public record BulkSetActiveRequest(
        @NotEmpty(message = "Select at least one member") List<UUID> userIds,
        @NotNull(message = "Active is required") Boolean active,
        MemberStatusReason reason,
        @NotBlank(message = "A note explaining the reason is required") String notes
) {}
