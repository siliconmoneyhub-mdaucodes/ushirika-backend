package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.member.enums.MemberStatusReason;
import jakarta.validation.constraints.NotBlank;

/** Body for the split /admin/users/{id}/activate and /admin/users/{id}/deactivate endpoints --
 * the direction is already implied by which endpoint was called, so unlike SetActiveRequest this
 * carries no separate active flag. reason is ignored (always REINSTATED) on the activate path. */
public record SetActiveActionRequest(
        MemberStatusReason reason,
        @NotBlank(message = "A note explaining the reason is required") String notes
) {}
