package com.mdau.ushirika.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

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

    /**
     * Entities like {@code Meeting.meetingDate} store a bare {@code LocalDateTime} meaning
     * "wall-clock time in {@link #ORG_ZONE}", with no offset of its own. Sent to the frontend
     * as-is, a viewer's browser (e.g. {@code new Date(iso)}) reads it as its OWN local time
     * instead -- correct only for a Central-time viewer, wrong by several hours for anyone else
     * (an admin browsing from Kenya, in practice). Convert to a real instant before it crosses
     * the wire so every viewer's browser converts it to their own local time correctly.
     */
    public static Instant toInstant(LocalDateTime orgLocal) {
        return orgLocal == null ? null : orgLocal.atZone(ORG_ZONE).toInstant();
    }

    /**
     * Audit/creation/update stamps (e.g. {@code AuditLog.createdAt}, every {@code BaseEntity}'s
     * {@code createdAt}/{@code updatedAt} via Spring's default JPA auditing {@code
     * DateTimeProvider}) are written with a bare {@code LocalDateTime.now()} -- which, on this
     * server, means the digits are already UTC wall-clock time, not {@link #ORG_ZONE}. Sent to
     * the frontend as-is, a viewer's browser still misreads them as its OWN local time (same
     * failure mode as {@link #toInstant}), so this needs the same wire-format fix -- just tagged
     * with the zone the digits actually came from (UTC), not the org's.
     */
    public static Instant serverInstant(LocalDateTime utcStamp) {
        return utcStamp == null ? null : utcStamp.toInstant(ZoneOffset.UTC);
    }
}
