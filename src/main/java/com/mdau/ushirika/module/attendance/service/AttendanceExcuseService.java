package com.mdau.ushirika.module.attendance.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.attendance.dto.AttendanceExcuseDto;
import com.mdau.ushirika.module.attendance.dto.SubmitExcuseRequest;
import com.mdau.ushirika.module.attendance.entity.AttendanceExcuse;
import com.mdau.ushirika.module.attendance.entity.AttendanceRecord;
import com.mdau.ushirika.module.attendance.entity.Meeting;
import com.mdau.ushirika.module.attendance.enums.AttendanceStatus;
import com.mdau.ushirika.module.attendance.enums.ExcuseStatus;
import com.mdau.ushirika.module.attendance.enums.FineStatus;
import com.mdau.ushirika.module.attendance.repository.AttendanceExcuseRepository;
import com.mdau.ushirika.module.attendance.repository.AttendanceRecordRepository;
import com.mdau.ushirika.module.attendance.repository.MeetingRepository;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.notification.enums.InAppNotificationCategory;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceExcuseService {

    private final AttendanceExcuseRepository excuseRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final MemberProfileRepository profileRepository;
    private final FineService fineService;
    private final InAppNotificationService notificationService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    // ── Member ────────────────────────────────────────────────────────────────

    @Transactional
    public AttendanceExcuseDto submitExcuse(UUID meetingId, SubmitExcuseRequest req) {
        User user = currentUser();
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found: " + meetingId));
        AttendanceRecord record = attendanceRecordRepository.findByMeetingAndUser(meeting, user)
                .orElseThrow(() -> new BadRequestException("No attendance record found for this meeting."));

        if (record.getStatus() != AttendanceStatus.LATE && record.getStatus() != AttendanceStatus.ABSENT) {
            throw new BadRequestException("Only late or absent meetings can be excused.");
        }
        if (excuseRepository.existsByAttendanceRecordAndStatus(record, ExcuseStatus.PENDING)) {
            throw new BadRequestException("You already have a pending apology for this meeting.");
        }

        AttendanceExcuse excuse = AttendanceExcuse.builder()
                .attendanceRecord(record)
                .reason(req.reason())
                .evidenceUrl(req.evidenceUrl())
                .build();
        AttendanceExcuse saved = excuseRepository.save(excuse);

        String body = user.getFullName() + " submitted an apology for \"" + meeting.getTitle()
                + "\" (" + record.getStatus().name().toLowerCase() + ").";
        for (User leader : userRepository.findAllByRoleIn(List.of(UserRole.SUPERADMIN, UserRole.ADMIN, UserRole.LEADERSHIP))) {
            notificationService.createForUser(leader.getId(), InAppNotificationCategory.ATTENDANCE_WARNING,
                    "New Attendance Apology", body, "/admin/meetings");
        }

        return AttendanceExcuseDto.from(saved, memberIdOf(user.getId()));
    }

    @Transactional(readOnly = true)
    public List<AttendanceExcuseDto> myExcuses() {
        User user = currentUser();
        String memberId = memberIdOf(user.getId());
        return excuseRepository.findByAttendanceRecord_User_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(e -> AttendanceExcuseDto.from(e, memberId))
                .toList();
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AttendanceExcuseDto> listExcuses(String status, Pageable pageable) {
        Map<UUID, String> memberIds = buildMemberIdMap();
        if (status != null && !status.isBlank()) {
            ExcuseStatus s = ExcuseStatus.valueOf(status.toUpperCase());
            return excuseRepository.findByStatusOrderByCreatedAtDesc(s, pageable)
                    .map(e -> AttendanceExcuseDto.from(e, memberIds.get(e.getAttendanceRecord().getUser().getId())));
        }
        return excuseRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(e -> AttendanceExcuseDto.from(e, memberIds.get(e.getAttendanceRecord().getUser().getId())));
    }

    @Transactional
    public AttendanceExcuseDto decide(UUID id, boolean approve, String adminNotes) {
        User admin = currentUser();
        AttendanceExcuse excuse = excuseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Apology not found: " + id));
        if (excuse.getStatus() != ExcuseStatus.PENDING) {
            throw new BadRequestException("Only pending apologies can be reviewed.");
        }

        excuse.setStatus(approve ? ExcuseStatus.APPROVED : ExcuseStatus.REJECTED);
        excuse.setAdminNotes(adminNotes);
        excuse.setDecidedBy(admin);
        excuse.setDecidedAt(LocalDateTime.now());

        AttendanceRecord record = excuse.getAttendanceRecord();
        User member = record.getUser();

        if (approve) {
            record.setStatus(AttendanceStatus.EXCUSED);
            if (record.getFine() != null && record.getFine().getStatus() == FineStatus.PENDING) {
                fineService.waiveFine(record.getFine().getId(), "Apology approved — " + excuse.getReason());
            }
            attendanceRecordRepository.save(record);
        }
        excuseRepository.save(excuse);

        String subject = approve ? "Your attendance apology was approved" : "Your attendance apology was reviewed";
        String body = approve
                ? "Your apology for \"" + record.getMeeting().getTitle() + "\" has been approved. Any related fine has been waived."
                : "Your apology for \"" + record.getMeeting().getTitle() + "\" was not approved."
                    + (adminNotes != null && !adminNotes.isBlank() ? " Admin note: " + adminNotes : "");

        notificationService.createForUser(member.getId(), InAppNotificationCategory.ATTENDANCE_WARNING,
                subject, body, "/portal/meetings");
        try {
            emailService.sendPlain(member.getEmail(), member.getFullName(), subject, "<p>" + body + "</p>");
        } catch (Exception e) {
            log.warn("Excuse decision email failed for {}: {}", member.getEmail(), e.getMessage());
        }

        auditLogService.log(admin, approve ? "EXCUSE_APPROVED" : "EXCUSE_REJECTED", "AttendanceExcuse", id,
                (approve ? "Approved" : "Rejected") + " apology for " + member.getFullName()
                        + " — " + record.getMeeting().getTitle());

        return AttendanceExcuseDto.from(excuse, memberIdOf(member.getId()));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
