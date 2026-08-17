package com.mdau.ushirika.module.mgr.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.mgr.dto.*;
import com.mdau.ushirika.module.mgr.entity.*;
import com.mdau.ushirika.module.mgr.enums.*;
import com.mdau.ushirika.module.mgr.repository.*;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.NotificationCategory;
import com.mdau.ushirika.module.notification.service.NotificationDispatcher;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.enums.ProgramType;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import com.mdau.ushirika.module.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MgrService {

    private final MgrCycleRepository cycleRepo;
    private final MgrSlotRepository slotRepo;
    private final MgrContributionRepository contributionRepo;
    private final MgrJoinRequestRepository joinRequestRepo;
    private final MemberProfileRepository profileRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final NotificationDispatcher notificationDispatcher;
    private final ProgramRepository programRepo;
    private final ProgramAdminAssignmentRepository programAdminAssignmentRepo;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    // ── Cycles ────────────────────────────────────────────────────────────────

    @Transactional
    public MgrCycleDto createCycle(CreateCycleRequest req) {
        // Creating a cycle re-invites every WAITLISTED member and overwrites whatever they were
        // last asked about (see askWaitlistForNewCycle) -- that's fine when it's the only cycle
        // currently recruiting, but a second DRAFT cycle would silently wipe out responses
        // already collected for the first one before an admin ever gets to activate it.
        cycleRepo.findFirstByStatusOrderByStartDateDesc(CycleStatus.DRAFT).ifPresent(existing -> {
            throw new BadRequestException("A cycle (\"" + existing.getName() + "\") is already in DRAFT and " +
                    "collecting waitlist responses. Activate or cancel it before creating another -- creating a " +
                    "second DRAFT cycle would overwrite everyone's responses to the first one.");
        });

        MgrCycle cycle = MgrCycle.builder()
                .name(req.name())
                .year(req.year())
                .startDate(req.startDate())
                .endDate(req.startDate().plusMonths(11).withDayOfMonth(
                        req.startDate().plusMonths(11).lengthOfMonth()))
                .totalSlots(req.totalSlots() != null ? req.totalSlots() : 24)
                .monthlyContribution(req.monthlyContribution() != null
                        ? req.monthlyContribution() : new BigDecimal("100.00"))
                .payoutsPerMonth(req.payoutsPerMonth() != null ? req.payoutsPerMonth() : 2)
                .payoutAmountPerSlot(req.payoutAmountPerSlot() != null
                        ? req.payoutAmountPerSlot() : new BigDecimal("1200.00"))
                .reservePercentage(req.reservePercentage() != null
                        ? req.reservePercentage() : BigDecimal.ZERO)
                .benefitPayoutDay(req.benefitPayoutDay() != null ? req.benefitPayoutDay() : 15)
                .notes(req.notes())
                .build();
        cycleRepo.save(cycle);
        log.info("MGR cycle created: id={} name={}", cycle.getId(), cycle.getName());
        askWaitlistForNewCycle(cycle);
        return MgrCycleDto.summary(cycle, 0, 0);
    }

    /** Automatically asks every currently WAITLISTED member whether they want to join this newly
     * created cycle or keep waiting -- overwrites any prior invite, since a fresh cycle supersedes
     * whatever they were last asked about. Response happens in the member's portal; see
     * respondToCycleInvite and admitWaitlistedMembers. */
    private void askWaitlistForNewCycle(MgrCycle cycle) {
        List<MgrJoinRequest> waitlisted = joinRequestRepo.findByStatusOrderByCreatedAtAsc(JoinRequestStatus.WAITLISTED);
        for (MgrJoinRequest request : waitlisted) {
            request.setInvitedCycle(cycle);
            request.setInvitedAt(LocalDateTime.now());
            request.setCycleOptIn(null);
            request.setCycleRespondedAt(null);
            joinRequestRepo.save(request);
            sendCycleInviteEmail(request, cycle);
        }
        if (!waitlisted.isEmpty()) {
            log.info("MGR cycle invite sent to {} waitlisted member(s) for cycle {}", waitlisted.size(), cycle.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<MgrCycleDto> listCycles() {
        return cycleRepo.findAllByOrderByYearDescStartDateDesc().stream()
                .map(c -> {
                    int assigned = slotRepo.countByCycle(c);
                    long paid = slotRepo.countByCycleAndStatus(c, SlotStatus.PAID);
                    return MgrCycleDto.summary(c, assigned, paid);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public MgrCycleDto getCycle(UUID id) {
        return toFullDto(findCycle(id));
    }

    @Transactional
    public MgrCycleDto updateCycle(UUID id, CreateCycleRequest req) {
        MgrCycle cycle = findCycle(id);
        if (cycle.getStatus() != CycleStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT cycles can be updated.");
        }
        cycle.setName(req.name());
        cycle.setYear(req.year());
        cycle.setStartDate(req.startDate());
        cycle.setEndDate(req.startDate().plusMonths(11).withDayOfMonth(
                req.startDate().plusMonths(11).lengthOfMonth()));
        if (req.totalSlots()          != null) cycle.setTotalSlots(req.totalSlots());
        if (req.monthlyContribution() != null) cycle.setMonthlyContribution(req.monthlyContribution());
        if (req.payoutsPerMonth()     != null) cycle.setPayoutsPerMonth(req.payoutsPerMonth());
        if (req.payoutAmountPerSlot() != null) cycle.setPayoutAmountPerSlot(req.payoutAmountPerSlot());
        if (req.reservePercentage()   != null) cycle.setReservePercentage(req.reservePercentage());
        if (req.benefitPayoutDay()    != null) cycle.setBenefitPayoutDay(req.benefitPayoutDay());
        if (req.notes()               != null) cycle.setNotes(req.notes());
        cycleRepo.save(cycle);
        return toFullDto(cycle);
    }

    // ── Activate / complete / cancel ──────────────────────────────────────────

    @Transactional
    public MgrCycleDto activateCycle(UUID id) {
        MgrCycle cycle = findCycle(id);
        if (cycle.getStatus() != CycleStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT cycles can be activated.");
        }

        // Sweep the WAITLISTED queue (FCFS by application date) into whatever capacity remains
        // beyond members already manually assigned pre-activation, before checking "any members
        // at all" -- this is the moment applications submitted at any time (even while another
        // cycle was running) finally land a real seat.
        List<MgrSlot> admittedFromWaitlist = admitWaitlistedMembers(cycle);

        List<MgrSlot> slots = slotRepo.findByCycleOrderBySlotNumber(cycle);
        if (slots.isEmpty()) {
            throw new BadRequestException("Add at least one approved member before activating.");
        }

        // Generate contribution records: all slots × 12 months
        List<MgrContribution> contributions = new ArrayList<>();
        for (MgrSlot slot : slots) {
            for (int month = 1; month <= 12; month++) {
                contributions.add(MgrContribution.builder()
                        .slot(slot)
                        .cycle(cycle)
                        .contributionMonth(month)
                        .amount(cycle.getMonthlyContribution())
                        .build());
            }
        }
        contributionRepo.saveAll(contributions);

        cycle.setStatus(CycleStatus.ACTIVE);
        cycle.setActivatedAt(LocalDateTime.now());
        cycleRepo.save(cycle);

        log.info("MGR cycle activated: id={} slots={} admittedFromWaitlist={}",
                id, slots.size(), admittedFromWaitlist.size());
        notifyAllSlotMembers(cycle, slots, "MGR Cycle Activated — " + cycle.getName(),
                "Your Merry-Go-Round cycle is now active! Log in to your portal to see your contribution schedule.");

        return toFullDto(cycle);
    }

    /**
     * Admits only WAITLISTED members who explicitly opted in to THIS cycle when asked (see
     * askWaitlistForNewCycle/respondToCycleInvite), oldest opt-in first, up to whatever capacity
     * remains after any members already manually assigned pre-activation. Anyone who said "keep
     * waiting," never responded, or was invited to a different cycle stays WAITLISTED -- they're
     * picked up by the next cycle's automated ask instead of being swept in blind.
     */
    private List<MgrSlot> admitWaitlistedMembers(MgrCycle cycle) {
        int existing = slotRepo.countByCycle(cycle);
        int capacity = cycle.getTotalSlots() - existing;
        if (capacity <= 0) {
            return List.of();
        }

        List<MgrJoinRequest> optedIn = joinRequestRepo.findByStatusOrderByCreatedAtAsc(JoinRequestStatus.WAITLISTED)
                .stream()
                .filter(r -> r.getInvitedCycle() != null && r.getInvitedCycle().getId().equals(cycle.getId())
                        && Boolean.TRUE.equals(r.getCycleOptIn()))
                .toList();
        List<MgrJoinRequest> toAdmit = optedIn.subList(0, Math.min(capacity, optedIn.size()));

        List<MgrSlot> newSlots = new ArrayList<>();
        int slotNumber = slotRepo.findMaxSlotNumberByCycle(cycle);
        for (MgrJoinRequest request : toAdmit) {
            slotNumber++;
            MgrSlot slot = MgrSlot.builder()
                    .cycle(cycle)
                    .user(request.getUser())
                    .slotNumber(slotNumber)
                    .build();
            slotRepo.save(slot);
            newSlots.add(slot);

            request.setStatus(JoinRequestStatus.ADMITTED);
            request.setCycle(cycle);
            request.setAdmittedAt(LocalDateTime.now());
            joinRequestRepo.save(request);

            sendAdmittedEmail(request);
        }

        if (!toAdmit.isEmpty()) {
            log.info("MGR waitlist swept into cycle {}: admitted={} optedInNotFit={}",
                    cycle.getId(), toAdmit.size(), optedIn.size() - toAdmit.size());
        }
        return newSlots;
    }

    @Transactional
    public MgrCycleDto completeCycle(UUID id) {
        MgrCycle cycle = findCycle(id);
        if (cycle.getStatus() != CycleStatus.ACTIVE) {
            throw new BadRequestException("Only ACTIVE cycles can be completed.");
        }
        cycle.setStatus(CycleStatus.COMPLETED);
        cycle.setCompletedAt(LocalDateTime.now());
        cycleRepo.save(cycle);
        return toFullDto(cycle);
    }

    @Transactional
    public MgrCycleDto cancelCycle(UUID id) {
        MgrCycle cycle = findCycle(id);
        // Only DRAFT is safe to cancel through this simple path -- an ACTIVE cycle already has
        // real contributions collected and possibly payouts made. Cancelling it here would leave
        // those PAID/DRAWN/SCHEDULED slots and PENDING contributions dangling under a CANCELLED
        // cycle with zero notification to enrolled members. Unwinding a live-money cycle needs a
        // deliberate, hands-on process, not a confirm-dialog button.
        if (cycle.getStatus() != CycleStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT cycles can be cancelled this way. " +
                    "An ACTIVE cycle already has real money moving -- contact the developer to unwind it safely.");
        }
        cycle.setStatus(CycleStatus.CANCELLED);
        cycleRepo.save(cycle);

        // Members invited to (or who opted into) this cycle would otherwise be stuck showing
        // "you're enrolled once it activates" for a cycle that never will. Clear the invite so
        // they fall back to plain WAITLISTED and pick up the next real cycle's ask instead.
        List<MgrJoinRequest> invited = joinRequestRepo.findByInvitedCycle(cycle);
        for (MgrJoinRequest request : invited) {
            request.setInvitedCycle(null);
            request.setInvitedAt(null);
            request.setCycleOptIn(null);
            request.setCycleRespondedAt(null);
            joinRequestRepo.save(request);
        }
        log.info("MGR cycle cancelled: id={} — cleared invite state for {} member(s)", cycle.getId(), invited.size());

        return toFullDto(cycle);
    }

    // ── Join Requests ─────────────────────────────────────────────────────────
    // Applications are accepted at any time, independent of whether any cycle is DRAFT or
    // ACTIVE -- there is no cycle-status gate here at all. A request only ever becomes tied to a
    // specific cycle once ADMITTED (see admitWaitlistedMembers above).

    private static final Set<JoinRequestStatus> OPEN_JOIN_REQUEST_STATUSES =
            Set.of(JoinRequestStatus.PENDING, JoinRequestStatus.FORM_SENT, JoinRequestStatus.WAITLISTED);

    @Transactional
    public MgrJoinRequestDto requestJoin(String memberNotes) {
        User member = currentUser();
        if (!member.isActive()) {
            throw new BadRequestException("Only active members may apply to join MGR.");
        }
        if (joinRequestRepo.existsByUserAndStatusIn(member, OPEN_JOIN_REQUEST_STATUSES)) {
            throw new ConflictException("You already have a pending or waitlisted MGR join request.");
        }
        if (slotRepo.existsByUserAndCycleStatus(member, CycleStatus.ACTIVE)) {
            throw new ConflictException("You are already enrolled in the currently active MGR cycle.");
        }

        MgrJoinRequest request = MgrJoinRequest.builder()
                .user(member)
                .memberNotes(memberNotes)
                .build();
        joinRequestRepo.save(request);

        log.info("MGR join request submitted: member={}", member.getEmail());
        return MgrJoinRequestDto.from(request, memberId(member));
    }

    @Transactional(readOnly = true)
    public List<MgrJoinRequestDto> getMyJoinRequests() {
        User member = currentUser();
        return joinRequestRepo.findByUserOrderByCreatedAtDesc(member)
                .stream()
                .map(r -> MgrJoinRequestDto.from(r, memberId(member)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MgrJoinRequestDto> listJoinRequests(JoinRequestStatus status) {
        List<MgrJoinRequest> requests = status != null
                ? joinRequestRepo.findByStatusOrderByCreatedAtDesc(status)
                : joinRequestRepo.findAllByOrderByCreatedAtDesc();
        return requests.stream()
                .map(r -> MgrJoinRequestDto.from(r, memberId(r.getUser())))
                .toList();
    }

    /** Coordinator sends the applicant an info form explaining how MGR works -- moves PENDING to
     * FORM_SENT. No payment involved; the member just needs to confirm in their portal that they
     * want to join the waitlist (see confirmJoinWaitlist below). */
    @Transactional
    public MgrJoinRequestDto sendForm(UUID requestId, String adminNotes) {
        User admin = currentUser();
        requireMgrCoordinatorAccess(admin);
        MgrJoinRequest request = findJoinRequest(requestId);

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new BadRequestException("Only PENDING requests can have the form sent.");
        }

        request.setStatus(JoinRequestStatus.FORM_SENT);
        request.setAdminNotes(adminNotes);
        request.setFormSentBy(admin);
        request.setFormSentAt(LocalDateTime.now());
        joinRequestRepo.save(request);

        log.info("MGR join-request form sent: id={} member={} by={}",
                requestId, request.getUser().getEmail(), admin.getEmail());
        sendMgrFormEmail(request);
        return MgrJoinRequestDto.from(request, memberId(request.getUser()));
    }

    /** Member confirms they want to join the waitlist after reviewing the form -- moves FORM_SENT
     * to WAITLISTED. Does not create a slot; they're automatically asked about each new cycle as
     * it's created (see askWaitlistForNewCycle) and swept in at activation if they opt in. */
    @Transactional
    public MgrJoinRequestDto confirmJoinWaitlist(UUID requestId) {
        User member = currentUser();
        MgrJoinRequest request = findJoinRequest(requestId);

        if (!request.getUser().getId().equals(member.getId())) {
            throw new BadRequestException("You can only confirm your own MGR join request.");
        }
        if (request.getStatus() != JoinRequestStatus.FORM_SENT) {
            throw new BadRequestException("This request isn't awaiting your confirmation.");
        }

        request.setStatus(JoinRequestStatus.WAITLISTED);
        request.setRespondedAt(LocalDateTime.now());
        joinRequestRepo.save(request);

        log.info("MGR join request confirmed by member: id={} member={}", requestId, member.getEmail());
        sendWaitlistedEmail(request);

        // A cycle may already be sitting in DRAFT waiting for members -- give this brand-new
        // waitlist entry the same invite everyone else already got instead of making them wait
        // for the next cycle to be created.
        cycleRepo.findFirstByStatusOrderByStartDateDesc(CycleStatus.DRAFT).ifPresent(draft -> {
            request.setInvitedCycle(draft);
            request.setInvitedAt(LocalDateTime.now());
            request.setCycleOptIn(null);
            request.setCycleRespondedAt(null);
            joinRequestRepo.save(request);
            sendCycleInviteEmail(request, draft);
        });

        return MgrJoinRequestDto.from(request, memberId(request.getUser()));
    }

    /** Member responds to the automated "join this cycle or keep waiting?" ask. No response by
     * the time the invited cycle activates defaults to "keep waiting" (see admitWaitlistedMembers). */
    @Transactional
    public MgrJoinRequestDto respondToCycleInvite(UUID requestId, boolean joining) {
        User member = currentUser();
        MgrJoinRequest request = findJoinRequest(requestId);

        if (!request.getUser().getId().equals(member.getId())) {
            throw new BadRequestException("You can only respond to your own MGR cycle invite.");
        }
        if (request.getStatus() != JoinRequestStatus.WAITLISTED || request.getInvitedCycle() == null) {
            throw new BadRequestException("You don't have an open cycle invite to respond to right now.");
        }

        request.setCycleOptIn(joining);
        request.setCycleRespondedAt(LocalDateTime.now());
        joinRequestRepo.save(request);

        log.info("MGR cycle invite response: id={} member={} joining={}", requestId, member.getEmail(), joining);
        return MgrJoinRequestDto.from(request, memberId(request.getUser()));
    }

    @Transactional
    public MgrJoinRequestDto rejectJoinRequest(UUID requestId, String adminNotes) {
        User admin = currentUser();
        requireMgrCoordinatorAccess(admin);
        MgrJoinRequest request = findJoinRequest(requestId);

        if (request.getStatus() != JoinRequestStatus.PENDING
                && request.getStatus() != JoinRequestStatus.FORM_SENT
                && request.getStatus() != JoinRequestStatus.WAITLISTED) {
            throw new BadRequestException("Only PENDING, FORM_SENT, or WAITLISTED requests can be rejected.");
        }

        request.setStatus(JoinRequestStatus.REJECTED);
        request.setAdminNotes(adminNotes);
        request.setRespondedBy(admin);
        request.setRespondedAt(LocalDateTime.now());
        joinRequestRepo.save(request);

        log.info("MGR join request rejected: id={} member={}", requestId, request.getUser().getEmail());
        sendRejectedJoinEmail(request, adminNotes);
        return MgrJoinRequestDto.from(request, memberId(request.getUser()));
    }

    // ── Monthly Draw ──────────────────────────────────────────────────────────

    /**
     * Admin triggers the monthly beneficiary draw.
     * Randomly selects payoutsPerMonth SCHEDULED+undrawn members and assigns them
     * the given payout month. Sends email notification to all cycle members.
     */
    @Transactional
    public List<MgrSlotDto> runMonthlyDraw(UUID cycleId, int month, Integer drawYear) {
        User admin = currentUser();
        MgrCycle cycle = findCycle(cycleId);

        if (cycle.getStatus() != CycleStatus.ACTIVE) {
            throw new BadRequestException("Monthly draws can only run on ACTIVE cycles.");
        }

        // Guard: month must not already have drawn beneficiaries
        List<MgrSlot> alreadyDrawn = slotRepo.findByCycleAndPayoutMonth(cycle, month);
        if (!alreadyDrawn.isEmpty()) {
            throw new ConflictException("Month " + month + " has already been drawn (" +
                    alreadyDrawn.size() + " beneficiaries selected). Cannot draw again.");
        }

        List<MgrSlot> undrawn = slotRepo.findUndrawnByCycle(cycle, SlotStatus.SCHEDULED);
        if (undrawn.isEmpty()) {
            throw new BadRequestException("No remaining members to draw — all have been paid this cycle.");
        }

        // Only draw from members who are current on *this month's* contribution -- being behind
        // on payment shouldn't be rewarded with a payout draw.
        Set<UUID> currentSlotIds = contributionRepo.findByCycleAndContributionMonthOrderBySlotSlotNumber(cycle, month)
                .stream()
                .filter(c -> c.getStatus() == ContributionStatus.PAID || c.getStatus() == ContributionStatus.WAIVED)
                .map(c -> c.getSlot().getId())
                .collect(Collectors.toSet());
        List<MgrSlot> eligible = undrawn.stream()
                .filter(s -> currentSlotIds.contains(s.getId()))
                .collect(Collectors.toList());
        if (eligible.isEmpty()) {
            throw new BadRequestException("No members are current on month " + month +
                    "'s contribution yet — nobody is eligible for this draw.");
        }

        int drawCount = Math.min(cycle.getPayoutsPerMonth(), eligible.size());

        // Shuffle and pick
        Collections.shuffle(eligible, new Random());
        List<MgrSlot> drawn = eligible.subList(0, drawCount);

        int year = drawYear != null ? drawYear : cycle.getStartDate().getYear();
        LocalDate payoutDate = LocalDate.of(year, month, Math.min(cycle.getBenefitPayoutDay(),
                LocalDate.of(year, month, 1).lengthOfMonth()));

        for (int i = 0; i < drawn.size(); i++) {
            MgrSlot slot = drawn.get(i);
            slot.setPayoutMonth(month);
            slot.setPayoutOrder(i + 1);
            slot.setScheduledPayoutDate(payoutDate);
            slot.setPayoutAmount(cycle.getPayoutAmountPerSlot());
            slot.setStatus(SlotStatus.DRAWN);
            slot.setDrawnAt(LocalDateTime.now());
            slotRepo.save(slot);
        }

        log.info("MGR monthly draw: cycleId={} month={} drawn={} by={}", cycleId, month, drawCount, admin.getEmail());

        // Notify all cycle members
        List<MgrSlot> allSlots = slotRepo.findByCycleOrderBySlotNumber(cycle);
        String beneficiaryNames = drawn.stream()
                .map(s -> s.getUser().getFullName())
                .collect(Collectors.joining(" & "));

        notifyMonthlyDraw(cycle, allSlots, drawn, month, payoutDate);

        return drawn.stream()
                .map(s -> MgrSlotDto.publicView(s, memberId(s.getUser()), photoUrl(s.getUser())))
                .toList();
    }

    // ── Current month's beneficiaries (portal animation data) ─────────────────

    @Transactional(readOnly = true)
    public List<MgrSlotDto> getCurrentBeneficiaries(UUID cycleId, int month) {
        MgrCycle cycle = findCycle(cycleId);
        return slotRepo.findByCycleAndPayoutMonth(cycle, month)
                .stream()
                .map(s -> MgrSlotDto.publicView(s, memberId(s.getUser()), photoUrl(s.getUser())))
                .toList();
    }

    // ── All members in cycle (for portal animation name pool) ─────────────────

    @Transactional(readOnly = true)
    public List<MgrSlotDto> getCycleMembers(UUID cycleId) {
        MgrCycle cycle = findCycle(cycleId);
        User viewer = currentUser();
        // Verify they're in this cycle
        slotRepo.findByCycleAndUser(cycle, viewer)
                .orElseThrow(() -> new BadRequestException("You are not a member of this cycle."));
        return slotRepo.findByCycleOrderBySlotNumber(cycle)
                .stream()
                .map(s -> MgrSlotDto.publicView(s, memberId(s.getUser()), photoUrl(s.getUser())))
                .toList();
    }

    // ── Payout confirmation (admin) ───────────────────────────────────────────

    @Transactional
    public MgrSlotDto recordPayout(UUID slotId, RecordPayoutRequest req) {
        MgrSlot slot = findSlot(slotId);
        if (slot.getCycle().getStatus() != CycleStatus.ACTIVE) {
            throw new BadRequestException("Payouts can only be recorded for ACTIVE cycles.");
        }
        if (slot.getStatus() == SlotStatus.SCHEDULED) {
            throw new BadRequestException("This member has not been drawn as a beneficiary yet.");
        }
        if (slot.getStatus() == SlotStatus.PAID) {
            throw new BadRequestException("This slot has already been paid out.");
        }

        slot.setStatus(SlotStatus.PAID);
        slot.setPaidAt(LocalDateTime.now());
        slot.setPaymentReference(req.paymentReference());
        slot.setAdminNotes(req.adminNotes());
        slotRepo.save(slot);

        log.info("MGR payout recorded: slotId={} member={}", slotId, slot.getUser().getEmail());
        sendPayoutNotification(slot);

        // Auto-complete cycle if all drawn slots are paid
        long remaining = slotRepo.countByCycleAndStatus(slot.getCycle(), SlotStatus.DRAWN);
        if (remaining == 0) {
            long scheduled = slotRepo.countByCycleAndStatus(slot.getCycle(), SlotStatus.SCHEDULED);
            if (scheduled == 0) {
                slot.getCycle().setStatus(CycleStatus.COMPLETED);
                slot.getCycle().setCompletedAt(LocalDateTime.now());
                cycleRepo.save(slot.getCycle());
            }
        }

        return MgrSlotDto.from(slot, memberId(slot.getUser()), photoUrl(slot.getUser()),
                contributionRepo.findBySlotOrderByContributionMonth(slot)
                        .stream().map(MgrContributionDto::from).toList());
    }

    // ── Receipt confirmation (member) ─────────────────────────────────────────

    @Transactional
    public MgrSlotDto confirmReceipt(UUID slotId, String notes) {
        User member = currentUser();
        MgrSlot slot = findSlot(slotId);

        if (!slot.getUser().getId().equals(member.getId())) {
            throw new BadRequestException("You can only confirm receipt for your own payout.");
        }
        if (slot.getStatus() != SlotStatus.PAID) {
            throw new BadRequestException("Payout must be recorded by admin before you can confirm receipt.");
        }
        if (slot.isReceiptConfirmed()) {
            throw new ConflictException("You have already confirmed receipt for this payout.");
        }

        slot.setReceiptConfirmed(true);
        slot.setReceiptConfirmedAt(LocalDateTime.now());
        slot.setReceiptNotes(notes);
        slotRepo.save(slot);

        log.info("MGR receipt confirmed: slotId={} member={}", slotId, member.getEmail());

        return MgrSlotDto.from(slot, memberId(member), photoUrl(member),
                contributionRepo.findBySlotOrderByContributionMonth(slot)
                        .stream().map(MgrContributionDto::from).toList());
    }

    // ── Slots (admin manual assignment kept for flexibility) ──────────────────

    @Transactional
    public MgrSlotDto assignSlot(UUID cycleId, AssignSlotRequest req) {
        MgrCycle cycle = findCycle(cycleId);
        if (cycle.getStatus() != CycleStatus.DRAFT) {
            throw new BadRequestException("Slots can only be assigned to DRAFT cycles.");
        }
        User user = userRepo.findById(req.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + req.userId()));
        if (slotRepo.existsByCycleAndUser(cycle, user)) {
            throw new ConflictException("This member already has a slot in this cycle.");
        }
        int slotNumber = req.slotNumber() != null
                ? req.slotNumber()
                : slotRepo.findMaxSlotNumberByCycle(cycle) + 1;

        MgrSlot slot = MgrSlot.builder()
                .cycle(cycle)
                .user(user)
                .slotNumber(slotNumber)
                .build();
        slotRepo.save(slot);
        log.info("MGR slot manually assigned: cycleId={} user={} slot={}", cycleId, user.getEmail(), slotNumber);
        return MgrSlotDto.summary(slot, memberId(user), photoUrl(user));
    }

    @Transactional
    public void removeSlot(UUID slotId) {
        MgrSlot slot = findSlot(slotId);
        if (slot.getCycle().getStatus() != CycleStatus.DRAFT) {
            throw new BadRequestException("Slots can only be removed from DRAFT cycles.");
        }
        slotRepo.delete(slot);
    }

    // ── Contributions ─────────────────────────────────────────────────────────

    /** Sum of this member's PENDING contribution amounts for their current slot — 0 if not in an active cycle. */
    @Transactional(readOnly = true)
    public BigDecimal outstandingContributionBalance(User member) {
        return slotRepo.findByUserAndCycleStatus(member, CycleStatus.ACTIVE)
                .map(slot -> contributionRepo.findBySlotOrderByContributionMonth(slot).stream()
                        .filter(c -> c.getStatus() == ContributionStatus.PENDING)
                        .map(MgrContribution::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .orElse(BigDecimal.ZERO);
    }

    /** Credits a confirmed payment toward this member's MGR contributions. Applies sequentially
     * to the earliest PENDING months for their current slot — whole months only, since
     * contribution amounts are fixed. Returns whatever didn't cover a full month so the caller
     * (PaymentAllocationService) can keep it as member credit instead of losing it. */
    @Transactional
    public BigDecimal applyContribution(User member, BigDecimal amountUsd) {
        MgrSlot slot = slotRepo.findByUserAndCycleStatus(member, CycleStatus.ACTIVE).orElse(null);
        if (slot == null) {
            log.warn("Payment tried to credit MGR contribution for {} but they have no active slot", member.getEmail());
            return amountUsd;
        }
        List<MgrContribution> pending = contributionRepo.findBySlotOrderByContributionMonth(slot).stream()
                .filter(c -> c.getStatus() == ContributionStatus.PENDING)
                .toList();

        BigDecimal remaining = amountUsd;
        for (MgrContribution c : pending) {
            if (remaining.compareTo(c.getAmount()) < 0) break;
            c.setPaymentMethod("STRIPE");
            c.setPaidAt(LocalDateTime.now());
            c.setStatus(ContributionStatus.PAID);
            contributionRepo.save(c);
            remaining = remaining.subtract(c.getAmount());
        }
        return remaining;
    }

    @Transactional
    public MgrContributionDto waiveContribution(UUID contributionId, String reason) {
        MgrContribution contribution = contributionRepo.findById(contributionId)
                .orElseThrow(() -> new ResourceNotFoundException("Contribution not found: " + contributionId));
        if (contribution.getStatus() == ContributionStatus.PAID) {
            throw new BadRequestException("Cannot waive a PAID contribution.");
        }
        contribution.setStatus(ContributionStatus.WAIVED);
        contribution.setNotes(reason);
        contributionRepo.save(contribution);
        return MgrContributionDto.from(contribution);
    }

    @Transactional(readOnly = true)
    public List<MgrContributionDto> getMonthContributions(UUID cycleId, int month) {
        MgrCycle cycle = findCycle(cycleId);
        return contributionRepo
                .findByCycleAndContributionMonthOrderBySlotSlotNumber(cycle, month)
                .stream()
                .map(MgrContributionDto::from)
                .toList();
    }

    // ── Member self-service ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MgrSlotDto getMySlot(UUID cycleId) {
        User user = currentUser();
        MgrCycle cycle = cycleId != null ? findCycle(cycleId)
                : cycleRepo.findFirstByStatusOrderByStartDateDesc(CycleStatus.ACTIVE)
                        .orElseThrow(() -> new ResourceNotFoundException("No active MGR cycle found."));

        MgrSlot slot = slotRepo.findByCycleAndUser(cycle, user)
                .orElseThrow(() -> new ResourceNotFoundException("You are not enrolled in this MGR cycle."));

        List<MgrContributionDto> contributions = contributionRepo
                .findBySlotOrderByContributionMonth(slot).stream()
                .map(MgrContributionDto::from).toList();
        return MgrSlotDto.from(slot, memberId(user), photoUrl(user), contributions);
    }

    // ── Public info ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<MgrCycleDto> getActiveCyclePublicInfo() {
        return cycleRepo.findFirstByStatusOrderByStartDateDesc(CycleStatus.ACTIVE)
                .map(c -> {
                    int assigned = slotRepo.countByCycle(c);
                    long paid = slotRepo.countByCycleAndStatus(c, SlotStatus.PAID);
                    return MgrCycleDto.summary(c, assigned, paid);
                });
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private void notifyAllSlotMembers(MgrCycle cycle, List<MgrSlot> slots, String subject, String body) {
        String portalUrl = siteUrl + "/portal/mgr";
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#007834">%s</h2>
              <p>%s</p>
              <p><a href="%s" style="display:inline-block;background:#007834;color:#fff;
                 padding:10px 22px;border-radius:999px;text-decoration:none;font-weight:600">
                 Go to My MGR Portal
              </a></p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>""".formatted(subject, body, portalUrl);

        for (MgrSlot slot : slots) {
            emailService.sendPlain(slot.getUser().getEmail(), slot.getUser().getFullName(), subject, html);
        }
    }

    private void notifyMonthlyDraw(MgrCycle cycle, List<MgrSlot> allSlots,
                                    List<MgrSlot> drawn, int month, LocalDate payoutDate) {
        String subject = "MGR Benefit Draw — " + month + "/" + cycle.getYear() + " — " + cycle.getName();
        String names = drawn.stream().map(s -> s.getUser().getFullName()).collect(Collectors.joining(" & "));
        String portalUrl = siteUrl + "/portal/mgr";
        String amount = drawn.isEmpty() ? "" : "$" + drawn.get(0).getPayoutAmount().toPlainString();

        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#007834">This Month's MGR Beneficiaries Have Been Selected!</h2>
              <p>The monthly Merry-Go-Round draw for <strong>%s</strong> has been completed.</p>
              <p style="font-size:18px;font-weight:600;color:#007834">%s</p>
              <p>Each beneficiary will receive <strong>%s</strong> on or around
                 <strong>%s</strong>. Beneficiaries must confirm receipt in the portal once paid.</p>
              <p>Visit your member portal to see the full animation reveal:</p>
              <p><a href="%s" style="display:inline-block;background:#007834;color:#fff;
                 padding:10px 22px;border-radius:999px;text-decoration:none;font-weight:600">
                 See the Reveal in Portal
              </a></p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>""".formatted(cycle.getName(), names, amount, payoutDate, portalUrl);

        for (MgrSlot slot : allSlots) {
            emailService.sendPlain(slot.getUser().getEmail(), slot.getUser().getFullName(), subject, html);
        }
    }

    private void sendPayoutNotification(MgrSlot slot) {
        String name  = slot.getUser().getFullName();
        String email = slot.getUser().getEmail();
        String portal = siteUrl + "/portal/mgr";
        String amount = "$" + slot.getPayoutAmount().toPlainString();
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#007834">Your MGR Payout Has Been Sent!</h2>
              <p>Hi %s,</p>
              <p>Your Merry-Go-Round payout of <strong>%s</strong> has been disbursed
                 via <strong>%s</strong> (reference: %s).</p>
              <p>Please confirm receipt in your member portal so the cycle records stay accurate.</p>
              <p><a href="%s" style="display:inline-block;background:#007834;color:#fff;
                 padding:10px 22px;border-radius:999px;text-decoration:none;font-weight:600">
                 Confirm Receipt
              </a></p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>""".formatted(name, amount,
                slot.getPaymentReference() != null ? "" : "the configured payment method",
                slot.getPaymentReference() != null ? slot.getPaymentReference() : "N/A",
                portal);
        emailService.sendPlain(email, name, "Your MGR Payout Has Been Disbursed — Ushirika Welfare Organization", html);
    }

    /** Sent when the coordinator sends the applicant the info form (PENDING -> FORM_SENT).
     * Explains how MGR works in plain terms since, unlike Benevolence, there's no payment here --
     * just an informed decision to join the waitlist. */
    private void sendMgrFormEmail(MgrJoinRequest r) {
        String name = r.getUser().getFullName();
        String portal = siteUrl + "/portal/mgr";
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto;padding:24px">
              <h2 style="color:#007834">Your MGR Application is Ready to Complete</h2>
              <p>Hi %s,</p>
              <p>A program coordinator has reviewed your request to join the Merry-Go-Round (MGR)
                 program. Here's how it works before you confirm:</p>
              <ol style="line-height:1.7">
                <li>Each cycle runs 12 months. Members contribute a fixed amount every month.</li>
                <li>Every month, a set number of members are drawn at random to receive a
                    lump-sum payout — everyone gets their turn by the time the cycle ends.</li>
                <li>There's nothing to pay right now. Confirming just puts you on the waitlist.</li>
              </ol>
              <p>Once you confirm in your portal, you're on the waitlist. Whenever a new cycle is
                 about to open, we'll email you (and show it in your portal) asking whether you
                 want to join that cycle or keep waiting for a later one — your choice, every time.</p>
              <p>
                <a href="%s"
                   style="display:inline-block;background:#007834;color:#fff;padding:10px 22px;
                          border-radius:999px;text-decoration:none;font-weight:600">
                  Confirm &amp; Join the Waitlist &rarr;
                </a>
              </p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>
            """.formatted(name, portal);
        try {
            emailService.sendPlain(r.getUser().getEmail(), name,
                    "Your Ushirika MGR Application is Ready to Complete", html);
        } catch (Exception e) {
            log.warn("MGR form-sent email failed for {}: {}", r.getUser().getEmail(), e.getMessage());
        }

        notificationDispatcher.dispatchWhatsApp(NotificationCategory.PROGRAM_ACTION_REQUIRED,
                r.getUser().getPhone(), name, List.of(
                        name,
                        "Your MGR application is ready — confirm in your portal to join the waitlist. No payment needed.",
                        portal
                ));
    }

    /** Sent when the member confirms and lands on the waitlist (FORM_SENT -> WAITLISTED). */
    private void sendWaitlistedEmail(MgrJoinRequest r) {
        String name = r.getUser().getFullName();
        String portal = siteUrl + "/portal/mgr";
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#007834">You're on the MGR Waitlist!</h2>
              <p>Hi %s,</p>
              <p>You've confirmed your Merry-Go-Round application and you're now on the waitlist,
                 first-come-first-served by application date.</p>
              <p>Whenever a new cycle is created, we'll automatically email you (and show it in
                 your portal) asking whether you want to join that cycle or keep waiting for a
                 later one. If a cycle is already open for new members right now, look out for
                 that ask shortly.</p>
              <p><a href="%s" style="display:inline-block;background:#007834;color:#fff;
                 padding:10px 22px;border-radius:999px;text-decoration:none;font-weight:600">
                 Go to MGR Portal
              </a></p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>""".formatted(name, portal);
        emailService.sendPlain(r.getUser().getEmail(), name, "You're on the MGR Waitlist — Ushirika Welfare Organization", html);
    }

    /** Sent whenever a new cycle is created and this WAITLISTED member is asked whether they want
     * to join it or keep waiting -- the automated per-cycle opt-in ask. */
    private void sendCycleInviteEmail(MgrJoinRequest r, MgrCycle cycle) {
        String name = r.getUser().getFullName();
        String portal = siteUrl + "/portal/mgr";
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#007834">A New MGR Cycle Is Opening — Want In?</h2>
              <p>Hi %s,</p>
              <p><strong>%s</strong> is opening up, with a monthly contribution of
                 <strong>$%s</strong> and a payout of <strong>$%s</strong> per beneficiary.</p>
              <p>You're on the MGR waitlist — do you want to join this cycle, or keep waiting for a
                 future one? Let us know in your portal.</p>
              <p>If we don't hear from you before this cycle fills up and activates, you'll simply
                 stay on the waitlist and get asked again about the next one — no action needed if
                 you'd rather wait.</p>
              <p><a href="%s" style="display:inline-block;background:#007834;color:#fff;
                 padding:10px 22px;border-radius:999px;text-decoration:none;font-weight:600">
                 Respond in My Portal
              </a></p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>""".formatted(name, cycle.getName(), cycle.getMonthlyContribution(),
                cycle.getPayoutAmountPerSlot(), portal);
        try {
            emailService.sendPlain(r.getUser().getEmail(), name,
                    "Join " + cycle.getName() + "? — Ushirika MGR", html);
        } catch (Exception e) {
            log.warn("MGR cycle-invite email failed for {}: {}", r.getUser().getEmail(), e.getMessage());
        }

        notificationDispatcher.dispatchWhatsApp(NotificationCategory.PROGRAM_ACTION_REQUIRED,
                r.getUser().getPhone(), name, List.of(
                        name,
                        cycle.getName() + " is opening — respond in your portal to join this cycle or keep waiting.",
                        portal
                ));
    }

    /** Sent when a waitlisted member is actually swept into a real cycle at that cycle's activation. */
    private void sendAdmittedEmail(MgrJoinRequest r) {
        String name = r.getUser().getFullName();
        String cycleName = r.getCycle().getName();
        String portal = siteUrl + "/portal/mgr";
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#007834">You're In! Enrolled in %s</h2>
              <p>Hi %s,</p>
              <p>Great news — you've been enrolled in <strong>%s</strong>. Your contribution
                 schedule is now live in your member portal.</p>
              <p><a href="%s" style="display:inline-block;background:#007834;color:#fff;
                 padding:10px 22px;border-radius:999px;text-decoration:none;font-weight:600">
                 Go to MGR Portal
              </a></p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>""".formatted(cycleName, name, cycleName, portal);
        emailService.sendPlain(r.getUser().getEmail(), name, "You're Enrolled — " + cycleName, html);
    }

    private void sendRejectedJoinEmail(MgrJoinRequest r, String reason) {
        String name = r.getUser().getFullName();
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#B91C1C">MGR Join Request Not Approved</h2>
              <p>Hi %s,</p>
              <p>Your request to join the Merry-Go-Round program was not approved at this time.</p>
              %s
              <p>You are welcome to submit a new request at any time.
                 Contact <a href="mailto:info@ushirikacommunity.site">info@ushirikacommunity.site</a>
                 if you have questions.</p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>""".formatted(name,
                reason != null ? "<p><strong>Reason:</strong> " + reason + "</p>" : "");
        emailService.sendPlain(r.getUser().getEmail(), name, "MGR Join Request Update — Ushirika Welfare Organization", html);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MgrCycleDto toFullDto(MgrCycle cycle) {
        List<MgrSlot> slots = slotRepo.findByCycleOrderBySlotNumber(cycle);
        int assigned = slots.size();
        long paidPayouts = slots.stream().filter(s -> s.getStatus() == SlotStatus.PAID).count();
        long pendingContribs = contributionRepo.countByCycleAndStatus(cycle, ContributionStatus.PENDING);

        List<MgrSlotDto> slotDtos = slots.stream()
                .map(s -> {
                    List<MgrContributionDto> contribs = contributionRepo
                            .findBySlotOrderByContributionMonth(s).stream()
                            .map(MgrContributionDto::from).toList();
                    return MgrSlotDto.from(s, memberId(s.getUser()), photoUrl(s.getUser()), contribs);
                })
                .toList();

        return MgrCycleDto.from(cycle, assigned, paidPayouts, pendingContribs, slotDtos);
    }

    private String memberId(User user) {
        return profileRepo.findByUser(user).map(MemberProfile::getMemberId).orElse(null);
    }

    private String photoUrl(User user) {
        return profileRepo.findByUser(user).map(MemberProfile::getPhotoUrl).orElse(null);
    }

    private MgrCycle findCycle(UUID id) {
        return cycleRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MGR cycle not found: " + id));
    }

    private MgrSlot findSlot(UUID id) {
        return slotRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MGR slot not found: " + id));
    }

    private MgrJoinRequest findJoinRequest(UUID id) {
        return joinRequestRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MGR join request not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    /**
     * Approving/rejecting a join request is deciding MGR program membership — the client requires
     * only the MGR program's assigned coordinator can do this, not a general ADMIN. The /admin/mgr/**
     * path is open to any ADMIN at the security-filter level, so this service-level check is what
     * actually enforces the restriction. SUPERADMIN keeps a break-glass override.
     */
    private void requireMgrCoordinatorAccess(User user) {
        if (user.getRole() == UserRole.SUPERADMIN) {
            return;
        }
        List<Program> mgrPrograms = programRepo.findAllByType(ProgramType.MGR);
        boolean isAssignedCoordinator = mgrPrograms.stream()
                .anyMatch(p -> programAdminAssignmentRepo.existsByProgramIdAndUserId(p.getId(), user.getId()));
        if (!isAssignedCoordinator) {
            throw new ForbiddenException("Only the MGR program's coordinator can decide join requests.");
        }
    }
}
