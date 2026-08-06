package com.mdau.ushirika.module.report.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.attendance.entity.AttendanceRecord;
import com.mdau.ushirika.module.attendance.entity.Fine;
import com.mdau.ushirika.module.attendance.entity.Meeting;
import com.mdau.ushirika.module.attendance.enums.FineStatus;
import com.mdau.ushirika.module.attendance.enums.MeetingStatus;
import com.mdau.ushirika.module.attendance.repository.AttendanceRecordRepository;
import com.mdau.ushirika.module.attendance.repository.FineRepository;
import com.mdau.ushirika.module.attendance.repository.MeetingRepository;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.dues.entity.MembershipDue;
import com.mdau.ushirika.module.dues.enums.DuesStatus;
import com.mdau.ushirika.module.dues.repository.MembershipDueRepository;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.mgr.entity.MgrContribution;
import com.mdau.ushirika.module.mgr.repository.MgrContributionRepository;
import com.mdau.ushirika.module.report.dto.*;
import com.mdau.ushirika.module.report.util.CsvBuilder;
import com.mdau.ushirika.module.report.util.PdfBuilder;
import com.mdau.ushirika.module.report.util.TableBuilder;
import com.mdau.ushirika.module.report.util.XlsxBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository             userRepository;
    private final MemberProfileRepository    profileRepository;
    private final MembershipDueRepository    dueRepository;
    private final FineRepository             fineRepository;
    private final MeetingRepository          meetingRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final MgrContributionRepository  contributionRepository;

    // ── Members ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] membersCsv() { var t = CsvBuilder.create(); populateMembersTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] membersXlsx() { var t = XlsxBuilder.create("Members"); populateMembersTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] membersPdf() { var t = PdfBuilder.create("Members Report"); populateMembersTable(t); return t.toBytes(); }

    private void populateMembersTable(TableBuilder table) {
        List<User> members = userRepository.findAllByRole(UserRole.MEMBER);
        Map<UUID, String> memberIds = profileRepository.findAll()
                .stream()
                .filter(p -> p.getMemberId() != null)
                .collect(Collectors.toMap(p -> p.getUser().getId(), MemberProfile::getMemberId));

        table.header("Member ID", "First Name", "Last Name", "Email", "Phone",
                "Role", "Active", "Membership Ceased", "Joined");

        for (User u : members) {
            table.col(memberIds.getOrDefault(u.getId(), ""))
                 .col(u.getFirstName())
                 .col(u.getLastName())
                 .col(u.getEmail())
                 .col(u.getPhone())
                 .col(u.getRole())
                 .col(u.isActive())
                 .col(u.isMembershipCeased())
                 .col(u.getCreatedAt() != null ? u.getCreatedAt().toLocalDate() : "")
                 .newRow();
        }
    }

    // ── Dues ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] duesCsv(Integer year) { var t = CsvBuilder.create(); populateDuesTable(t, year); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] duesXlsx(Integer year) { var t = XlsxBuilder.create("Dues"); populateDuesTable(t, year); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] duesPdf(Integer year) { var t = PdfBuilder.create("Dues Report"); populateDuesTable(t, year); return t.toBytes(); }

    private void populateDuesTable(TableBuilder table, Integer year) {
        List<MembershipDue> dues;
        if (year != null) {
            dues = dueRepository.findAll(Sort.by("user.lastName", "year"))
                    .stream()
                    .filter(d -> d.getYear() == year)
                    .toList();
        } else {
            dues = dueRepository.findAll(Sort.by("year").descending().and(Sort.by("user.lastName")));
        }

        Map<UUID, String> memberIds = profileRepository.findAll()
                .stream()
                .filter(p -> p.getMemberId() != null)
                .collect(Collectors.toMap(p -> p.getUser().getId(), MemberProfile::getMemberId));

        table.header("Member ID", "Name", "Email", "Year", "Amount",
                "Due Date", "Status", "Paid At", "Payment Method", "Payment Reference");

        for (MembershipDue d : dues) {
            User u = d.getUser();
            table.col(memberIds.getOrDefault(u.getId(), ""))
                 .col(u.getFullName())
                 .col(u.getEmail())
                 .col(d.getYear())
                 .col(d.getAmount())
                 .col(d.getDueDate())
                 .col(d.getStatus())
                 .col(d.getPaidAt() != null ? d.getPaidAt().toLocalDate() : "")
                 .col(d.getPaymentMethod() != null ? d.getPaymentMethod() : "")
                 .col(d.getPaymentReference() != null ? d.getPaymentReference() : "")
                 .newRow();
        }
    }

    // ── Fines ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] finesCsv() { var t = CsvBuilder.create(); populateFinesTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] finesXlsx() { var t = XlsxBuilder.create("Fines"); populateFinesTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] finesPdf() { var t = PdfBuilder.create("Fines Report"); populateFinesTable(t); return t.toBytes(); }

    private void populateFinesTable(TableBuilder table) {
        List<Fine> fines = fineRepository.findAll(Sort.by("createdAt").descending());

        Map<UUID, String> memberIds = profileRepository.findAll()
                .stream()
                .filter(p -> p.getMemberId() != null)
                .collect(Collectors.toMap(p -> p.getUser().getId(), MemberProfile::getMemberId));

        table.header("Member ID", "Name", "Email", "Reason", "Amount",
                "Due Date", "Status", "Paid At", "Related Meeting", "Waived Reason");

        for (Fine f : fines) {
            User u = f.getUser();
            table.col(memberIds.getOrDefault(u.getId(), ""))
                 .col(u.getFullName())
                 .col(u.getEmail())
                 .col(f.getReason())
                 .col(f.getAmount())
                 .col(f.getDueDate())
                 .col(f.getStatus())
                 .col(f.getPaidAt() != null ? f.getPaidAt().toLocalDate() : "")
                 .col(f.getMeeting() != null ? f.getMeeting().getTitle() : "")
                 .col(f.getWaivedReason() != null ? f.getWaivedReason() : "")
                 .newRow();
        }
    }

    // ── Attendance compliance ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AttendanceComplianceReport attendanceReport() {
        List<Meeting> meetings = meetingRepository.findAllByStatusOrderByMeetingDateAsc(MeetingStatus.COMPLETED);
        List<User> members     = userRepository.findAllByRole(UserRole.MEMBER);

        // Build lookup: userId → (meetingId → status)
        List<AttendanceRecord> allRecords = attendanceRepository.findAll();
        Map<UUID, Map<UUID, String>> recordMap = new HashMap<>();
        for (AttendanceRecord r : allRecords) {
            recordMap
                .computeIfAbsent(r.getUser().getId(), k -> new HashMap<>())
                .put(r.getMeeting().getId(), r.getStatus().name());
        }

        Map<UUID, String> memberIds = profileRepository.findAll()
                .stream()
                .filter(p -> p.getMemberId() != null)
                .collect(Collectors.toMap(p -> p.getUser().getId(), MemberProfile::getMemberId));

        List<AttendanceMeetingHeader> headers = meetings.stream()
                .map(m -> new AttendanceMeetingHeader(m.getId(), m.getTitle(), m.getMeetingDate(), m.getType()))
                .toList();

        List<AttendanceComplianceRow> rows = members.stream()
                .sorted(Comparator.comparing(User::getLastName))
                .map(u -> {
                    Map<UUID, String> userRecords = recordMap.getOrDefault(u.getId(), Map.of());
                    Map<String, String> statuses = new LinkedHashMap<>();
                    for (Meeting m : meetings) {
                        statuses.put(m.getId().toString(),
                                userRecords.getOrDefault(m.getId(), "NOT_RECORDED"));
                    }
                    return new AttendanceComplianceRow(
                            u.getId(), u.getFullName(),
                            memberIds.getOrDefault(u.getId(), ""),
                            u.getEmail(), statuses);
                })
                .toList();

        return new AttendanceComplianceReport(headers, rows, members.size(), meetings.size());
    }

    @Transactional(readOnly = true)
    public byte[] attendanceCsv() { var t = CsvBuilder.create(); populateAttendanceTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] attendanceXlsx() { var t = XlsxBuilder.create("Attendance"); populateAttendanceTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] attendancePdf() { var t = PdfBuilder.create("Attendance Compliance Report"); populateAttendanceTable(t); return t.toBytes(); }

    private void populateAttendanceTable(TableBuilder table) {
        AttendanceComplianceReport report = attendanceReport();

        List<String> headers = new ArrayList<>(List.of("Member ID", "Name", "Email"));
        for (AttendanceMeetingHeader m : report.meetings()) {
            headers.add(m.title() + " (" + m.meetingDate().toLocalDate() + ")");
        }
        table.header(headers.toArray(new String[0]));

        for (AttendanceComplianceRow row : report.rows()) {
            table.col(row.memberId()).col(row.memberName()).col(row.email());
            for (AttendanceMeetingHeader m : report.meetings()) {
                table.col(row.statuses().getOrDefault(m.id().toString(), "NOT_RECORDED"));
            }
            table.newRow();
        }
    }

    // ── MGR contributions ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] mgrCsv() { var t = CsvBuilder.create(); populateMgrTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] mgrXlsx() { var t = XlsxBuilder.create("MGR Contributions"); populateMgrTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] mgrPdf() { var t = PdfBuilder.create("MGR Contributions Report"); populateMgrTable(t); return t.toBytes(); }

    private void populateMgrTable(TableBuilder table) {
        List<MgrContribution> all = contributionRepository.findAll(
                Sort.by("cycle.startDate").descending()
                    .and(Sort.by("contributionMonth"))
                    .and(Sort.by("slot.slotNumber")));

        Map<UUID, String> memberIds = profileRepository.findAll()
                .stream()
                .filter(p -> p.getMemberId() != null)
                .collect(Collectors.toMap(p -> p.getUser().getId(), MemberProfile::getMemberId));

        table.header("Cycle", "Slot #", "Member ID", "Name", "Email",
                "Month", "Amount", "Status", "Paid At", "Payment Method", "Payment Reference");

        for (MgrContribution c : all) {
            User u = c.getSlot().getUser();
            table.col(c.getCycle().getName())
                 .col(c.getSlot().getSlotNumber())
                 .col(memberIds.getOrDefault(u.getId(), ""))
                 .col(u.getFullName())
                 .col(u.getEmail())
                 .col(c.getContributionMonth())
                 .col(c.getAmount())
                 .col(c.getStatus())
                 .col(c.getPaidAt() != null ? c.getPaidAt().toLocalDate() : "")
                 .col(c.getPaymentMethod() != null ? c.getPaymentMethod() : "")
                 .col(c.getPaymentReference() != null ? c.getPaymentReference() : "")
                 .newRow();
        }
    }

    // ── Receipts (member) ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DueReceiptDto dueReceipt(UUID dueId) {
        User user = currentUser();
        MembershipDue due = dueRepository.findById(dueId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));

        if (!due.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Access denied.");
        }
        if (due.getStatus() != DuesStatus.PAID) {
            throw new BadRequestException("Receipt is only available for paid dues.");
        }

        String memberId = profileRepository.findByUser(user)
                .map(MemberProfile::getMemberId).orElse("");

        return new DueReceiptDto(
                due.getId(),
                "RCP-DUES-" + due.getId().toString().substring(0, 8).toUpperCase(),
                user.getFullName(),
                memberId,
                user.getEmail(),
                due.getYear(),
                due.getAmount(),
                due.getDueDate(),
                due.getPaidAt(),
                due.getPaymentMethod(),
                due.getPaymentReference()
        );
    }

    @Transactional(readOnly = true)
    public FineReceiptDto fineReceipt(UUID fineId) {
        User user = currentUser();
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new ResourceNotFoundException("Fine not found."));

        if (!fine.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Access denied.");
        }
        if (fine.getStatus() != FineStatus.PAID) {
            throw new BadRequestException("Receipt is only available for paid fines.");
        }

        String memberId = profileRepository.findByUser(user)
                .map(MemberProfile::getMemberId).orElse("");

        return new FineReceiptDto(
                fine.getId(),
                "RCP-FINE-" + fine.getId().toString().substring(0, 8).toUpperCase(),
                user.getFullName(),
                memberId,
                user.getEmail(),
                fine.getReason(),
                fine.getAmount(),
                fine.getDueDate(),
                fine.getPaidAt(),
                fine.getMeeting() != null ? fine.getMeeting().getTitle() : null
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
