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
import com.mdau.ushirika.module.benevolence.entity.BenevolenceClaim;
import com.mdau.ushirika.module.benevolence.repository.BenevolenceClaimRepository;
import com.mdau.ushirika.module.dashboard.dto.FinanceDashboardDto;
import com.mdau.ushirika.module.dashboard.service.DashboardService;
import com.mdau.ushirika.module.dues.entity.MembershipDue;
import com.mdau.ushirika.module.dues.enums.DuesStatus;
import com.mdau.ushirika.module.dues.repository.MembershipDueRepository;
import com.mdau.ushirika.module.election.entity.Election;
import com.mdau.ushirika.module.election.entity.ElectionResult;
import com.mdau.ushirika.module.election.enums.ElectionStatus;
import com.mdau.ushirika.module.election.repository.ElectionRepository;
import com.mdau.ushirika.module.election.repository.ElectionResultRepository;
import com.mdau.ushirika.module.loan.entity.LoanApplication;
import com.mdau.ushirika.module.loan.repository.LoanApplicationRepository;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.mgr.entity.MgrContribution;
import com.mdau.ushirika.module.mgr.repository.MgrContributionRepository;
import com.mdau.ushirika.module.program.entity.ProgramApplication;
import com.mdau.ushirika.module.program.enums.ProgramType;
import com.mdau.ushirika.module.program.repository.ProgramApplicationRepository;
import com.mdau.ushirika.module.report.dto.*;
import com.mdau.ushirika.module.report.util.CsvBuilder;
import com.mdau.ushirika.module.report.util.PdfBuilder;
import com.mdau.ushirika.module.report.util.TableBuilder;
import com.mdau.ushirika.module.report.util.XlsxBuilder;
import com.mdau.ushirika.module.scholarship.entity.ScholarshipApplication;
import com.mdau.ushirika.module.scholarship.entity.ScholarshipAward;
import com.mdau.ushirika.module.scholarship.repository.ScholarshipApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final DashboardService           dashboardService;
    private final BenevolenceClaimRepository benevolenceClaimRepository;
    private final LoanApplicationRepository  loanApplicationRepository;
    private final ScholarshipApplicationRepository scholarshipApplicationRepository;
    private final ElectionRepository         electionRepository;
    private final ElectionResultRepository   electionResultRepository;
    private final ProgramApplicationRepository programApplicationRepository;
    private final com.mdau.ushirika.module.audit.repository.AuditLogRepository auditLogRepository;

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

    // ── Benevolence claims ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] benevolenceClaimsCsv() { var t = CsvBuilder.create(); populateBenevolenceClaimsTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] benevolenceClaimsXlsx() { var t = XlsxBuilder.create("Benevolence Claims"); populateBenevolenceClaimsTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] benevolenceClaimsPdf() { var t = PdfBuilder.create("Benevolence Claims Report"); populateBenevolenceClaimsTable(t); return t.toBytes(); }

    private void populateBenevolenceClaimsTable(TableBuilder table) {
        List<BenevolenceClaim> claims = benevolenceClaimRepository.findAll(Sort.by("submittedAt").descending());

        Map<UUID, String> memberIds = profileRepository.findAll()
                .stream()
                .filter(p -> p.getMemberId() != null)
                .collect(Collectors.toMap(p -> p.getUser().getId(), MemberProfile::getMemberId));

        table.header("Reference", "Member ID", "Name", "Email", "Deceased", "Relationship",
                "Category", "Date of Death", "Amount Approved", "Status", "Submitted", "Reviewed", "Disbursed");

        for (BenevolenceClaim c : claims) {
            User u = c.getEnrollment().getUser();
            table.col(c.getReferenceNumber())
                 .col(memberIds.getOrDefault(u.getId(), ""))
                 .col(u.getFullName())
                 .col(u.getEmail())
                 .col(c.getDeceasedName())
                 .col(c.getRelationship())
                 .col(c.getCategory() != null ? c.getCategory().getName() : "")
                 .col(c.getDateOfDeath())
                 .col(c.getAmountApproved())
                 .col(c.getStatus())
                 .col(c.getSubmittedAt() != null ? c.getSubmittedAt().toLocalDate() : "")
                 .col(c.getReviewedAt() != null ? c.getReviewedAt().toLocalDate() : "")
                 .col(c.getDisbursedAt() != null ? c.getDisbursedAt().toLocalDate() : "")
                 .newRow();
        }
    }

    // ── Loans ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] loansCsv() { var t = CsvBuilder.create(); populateLoansTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] loansXlsx() { var t = XlsxBuilder.create("Loans"); populateLoansTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] loansPdf() { var t = PdfBuilder.create("Loans Report"); populateLoansTable(t); return t.toBytes(); }

    private void populateLoansTable(TableBuilder table) {
        List<LoanApplication> loans = loanApplicationRepository.findAll(Sort.by("createdAt").descending());

        Map<UUID, String> memberIds = profileRepository.findAll()
                .stream()
                .filter(p -> p.getMemberId() != null)
                .collect(Collectors.toMap(p -> p.getUser().getId(), MemberProfile::getMemberId));

        table.header("Reference", "Member ID", "Name", "Email", "Purpose", "Requested Amount",
                "Approved Amount", "Term (months)", "Interest Rate", "Total Repayable", "Total Paid",
                "Outstanding", "Status", "Disbursed", "Due Date");

        for (LoanApplication l : loans) {
            User u = l.getUser();
            BigDecimal outstanding = l.getTotalRepayable() != null
                    ? l.getTotalRepayable().subtract(l.getTotalPaid())
                    : null;
            table.col(l.getReferenceNumber())
                 .col(memberIds.getOrDefault(u.getId(), ""))
                 .col(u.getFullName())
                 .col(u.getEmail())
                 .col(l.getPurpose())
                 .col(l.getRequestedAmount())
                 .col(l.getApprovedAmount())
                 .col(l.getTermMonths())
                 .col(l.getInterestRate())
                 .col(l.getTotalRepayable())
                 .col(l.getTotalPaid())
                 .col(outstanding)
                 .col(l.getStatus())
                 .col(l.getDisbursedAt())
                 .col(l.getDueDate())
                 .newRow();
        }
    }

    // ── Scholarships ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] scholarshipsCsv() { var t = CsvBuilder.create(); populateScholarshipsTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] scholarshipsXlsx() { var t = XlsxBuilder.create("Scholarships"); populateScholarshipsTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] scholarshipsPdf() { var t = PdfBuilder.create("Scholarships Report"); populateScholarshipsTable(t); return t.toBytes(); }

    private void populateScholarshipsTable(TableBuilder table) {
        List<ScholarshipApplication> apps = scholarshipApplicationRepository.findAll(Sort.by("createdAt").descending());

        Map<UUID, String> memberIds = profileRepository.findAll()
                .stream()
                .filter(p -> p.getMemberId() != null)
                .collect(Collectors.toMap(p -> p.getUser().getId(), MemberProfile::getMemberId));

        table.header("Reference", "Member ID", "Name", "Email", "Beneficiary", "Program",
                "Institution", "Course", "Academic Year", "Amount Awarded", "Status", "Submitted", "Awarded");

        for (ScholarshipApplication a : apps) {
            User u = a.getMember();
            ScholarshipAward award = a.getAward();
            table.col(a.getReferenceNumber())
                 .col(memberIds.getOrDefault(u.getId(), ""))
                 .col(u.getFullName())
                 .col(u.getEmail())
                 .col(a.getBeneficiaryName())
                 .col(a.getProgram() != null ? a.getProgram().getName() : "")
                 .col(a.getInstitutionName())
                 .col(a.getCourseOfStudy())
                 .col(a.getAcademicYear())
                 .col(award != null ? award.getAmountAwarded() : null)
                 .col(a.getStatus())
                 .col(a.getSubmittedAt() != null ? a.getSubmittedAt().toLocalDate() : "")
                 .col(award != null && award.getAwardedAt() != null ? award.getAwardedAt().toLocalDate() : "")
                 .newRow();
        }
    }

    // ── Elections ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] electionsCsv() { var t = CsvBuilder.create(); populateElectionsTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] electionsXlsx() { var t = XlsxBuilder.create("Election Results"); populateElectionsTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] electionsPdf() { var t = PdfBuilder.create("Election Results Report"); populateElectionsTable(t); return t.toBytes(); }

    /** Only COMPLETED elections have declared ElectionResult rows -- results are computed once
     * at declare-results time, not derived live from raw vote tallies. */
    private void populateElectionsTable(TableBuilder table) {
        List<Election> elections = electionRepository.findAllByOrderByYearDescCreatedAtDesc()
                .stream()
                .filter(e -> e.getStatus() == ElectionStatus.COMPLETED)
                .toList();

        table.header("Election", "Year", "Seat", "Candidate", "Member ID", "Votes", "Rank", "Winner");

        for (Election e : elections) {
            List<ElectionResult> results = electionResultRepository.findAllByElectionIdOrderBySeatTitleAscRankAsc(e.getId());
            for (ElectionResult r : results) {
                table.col(e.getTitle())
                     .col(e.getYear())
                     .col(r.getSeatTitle())
                     .col(r.getMemberName())
                     .col(r.getMemberId() != null ? r.getMemberId() : "")
                     .col(r.getVoteCount())
                     .col(r.getRank())
                     .col(r.isWinner())
                     .newRow();
            }
        }
    }

    // ── Program applications (non-MGR, non-Benevolence programs) ──────────────

    @Transactional(readOnly = true)
    public byte[] programApplicationsCsv() { var t = CsvBuilder.create(); populateProgramApplicationsTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] programApplicationsXlsx() { var t = XlsxBuilder.create("Program Applications"); populateProgramApplicationsTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] programApplicationsPdf() { var t = PdfBuilder.create("Program Applications Report"); populateProgramApplicationsTable(t); return t.toBytes(); }

    /** MGR and Benevolence each have their own dedicated join-request systems and reports
     * (MgrContribution / BenevolenceClaim above) -- restricted to CUSTOM programs here so this
     * doesn't overlap with those, even though the generic ProgramApplication path is technically
     * still reachable for BENEVOLENCE too. */
    private void populateProgramApplicationsTable(TableBuilder table) {
        List<ProgramApplication> apps = programApplicationRepository.findAll(Sort.by("appliedAt").descending())
                .stream()
                .filter(a -> a.getProgram().getType() == ProgramType.CUSTOM)
                .toList();

        Map<UUID, String> memberIds = profileRepository.findAll()
                .stream()
                .filter(p -> p.getMemberId() != null)
                .collect(Collectors.toMap(p -> p.getUser().getId(), MemberProfile::getMemberId));

        table.header("Program", "Member ID", "Name", "Email", "Status", "Applied", "Reviewed", "Reviewed By", "Rejection Reason");

        for (ProgramApplication a : apps) {
            User u = a.getApplicant();
            table.col(a.getProgram().getName())
                 .col(memberIds.getOrDefault(u.getId(), ""))
                 .col(u.getFullName())
                 .col(u.getEmail())
                 .col(a.getStatus())
                 .col(a.getAppliedAt() != null ? a.getAppliedAt().toLocalDate() : "")
                 .col(a.getReviewedAt() != null ? a.getReviewedAt().toLocalDate() : "")
                 .col(a.getReviewedBy() != null ? a.getReviewedBy().getFullName() : "")
                 .col(a.getRejectionReason() != null ? a.getRejectionReason() : "")
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

    // ── Finance summary ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] financeSummaryCsv(Integer year, int months) {
        var t = CsvBuilder.create(); populateFinanceSummaryTable(t, year, months); return t.toBytes();
    }

    @Transactional(readOnly = true)
    public byte[] financeSummaryXlsx(Integer year, int months) {
        var t = XlsxBuilder.create("Finance Summary"); populateFinanceSummaryTable(t, year, months); return t.toBytes();
    }

    @Transactional(readOnly = true)
    public byte[] financeSummaryPdf(Integer year, int months) {
        var t = PdfBuilder.create("Finance Summary Report"); populateFinanceSummaryTable(t, year, months); return t.toBytes();
    }

    private void populateFinanceSummaryTable(TableBuilder table, Integer year, int months) {
        int resolvedYear = year != null ? year : java.time.LocalDate.now().getYear();
        FinanceDashboardDto dto = dashboardService.getFinanceDashboard(resolvedYear, months);

        table.header("Section", "Metric", "Value");

        table.col("Dues (" + dto.dues().year() + ")").col("Total Billed").col(dto.dues().totalBilled().toString()).newRow();
        table.col("Dues (" + dto.dues().year() + ")").col("Total Collected").col(dto.dues().totalCollected().toString()).newRow();
        table.col("Dues (" + dto.dues().year() + ")").col("Collection Rate")
                .col(String.format("%.1f%%", dto.dues().collectionRatePercent())).newRow();

        table.col("Benevolence").col("Total Paid Out").col(dto.benevolence().totalPaidOut().toString()).newRow();
        table.col("Benevolence").col("Pending Claims").col(String.valueOf(dto.benevolence().pendingClaims())).newRow();

        table.col("Loans").col("Active (Disbursed/Repaying)").col(String.valueOf(dto.loans().activeCount())).newRow();
        table.col("Loans").col("Overdue").col(String.valueOf(dto.loans().overdueCount())).newRow();
        table.col("Loans").col("Defaulted").col(String.valueOf(dto.loans().defaultedCount())).newRow();
        table.col("Loans").col("Outstanding Principal").col(dto.loans().outstandingPrincipal().toString()).newRow();

        table.col("MGR").col("Total Contributed").col(dto.mgr().totalContributed().toString()).newRow();
    }

    // ── Money flow / ledger (Finance Visibility plan, Phase 5) ─────────────────

    @Transactional(readOnly = true)
    public byte[] moneyFlowCsv() { var t = CsvBuilder.create(); populateMoneyFlowTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] moneyFlowXlsx() { var t = XlsxBuilder.create("Money Flow"); populateMoneyFlowTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] moneyFlowPdf() { var t = PdfBuilder.create("Money Flow Report"); populateMoneyFlowTable(t); return t.toBytes(); }

    private void populateMoneyFlowTable(TableBuilder table) {
        List<com.mdau.ushirika.module.audit.entity.AuditLog> entries = auditLogRepository
                .findLedgerEntries(null, null, null, null,
                        org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        table.header("Date", "Actor", "Title", "Program", "Direction", "Amount", "Description");

        for (com.mdau.ushirika.module.audit.entity.AuditLog entry : entries) {
            table.col(entry.getCreatedAt() != null ? entry.getCreatedAt().toLocalDate() : "")
                 .col(entry.getActorName())
                 .col(entry.getActorTitle() != null ? entry.getActorTitle() : "")
                 .col(entry.getEntityType() != null ? entry.getEntityType() : "")
                 .col(entry.getDirection())
                 .col(entry.getAmount() != null ? entry.getAmount().toString() : "")
                 .col(entry.getDescription() != null ? entry.getDescription() : "")
                 .newRow();
        }
    }

    // ── Balances (Finance Visibility plan, Phase 5) ─────────────────────────────

    @Transactional(readOnly = true)
    public byte[] balancesCsv() { var t = CsvBuilder.create(); populateBalancesTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] balancesXlsx() { var t = XlsxBuilder.create("Balances"); populateBalancesTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] balancesPdf() { var t = PdfBuilder.create("Balances Report"); populateBalancesTable(t); return t.toBytes(); }

    private void populateBalancesTable(TableBuilder table) {
        FinanceDashboardDto.Balances balances = dashboardService.getBalances();

        table.header("Program", "Net Balance (all-time)");

        balances.byProgram().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> table.col(e.getKey()).col(e.getValue().toString()).newRow());

        table.col("TOTAL (org-wide)").col(balances.orgWideNet().toString()).newRow();
    }

    // ── Officials directory (Finance Visibility plan, Phase 5) ─────────────────

    @Transactional(readOnly = true)
    public byte[] officialsCsv() { var t = CsvBuilder.create(); populateOfficialsTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] officialsXlsx() { var t = XlsxBuilder.create("Officials"); populateOfficialsTable(t); return t.toBytes(); }

    @Transactional(readOnly = true)
    public byte[] officialsPdf() { var t = PdfBuilder.create("Officials Directory Report"); populateOfficialsTable(t); return t.toBytes(); }

    private void populateOfficialsTable(TableBuilder table) {
        List<User> officials = userRepository.findAllByOfficialTitleIsNotNull().stream()
                .sorted(Comparator.comparing(u -> u.getOfficialTitle().name()))
                .toList();

        table.header("Title", "Name", "Email", "Role", "Active");

        for (User u : officials) {
            table.col(u.getOfficialTitle().getDisplayName())
                 .col(u.getFullName())
                 .col(u.getEmail())
                 .col(u.getRole())
                 .col(u.isActive())
                 .newRow();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
