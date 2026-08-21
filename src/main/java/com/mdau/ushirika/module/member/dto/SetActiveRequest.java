package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.member.enums.MemberStatusReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** reason is required only when deactivating (active=false) and must be one of the
 * admin-selectable categories -- see AdminUserService.setActive(). Reactivating always records
 * REINSTATED. notes is always required so the member sees something specific, not just a category. */
public record SetActiveRequest(
        @NotNull(message = "Active is required") Boolean active,
        MemberStatusReason reason,
        @NotBlank(message = "A note explaining the reason is required") String notes
) {}
