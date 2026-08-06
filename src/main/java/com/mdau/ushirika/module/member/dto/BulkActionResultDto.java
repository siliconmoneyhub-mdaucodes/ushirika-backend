package com.mdau.ushirika.module.member.dto;

import java.util.List;
import java.util.UUID;

/** Per-item outcome of a bulk operation — one item's failure never blocks the rest, so the
 * admin needs to see exactly which applications succeeded and why any others didn't. */
public record BulkActionResultDto(
        int succeededCount,
        int failedCount,
        List<ItemResult> items
) {
    public record ItemResult(UUID applicationId, boolean success, String error) {
        public static ItemResult success(UUID id) {
            return new ItemResult(id, true, null);
        }

        public static ItemResult failure(UUID id, String error) {
            return new ItemResult(id, false, error);
        }
    }

    public static BulkActionResultDto of(List<ItemResult> items) {
        long succeeded = items.stream().filter(ItemResult::success).count();
        return new BulkActionResultDto((int) succeeded, items.size() - (int) succeeded, items);
    }
}
