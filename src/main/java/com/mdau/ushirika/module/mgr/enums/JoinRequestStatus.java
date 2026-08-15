package com.mdau.ushirika.module.mgr.enums;

public enum JoinRequestStatus {
    /** Awaiting coordinator review. */
    PENDING,
    /** Coordinator sent the applicant an info form explaining how MGR works. Awaiting the
     * member to confirm in their portal that they want to join the waitlist -- no payment
     * involved, just acknowledgement. */
    FORM_SENT,
    /** Member confirmed -- queued for automatic admission into a cycle. Whenever a new cycle
     * is created, every WAITLISTED member is automatically asked (email + portal) whether they
     * want to join that specific cycle or keep waiting; only those who opt in get swept into
     * that cycle at activation, first-come-first-served by application date among opt-ins. */
    WAITLISTED,
    /** Swept into a real cycle at that cycle's activation; has an MgrSlot. */
    ADMITTED,
    REJECTED
}
