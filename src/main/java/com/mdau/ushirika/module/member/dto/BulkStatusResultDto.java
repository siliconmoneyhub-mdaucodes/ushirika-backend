package com.mdau.ushirika.module.member.dto;

import java.util.List;

/** succeeded: how many of the requested members were actually updated. failures: one
 * "email: reason" entry per member that couldn't be changed (e.g. tried to bulk-deactivate a
 * SUPERADMIN, or a member whose status no longer matched what the admin selected against) --
 * partial success is normal for a bulk action, not itself an error. */
public record BulkStatusResultDto(int succeeded, List<String> failures) {}
