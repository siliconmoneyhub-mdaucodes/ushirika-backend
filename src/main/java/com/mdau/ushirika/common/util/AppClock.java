package com.mdau.ushirika.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The org's members and every real-world event (meetings, dues deadlines, membership-year
 * boundaries) live in US Central time -- but the server itself runs in UTC (Railway's default),
 * and admins may be logged in from anywhere (Kenya, in practice). {@code LocalDate.now()} /
 * {@code LocalDateTime.now()} with no zone silently use the JVM's default (UTC), which drifts
 * against Central by 5-6 hours -- enough to flip a calendar day early or late depending on time
 * of day, which is exactly wrong for "is this due date in the past" or "has the new membership
 * year started" checks. Use {@link #today()} / {@link #now()} anywhere a decision depends on
 * which real-world calendar day it is for the org; leave bare {@code Instant}/audit timestamps
 * alone since an instant doesn't belong to a timezone in the first place.
 */
public final class AppClock {

    public static final ZoneId ORG_ZONE = ZoneId.of("America/Chicago");

    private AppClock() {}

    public static LocalDate today() {
        return LocalDate.now(ORG_ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ORG_ZONE);
    }
}
