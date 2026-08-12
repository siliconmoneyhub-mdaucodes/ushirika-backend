package com.mdau.ushirika.module.mgr.enums;

public enum JoinRequestStatus {
    /** Awaiting coordinator review. */
    PENDING,
    /** Coordinator approved in principle -- queued for automatic admission into the
     * next cycle that activates, first-come-first-served by application date. */
    WAITLISTED,
    /** Swept into a real cycle at that cycle's activation; has an MgrSlot. */
    ADMITTED,
    REJECTED
}
