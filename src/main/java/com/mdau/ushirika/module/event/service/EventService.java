package com.mdau.ushirika.module.event.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.event.dto.*;
import com.mdau.ushirika.module.event.entity.Event;
import com.mdau.ushirika.module.event.entity.EventRegistration;
import com.mdau.ushirika.module.event.enums.EventStatus;
import com.mdau.ushirika.module.event.enums.RegistrationStatus;
import com.mdau.ushirika.module.event.repository.EventRegistrationRepository;
import com.mdau.ushirika.module.event.repository.EventRepository;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    // ─────────────────────────────────────── Public

    @Transactional(readOnly = true)
    public PagedResponse<EventDto> listPublicEvents(Pageable pageable) {
        return PagedResponse.of(
                eventRepository.findAllByStatusAndMembersOnlyFalseOrderByStartDateTimeAsc(
                        EventStatus.PUBLISHED, pageable)
                        .map(e -> EventDto.from(e, countActive(e))));
    }

    @Transactional(readOnly = true)
    public EventDto getPublicEvent(UUID id) {
        Event event = findEventById(id);
        if (event.getStatus() != EventStatus.PUBLISHED || event.isMembersOnly()) {
            throw new ResourceNotFoundException("Event not found.");
        }
        return EventDto.from(event, countActive(event));
    }

    @Transactional
    public RegistrationDto registerAsGuest(UUID eventId, GuestRegistrationRequest req) {
        Event event = findEventById(eventId);
        assertPublishedAndOpen(event);

        if (event.isMembersOnly()) {
            throw new ForbiddenException("This event is open to Ushirika members only. Please log in.");
        }

        // Guest duplicate check — by email per event
        boolean guestAlreadyRegistered = registrationRepository
                .findAllByEventOrderByRegisteredAtAsc(event, Pageable.unpaged())
                .stream()
                .anyMatch(r -> !r.isMemberRegistration()
                        && req.email().equalsIgnoreCase(r.getGuestEmail())
                        && r.getStatus() != RegistrationStatus.CANCELLED);
        if (guestAlreadyRegistered) {
            throw new ConflictException("This email address is already registered for this event.");
        }

        assertCapacityAvailable(event);

        EventRegistration registration = EventRegistration.builder()
                .event(event)
                .guestName(req.fullName())
                .guestEmail(req.email())
                .guestPhone(req.phone())
                .referenceCode(generateReferenceCode())
                .registeredAt(LocalDateTime.now())
                .build();
        registrationRepository.save(registration);

        sendConfirmationEmail(req.email(), req.fullName(), event, registration.getReferenceCode());
        log.info("Guest registered for event '{}': {}", event.getTitle(), req.email());
        return RegistrationDto.from(registration);
    }

    // ─────────────────────────────────────── Member

    @Transactional(readOnly = true)
    public PagedResponse<EventDto> listMemberEvents(Pageable pageable) {
        return PagedResponse.of(
                eventRepository.findAllByStatusOrderByStartDateTimeAsc(EventStatus.PUBLISHED, pageable)
                        .map(e -> EventDto.from(e, countActive(e))));
    }

    @Transactional(readOnly = true)
    public EventDto getMemberEvent(UUID id) {
        Event event = findEventById(id);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Event not found.");
        }
        return EventDto.from(event, countActive(event));
    }

    @Transactional
    public RegistrationDto register(UUID eventId) {
        User member = currentUser();
        Event event = findEventById(eventId);
        assertPublishedAndOpen(event);

        if (event.isMembersOnly()) {
            assertApprovedMember(member);
        }
        if (registrationRepository.existsByEventAndUser(event, member)) {
            throw new ConflictException("You are already registered for this event.");
        }
        assertCapacityAvailable(event);

        EventRegistration registration = EventRegistration.builder()
                .event(event)
                .user(member)
                .referenceCode(generateReferenceCode())
                .registeredAt(LocalDateTime.now())
                .build();
        registrationRepository.save(registration);

        sendConfirmationEmail(member.getEmail(), member.getFullName(), event, registration.getReferenceCode());
        log.info("Member {} registered for event '{}'", member.getEmail(), event.getTitle());
        return RegistrationDto.from(registration);
    }

    @Transactional
    public void cancelRegistration(UUID eventId) {
        User member = currentUser();
        Event event = findEventById(eventId);

        EventRegistration reg = registrationRepository.findByEventAndUser(event, member)
                .orElseThrow(() -> new BadRequestException("You are not registered for this event."));

        if (reg.getStatus() == RegistrationStatus.CANCELLED) {
            throw new BadRequestException("Registration is already cancelled.");
        }
        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new BadRequestException("Cannot cancel registration for a completed or cancelled event.");
        }
        if (LocalDateTime.now().isAfter(event.getStartDateTime())) {
            throw new BadRequestException("Cannot cancel registration after the event has started.");
        }

        reg.setStatus(RegistrationStatus.CANCELLED);
        registrationRepository.save(reg);
        log.info("Member {} cancelled registration for event '{}'", member.getEmail(), event.getTitle());
    }

    @Transactional(readOnly = true)
    public PagedResponse<RegistrationDto> myRegistrations(Pageable pageable) {
        User member = currentUser();
        return PagedResponse.of(
                registrationRepository.findAllByUserOrderByRegisteredAtDesc(member, pageable)
                        .map(RegistrationDto::from));
    }

    // ─────────────────────────────────────── Admin

    @Transactional(readOnly = true)
    public PagedResponse<EventDto> listAllEvents(Pageable pageable) {
        return PagedResponse.of(eventRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(e -> EventDto.from(e, countActive(e))));
    }

    @Transactional
    public EventDto createEvent(EventRequest req) {
        Event event = Event.builder()
                .title(req.title())
                .description(req.description())
                .type(req.type())
                .venue(req.venue())
                .onlineLink(req.onlineLink())
                .startDateTime(req.startDateTime())
                .endDateTime(req.endDateTime())
                .registrationDeadline(req.registrationDeadline())
                .capacity(req.capacity())
                .membersOnly(req.membersOnly())
                .requiresPayment(req.requiresPayment())
                .ticketPrice(req.ticketPrice())
                .coverImageUrl(req.coverImageUrl())
                .tags(req.tags() != null ? req.tags() : new ArrayList<>())
                .build();
        return EventDto.from(eventRepository.save(event), 0L);
    }

    @Transactional
    public EventDto updateEvent(UUID id, EventRequest req) {
        Event event = findEventById(id);
        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new BadRequestException("Cannot edit a completed or cancelled event.");
        }
        event.setTitle(req.title());
        event.setDescription(req.description());
        event.setType(req.type());
        event.setVenue(req.venue());
        event.setOnlineLink(req.onlineLink());
        event.setStartDateTime(req.startDateTime());
        event.setEndDateTime(req.endDateTime());
        event.setRegistrationDeadline(req.registrationDeadline());
        event.setCapacity(req.capacity());
        event.setMembersOnly(req.membersOnly());
        event.setRequiresPayment(req.requiresPayment());
        event.setTicketPrice(req.ticketPrice());
        event.setCoverImageUrl(req.coverImageUrl());
        event.setTags(req.tags() != null ? req.tags() : new ArrayList<>());
        return EventDto.from(eventRepository.save(event), countActive(event));
    }

    @Transactional
    public EventDto updateStatus(UUID id, EventStatus newStatus) {
        Event event = findEventById(id);
        event.setStatus(newStatus);
        return EventDto.from(eventRepository.save(event), countActive(event));
    }

    @Transactional(readOnly = true)
    public PagedResponse<RegistrationDto> listRegistrations(
            UUID eventId, RegistrationStatus status, Pageable pageable) {
        Event event = findEventById(eventId);
        var page = status != null
                ? registrationRepository.findAllByEventAndStatusOrderByRegisteredAtAsc(event, status, pageable)
                : registrationRepository.findAllByEventOrderByRegisteredAtAsc(event, pageable);
        return PagedResponse.of(page.map(RegistrationDto::from));
    }

    @Transactional
    public RegistrationDto markAttendance(UUID eventId, UUID registrationId, AttendanceRequest req) {
        User admin = currentUser();
        findEventById(eventId); // validate event exists

        EventRegistration reg;
        if (req.referenceCode() != null && !req.referenceCode().isBlank()) {
            reg = registrationRepository.findByReferenceCode(req.referenceCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No registration found with reference code: " + req.referenceCode()));
        } else {
            reg = registrationRepository.findById(registrationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));
        }

        if (!reg.getEvent().getId().equals(eventId)) {
            throw new BadRequestException("Registration does not belong to this event.");
        }
        if (!List.of(RegistrationStatus.ATTENDED, RegistrationStatus.NO_SHOW).contains(req.status())) {
            throw new BadRequestException("Attendance status must be ATTENDED or NO_SHOW.");
        }

        reg.setStatus(req.status());
        reg.setAttendanceMarkedAt(LocalDateTime.now());
        reg.setAttendanceMarkedBy(admin);
        return RegistrationDto.from(registrationRepository.save(reg));
    }

    // ─────────────────────────────────────── Private

    private void assertPublishedAndOpen(Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED && event.getStatus() != EventStatus.ONGOING) {
            throw new BadRequestException("This event is not currently open for registration.");
        }
        if (event.getRegistrationDeadline() != null
                && LocalDateTime.now().isAfter(event.getRegistrationDeadline())) {
            throw new BadRequestException("The registration deadline for this event has passed.");
        }
    }

    private void assertCapacityAvailable(Event event) {
        if (event.getCapacity() == null) return;
        long active = eventRepository.countActiveRegistrations(event.getId());
        if (active >= event.getCapacity()) {
            throw new BadRequestException("This event has reached full capacity.");
        }
    }

    private void assertApprovedMember(User user) {
        boolean isApproved = memberProfileRepository.findByUser(user)
                .map(p -> p.getMemberId() != null)
                .orElse(false);
        if (!isApproved) {
            throw new ForbiddenException(
                    "This event is open to approved Ushirika members only.");
        }
    }

    private void sendConfirmationEmail(String email, String name, Event event, String ref) {
        emailService.sendPlain(email, name,
                "Event Registration Confirmed — " + event.getTitle(),
                "Dear " + name.split(" ")[0] + ",\n\n" +
                "Your registration for \"" + event.getTitle() + "\" is confirmed.\n\n" +
                "Date: " + event.getStartDateTime().toLocalDate() + "\n" +
                "Time: " + event.getStartDateTime().toLocalTime() + "\n" +
                (event.getVenue() != null ? "Venue: " + event.getVenue() + "\n" : "") +
                (event.getOnlineLink() != null ? "Online link: " + event.getOnlineLink() + "\n" : "") +
                "\nYour check-in reference code: " + ref + "\n\n" +
                "We look forward to seeing you there.\n\n" +
                "Ushirika Welfare Organization"
        );
    }

    private long countActive(Event event) {
        return eventRepository.countActiveRegistrations(event.getId());
    }

    private Event findEventById(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }

    private String generateReferenceCode() {
        return "EVT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
