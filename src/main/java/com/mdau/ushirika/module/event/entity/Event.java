package com.mdau.ushirika.module.event.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.event.enums.EventStatus;
import com.mdau.ushirika.module.event.enums.EventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "events",
    indexes = {
        @Index(name = "idx_ev_status",           columnList = "status"),
        @Index(name = "idx_ev_type",             columnList = "type"),
        @Index(name = "idx_ev_start_dt",         columnList = "start_date_time"),
        @Index(name = "idx_ev_members_only",     columnList = "members_only"),
        // Public listing — published, non-members-only, upcoming
        @Index(name = "idx_ev_status_members",   columnList = "status, members_only"),
        @Index(name = "idx_ev_created_at",       columnList = "created_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event extends BaseEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 15)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "venue", length = 300)
    private String venue;

    /** URL for virtual or hybrid events. */
    @Column(name = "online_link", length = 500)
    private String onlineLink;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time")
    private LocalDateTime endDateTime;

    @Column(name = "registration_deadline")
    private LocalDateTime registrationDeadline;

    /** Null means unlimited capacity. */
    @Column(name = "capacity")
    private Integer capacity;

    /** True = only authenticated approved members may register. */
    @Column(name = "members_only", nullable = false)
    @Builder.Default
    private boolean membersOnly = false;

    /** True = attendees must pay before their registration is confirmed. */
    @Column(name = "requires_payment", nullable = false)
    @Builder.Default
    private boolean requiresPayment = false;

    /** Ticket / entry fee in USD. Null for free events. */
    @Column(name = "ticket_price", precision = 10, scale = 2)
    private BigDecimal ticketPrice;

    @Column(name = "cover_image_url", length = 1000)
    private String coverImageUrl;

    /** Optional searchable tags e.g. ["health", "community"]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EventRegistration> registrations = new ArrayList<>();

    // ── Upcoming reminders (24h / 6h before) ──────────────────────────────────

    @Column(name = "reminder_24h_sent", nullable = false)
    @Builder.Default
    private boolean reminder24hSent = false;

    @Column(name = "reminder_6h_sent", nullable = false)
    @Builder.Default
    private boolean reminder6hSent = false;
}
