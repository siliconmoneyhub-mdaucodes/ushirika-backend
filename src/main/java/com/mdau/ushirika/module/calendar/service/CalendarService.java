package com.mdau.ushirika.module.calendar.service;

import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.attendance.enums.FineStatus;
import com.mdau.ushirika.module.attendance.enums.MeetingStatus;
import com.mdau.ushirika.module.attendance.repository.FineRepository;
import com.mdau.ushirika.module.attendance.repository.MeetingRepository;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.calendar.dto.CalendarItemDto;
import com.mdau.ushirika.module.calendar.enums.CalendarItemType;
import com.mdau.ushirika.module.dues.enums.DuesStatus;
import com.mdau.ushirika.module.dues.repository.MembershipDueRepository;
import com.mdau.ushirika.module.event.enums.EventStatus;
import com.mdau.ushirika.module.event.repository.EventRepository;
import com.mdau.ushirika.module.mgr.repository.MgrSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final MeetingRepository       meetingRepository;
    private final EventRepository         eventRepository;
    private final MgrSlotRepository       mgrSlotRepository;
    private final MembershipDueRepository dueRepository;
    private final FineRepository          fineRepository;
    private final UserRepository          userRepository;

    @Transactional(readOnly = true)
    public List<CalendarItemDto> getMyCalendar(LocalDate from, LocalDate to) {
        User user = currentUser();
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.atTime(23, 59, 59);
        List<CalendarItemDto> items = new ArrayList<>();

        // Meetings (all members attend — no per-member filter)
        meetingRepository
                .findByStatusAndMeetingDateBetween(MeetingStatus.SCHEDULED, fromDt, toDt)
                .forEach(m -> items.add(new CalendarItemDto(
                        CalendarItemType.MEETING,
                        m.getId(),
                        m.getTitle(),
                        m.getDescription(),
                        AppClock.toInstant(m.getMeetingDate()),
                        null,
                        m.getLocation(),
                        "/portal/meetings"
                )));

        // Events (published and ongoing)
        eventRepository
                .findByStatusInAndStartDateTimeBetween(
                        List.of(EventStatus.PUBLISHED, EventStatus.ONGOING), fromDt, toDt)
                .forEach(e -> items.add(new CalendarItemDto(
                        CalendarItemType.EVENT,
                        e.getId(),
                        e.getTitle(),
                        e.getDescription(),
                        AppClock.toInstant(e.getStartDateTime()),
                        AppClock.toInstant(e.getEndDateTime()),
                        e.getVenue() != null ? e.getVenue() : e.getOnlineLink(),
                        "/portal/events/" + e.getId()
                )));

        // MGR payout slots drawn for this member
        mgrSlotRepository
                .findAllByUserAndScheduledPayoutDateBetween(user, from, to)
                .stream()
                .filter(s -> s.getScheduledPayoutDate() != null)
                .forEach(s -> items.add(new CalendarItemDto(
                        CalendarItemType.MGR_PAYOUT,
                        s.getId(),
                        "MGR Payout — " + s.getCycle().getName(),
                        "Month " + s.getPayoutMonth() + " payout" +
                                (s.getPayoutAmount() != null ? ": $" + s.getPayoutAmount() : ""),
                        AppClock.toInstant(s.getScheduledPayoutDate().atStartOfDay()),
                        null,
                        null,
                        "/portal/mgr"
                )));

        // Dues due in range (non-PAID)
        dueRepository
                .findByUserAndDueDateBetween(user, from, to)
                .stream()
                .filter(d -> d.getStatus() != DuesStatus.PAID && d.getStatus() != DuesStatus.WAIVED)
                .forEach(d -> items.add(new CalendarItemDto(
                        CalendarItemType.DUES_DUE,
                        d.getId(),
                        "Membership Dues — " + d.getYear(),
                        "Annual dues of $" + d.getAmount() + " due",
                        AppClock.toInstant(d.getDueDate().atStartOfDay()),
                        null,
                        null,
                        "/portal/payments"
                )));

        // Pending fines due in range
        fineRepository
                .findByUserAndStatusAndDueDateBetween(user, FineStatus.PENDING, from, to)
                .forEach(f -> items.add(new CalendarItemDto(
                        CalendarItemType.FINE_DUE,
                        f.getId(),
                        "Fine Due — $" + f.getAmount(),
                        f.getReason(),
                        AppClock.toInstant(f.getDueDate().atStartOfDay()),
                        null,
                        null,
                        "/portal/meetings"
                )));

        items.sort(Comparator.comparing(CalendarItemDto::start));
        return items;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
