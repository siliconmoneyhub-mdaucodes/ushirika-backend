package com.mdau.ushirika.module.attendance.repository;

import com.mdau.ushirika.module.attendance.entity.Meeting;
import com.mdau.ushirika.module.attendance.enums.MeetingStatus;
import com.mdau.ushirika.module.attendance.enums.MeetingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    Page<Meeting> findAllByOrderByMeetingDateDesc(Pageable pageable);

    /** Returns the two most recent completed quarterly-type meetings for consecutive-absence checking. */
    List<Meeting> findTop2ByTypeInAndStatusOrderByMeetingDateDesc(List<MeetingType> types, MeetingStatus status);

    List<Meeting> findByStatusAndMeetingDateBetween(MeetingStatus status, LocalDateTime from, LocalDateTime to);

    /** Upcoming, not-yet-24h-reminded meetings whose date has entered the 24h window. */
    List<Meeting> findByStatusAndReminder24hSentFalseAndMeetingDateBetween(
            MeetingStatus status, LocalDateTime from, LocalDateTime to);

    /** Upcoming, not-yet-6h-reminded meetings whose date has entered the 6h window. */
    List<Meeting> findByStatusAndReminder6hSentFalseAndMeetingDateBetween(
            MeetingStatus status, LocalDateTime from, LocalDateTime to);

    List<Meeting> findAllByStatusOrderByMeetingDateAsc(MeetingStatus status);

    /** Next upcoming scheduled meeting. */
    java.util.Optional<Meeting> findFirstByStatusAndMeetingDateAfterOrderByMeetingDateAsc(MeetingStatus status, LocalDateTime after);

    /** Most recently completed meeting — used for "last meeting attendance rate" dashboard stats. */
    java.util.Optional<Meeting> findFirstByStatusOrderByMeetingDateDesc(MeetingStatus status);
}
