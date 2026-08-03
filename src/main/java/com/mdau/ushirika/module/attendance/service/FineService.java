package com.mdau.ushirika.module.attendance.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.attendance.dto.CreateFineRequest;
import com.mdau.ushirika.module.attendance.dto.FineDto;
import com.mdau.ushirika.module.attendance.entity.Fine;
import com.mdau.ushirika.module.attendance.entity.Meeting;
import com.mdau.ushirika.module.attendance.enums.FineStatus;
import com.mdau.ushirika.module.attendance.repository.FineRepository;
import com.mdau.ushirika.module.attendance.repository.MeetingRepository;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FineService {

    private final FineRepository fineRepository;
    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final MemberProfileRepository profileRepository;
    private final AuditLogService auditLogService;

    /**
     * Creates a fine automatically from attendance (late arrival or unexcused absence), bypassing
     * CreateFineRequest since there's no admin-entered reason/due-date here. dueDate defaults to
     * two weeks after the meeting. actor/auditAction let the caller attribute the audit trail
     * correctly (the checking-in member for a late fine, the admin who completed the meeting for
     * an absence fine).
     */
    @Transactional
    public Fine createAutoFine(User user, Meeting meeting, String reason, java.math.BigDecimal amount,
                                User actor, String auditAction) {
        Fine fine = Fine.builder()
                .user(user)
                .meeting(meeting)
                .reason(reason)
                .amount(amount)
                .dueDate(meeting.getMeetingDate().toLocalDate().plusDays(14))
                .build();
        Fine saved = fineRepository.save(fine);
        auditLogService.log(actor, auditAction, "Fine", saved.getId(),
                String.format("Auto-fine of $%.2f — %s", amount, reason));
        return saved;
    }

    @Transactional
    public FineDto createFine(CreateFineRequest req) {
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + req.userId()));

        Meeting meeting = null;
        if (req.meetingId() != null) {
            meeting = meetingRepository.findById(req.meetingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Meeting not found: " + req.meetingId()));
        }

        Fine fine = Fine.builder()
                .user(user)
                .meeting(meeting)
                .reason(req.reason())
                .amount(req.amount())
                .dueDate(req.dueDate())
                .build();

        FineDto dto = FineDto.from(fineRepository.save(fine), memberIdOf(user.getId()));
        auditLogService.log(currentUser(), "FINE_CREATED", "Fine", dto.id(),
                String.format("Fine of $%.2f issued to %s — %s", req.amount(), user.getFullName(), req.reason()));
        return dto;
    }

    @Transactional(readOnly = true)
    public Page<FineDto> listFines(String status, Pageable pageable) {
        Map<UUID, String> memberIds = buildMemberIdMap();
        if (status != null) {
            FineStatus fineStatus = FineStatus.valueOf(status.toUpperCase());
            return fineRepository.findByStatusOrderByCreatedAtDesc(fineStatus, pageable)
                    .map(f -> FineDto.from(f, memberIds.get(f.getUser().getId())));
        }
        return fineRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(f -> FineDto.from(f, memberIds.get(f.getUser().getId())));
    }

    @Transactional(readOnly = true)
    public List<FineDto> getMyFines() {
        User user = currentUser();
        Map<UUID, String> memberIds = buildMemberIdMap();
        return fineRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(f -> FineDto.from(f, memberIds.get(f.getUser().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FineDto> getFinesForMember(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        String memberId = memberIdOf(userId);
        return fineRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(f -> FineDto.from(f, memberId))
                .toList();
    }

    @Transactional
    public FineDto waiveFine(UUID id, String reason) {
        Fine fine = findFineById(id);
        if (fine.getStatus() != FineStatus.PENDING) {
            throw new BadRequestException("Only pending fines can be waived.");
        }
        fine.setStatus(FineStatus.WAIVED);
        fine.setWaivedReason(reason);
        FineDto dto = FineDto.from(fineRepository.save(fine), memberIdOf(fine.getUser().getId()));
        auditLogService.log(currentUser(), "FINE_WAIVED", "Fine", id,
                "Waived fine for " + fine.getUser().getFullName() + " — " + fine.getReason());
        return dto;
    }

    @Transactional
    public FineDto markPaid(UUID id) {
        Fine fine = findFineById(id);
        if (fine.getStatus() != FineStatus.PENDING) {
            throw new BadRequestException("Only pending fines can be marked as paid.");
        }
        fine.setStatus(FineStatus.PAID);
        fine.setPaidAt(LocalDateTime.now());
        return FineDto.from(fineRepository.save(fine), memberIdOf(fine.getUser().getId()));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Fine findFineById(UUID id) {
        return fineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fine not found: " + id));
    }

    private String memberIdOf(UUID userId) {
        return profileRepository.findAll().stream()
                .filter(p -> p.getUser() != null && userId.equals(p.getUser().getId()))
                .map(p -> p.getMemberId())
                .findFirst().orElse(null);
    }

    private Map<UUID, String> buildMemberIdMap() {
        Map<UUID, String> map = new HashMap<>();
        profileRepository.findAll().forEach(p -> {
            if (p.getUser() != null && p.getMemberId() != null) {
                map.put(p.getUser().getId(), p.getMemberId());
            }
        });
        return map;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }
}
