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
    private final ProgramRepository programRepo;
    private final ProgramAdminAssignmentRepository programAdminAssignmentRepo;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    // ── Cycles ────────────────────────────────────────────────────────────────

    @Transactional
    public MgrCycleDto createCycle(CreateCycleRequest req) {
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
        return MgrCycleDto.summary(cycle, 0, 0);
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

    @Transactional
    public MgrCycleDto toggleEnrollment(UUID id) {
        MgrCycle cycle = findCycle(id);
        if (cycle.getStatus() != CycleStatus.DRAFT) {
            throw new BadRequestException("Enrollment can only be toggled for DRAFT cycles.");
        }
        cycle.setEnrollmentOpen(!cycle.isEnrollmentOpen());
        cycleRepo.save(cycle);
        log.info("MGR cycle enrollment toggled: id={} open={}", id, cycle.isEnrollmentOpen());
        return toFullDto(cycle);
    }

    // ── Activate / complete / cancel ──────────────────────────────────────────

    @Transactional
    public MgrCycleDto activateCycle(UUID id) {
        MgrCycle cycle = findCycle(id);
        if (cycle.getStatus() != CycleStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT cycles can be activated.");
        }
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
        cycle.setEnrollmentOpen(false);
        cycle.setActivatedAt(LocalDateTime.now());
        cycleRepo.save(cycle);

        log.info("MGR cycle activated: id={} slots={}", id, slots.size());
        notifyAllSlotMembers(cycle, slots, "MGR Cycle Activated — " + cycle.getName(),
                "Your Merry-Go-Round cycle is now active! Log in to your portal to see your contribution schedule.");

        return toFullDto(cycle);
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
        if (cycle.getStatus() == CycleStatus.COMPLETED) {
            throw new BadRequestException("Completed cycles cannot be cancelled.");
        }
        cycle.setStatus(CycleStatus.CANCELLED);
        cycleRepo.save(cycle);
        return toFullDto(cycle);
    }

    // ── Join Requests ─────────────────────────────────────────────────────────

    @Transactional
    public MgrJoinRequestDto requestJoin(UUID cycleId, String memberNotes) {
        User member = currentUser();
        MgrCycle cycle = findCycle(cycleId);

        if (!cycle.isEnrollmentOpen()) {
            throw new BadRequestException("Enrollment for this MGR cycle is currently closed.");
        }
        if (cycle.getStatus() != CycleStatus.DRAFT) {
            throw new BadRequestException("Join requests can only be submitted for cycles that are open for enrollment.");
        }
        if (joinRequestRepo.existsByCycleAndUser(cycle, member)) {
            throw new ConflictException("You have already submitted a join request for this cycle.");
        }
        if (slotRepo.existsByCycleAndUser(cycle, member)) {
            throw new ConflictException("You are already enrolled in this cycle.");
        }
        int currentCount = slotRepo.countByCycle(cycle);
        if (currentCount >= cycle.getTotalSlots()) {
            throw new BadRequestException("This cycle is full (" + cycle.getTotalSlots() + " members). Check for a future cycle.");
        }

        MgrJoinRequest request = MgrJoinRequest.builder()
                .cycle(cycle)
                .user(member)
                .memberNotes(memberNotes)
                .build();
        joinRequestRepo.save(request);

        log.info("MGR join request submitted: cycleId={} member={}", cycleId, member.getEmail());
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
    public List<MgrJoinRequestDto> listJoinRequests(UUID cycleId, JoinRequestStatus status) {
        MgrCycle cycle = findCycle(cycleId);
        List<MgrJoinRequest> requests = status != null
                ? joinRequestRepo.findByCycleAndStatusOrderByCreatedAtDesc(cycle, status)
                : joinRequestRepo.findByCycleOrderByCreatedAtDesc(cycle);
        return requests.stream()
                .map(r -> MgrJoinRequestDto.from(r, memberId(r.getUser())))
                .toList();
    }

    @Transactional
    public MgrJoinRequestDto approveJoinRequest(UUID requestId, String adminNotes) {
        User admin = currentUser();
        requireMgrCoordinatorAccess(admin);
        MgrJoinRequest request = findJoinRequest(requestId);

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new BadRequestException("Only PENDING requests can be approved.");
        }
        MgrCycle cycle = request.getCycle();
        if (cycle.getStatus() != CycleStatus.DRAFT) {
            throw new BadRequestException("Cannot approve requests for a non-DRAFT cycle.");
        }
        int currentCount = slotRepo.countByCycle(cycle);
        if (currentCount >= cycle.getTotalSlots()) {
            throw new BadRequestException("Cycle is full — cannot approve more members.");
        }

        // Create slot (sequential number, no payout month yet — assigned at monthly draw)
        MgrSlot slot = MgrSlot.builder()
                .cycle(cycle)
                .user(request.getUser())
                .slotNumber(currentCount + 1)
                .build();
        slotRepo.save(slot);

        request.setStatus(JoinRequestStatus.APPROVED);
        request.setAdminNotes(adminNotes);
        request.setRespondedBy(admin);
        request.setRespondedAt(LocalDateTime.now());
        joinRequestRepo.save(request);

        log.info("MGR join request approved: id={} member={} slot={}", requestId,
                request.getUser().getEmail(), slot.getSlotNumber());

        sendApprovedJoinEmail(request);
        return MgrJoinRequestDto.from(request, memberId(request.getUser()));
    }

    @Transactional
    public MgrJoinRequestDto rejectJoinRequest(UUID requestId, String adminNotes) {
        User admin = currentUser();
        requireMgrCoordinatorAccess(admin);
        MgrJoinRequest request = findJoinRequest(requestId);

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new BadRequestException("Only PENDING requests can be rejected.");
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

        int drawCount = Math.min(cycle.getPayoutsPerMonth(), undrawn.size());

        // Shuffle and pick
        Collections.shuffle(undrawn, new Random());
        List<MgrSlot> drawn = undrawn.subList(0, drawCount);

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
                : slotRepo.countByCycle(cycle) + 1;

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
        return slotRepo.findByUser(member)
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
        MgrSlot slot = slotRepo.findByUser(member).orElse(null);
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

    private void sendApprovedJoinEmail(MgrJoinRequest r) {
        String name = r.getUser().getFullName();
        String portal = siteUrl + "/portal/mgr";
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#007834">You're In! MGR Join Request Approved</h2>
              <p>Hi %s,</p>
              <p>Your request to join <strong>%s</strong> has been approved.
                 You have been enrolled in the cycle.</p>
              <p>Once the cycle is activated, you will receive a notification with your
                 contribution schedule. Log in to your portal to track your status.</p>
              <p><a href="%s" style="display:inline-block;background:#007834;color:#fff;
                 padding:10px 22px;border-radius:999px;text-decoration:none;font-weight:600">
                 Go to MGR Portal
              </a></p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>""".formatted(name, r.getCycle().getName(), portal);
        emailService.sendPlain(r.getUser().getEmail(), name,
                "MGR Join Request Approved — " + r.getCycle().getName(), html);
    }

    private void sendRejectedJoinEmail(MgrJoinRequest r, String reason) {
        String name = r.getUser().getFullName();
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#B91C1C">MGR Join Request Not Approved</h2>
              <p>Hi %s,</p>
              <p>Your request to join <strong>%s</strong> was not approved at this time.</p>
              %s
              <p>You are welcome to request to join a future cycle.
                 Contact <a href="mailto:info@ushirikacommunity.site">info@ushirikacommunity.site</a>
                 if you have questions.</p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>""".formatted(name, r.getCycle().getName(),
                reason != null ? "<p><strong>Reason:</strong> " + reason + "</p>" : "");
        emailService.sendPlain(r.getUser().getEmail(), name,
                "MGR Join Request Update — " + r.getCycle().getName(), html);
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
