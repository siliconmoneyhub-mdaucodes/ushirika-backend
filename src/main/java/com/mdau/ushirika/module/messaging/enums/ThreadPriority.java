package com.mdau.ushirika.module.messaging.enums;

/**
 * Urgency of a member <-> staff conversation. Set by the member when opening/escalating a thread or by
 * staff at any time. Validated at the Java layer only (no DB CHECK constraint -- see the messaging block
 * in DataInitializer.ensureSchemaExtensions()).
 */
public enum ThreadPriority {
    NORMAL, HIGH, URGENT;

    /** Sort weight for staff lists: URGENT first, then HIGH, then NORMAL. */
    public int sortWeight() {
        return switch (this) {
            case URGENT -> 0;
            case HIGH -> 1;
            case NORMAL -> 2;
        };
    }
}
