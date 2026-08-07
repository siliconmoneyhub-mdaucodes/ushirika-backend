package com.mdau.ushirika.module.event.repository;

import com.mdau.ushirika.module.event.entity.Event;
import com.mdau.ushirika.module.event.enums.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /** Public listing: published, open to all, upcoming first. */
    Page<Event> findAllByStatusAndMembersOnlyFalseOrderByStartDateTimeAsc(
            EventStatus status, Pageable pageable);

    /** Member listing: all published events regardless of membersOnly flag. */
    Page<Event> findAllByStatusOrderByStartDateTimeAsc(EventStatus status, Pageable pageable);

    /** Admin listing: all events all statuses. */
    Page<Event> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(EventStatus status);

    /** Calendar query: events with given statuses whose start falls within [from, to]. */
    @Query("SELECT e FROM Event e WHERE e.status IN :statuses AND e.startDateTime >= :from AND e.startDateTime <= :to ORDER BY e.startDateTime ASC")
    List<Event> findByStatusInAndStartDateTimeBetween(
            @Param("statuses") List<EventStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Count confirmed attendees for capacity enforcement. */
    @Query("""
        SELECT COUNT(r) FROM EventRegistration r
        WHERE r.event.id = :eventId
        AND r.status IN ('REGISTERED', 'ATTENDED')
        """)
    long countActiveRegistrations(@Param("eventId") UUID eventId);

    /** Upcoming, not-yet-24h-reminded events whose start has entered the 24h window. */
    @Query("SELECT e FROM Event e WHERE e.status IN :statuses AND e.reminder24hSent = false " +
           "AND e.startDateTime >= :from AND e.startDateTime <= :to")
    List<Event> findByStatusInAndReminder24hSentFalseAndStartDateTimeBetween(
            @Param("statuses") List<EventStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Upcoming, not-yet-6h-reminded events whose start has entered the 6h window. */
    @Query("SELECT e FROM Event e WHERE e.status IN :statuses AND e.reminder6hSent = false " +
           "AND e.startDateTime >= :from AND e.startDateTime <= :to")
    List<Event> findByStatusInAndReminder6hSentFalseAndStartDateTimeBetween(
            @Param("statuses") List<EventStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
