package com.mdau.ushirika.module.actionitems.service;

import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.actionitems.dto.ActionItemDto;
import com.mdau.ushirika.module.actionitems.dto.ActionItemsDto;
import com.mdau.ushirika.module.attendance.entity.AttendanceExcuse;
import com.mdau.ushirika.module.attendance.enums.ExcuseStatus;
import com.mdau.ushirika.module.attendance.repository.AttendanceExcuseRepository;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.Capability;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.benevolence.entity.BenevolenceClaim;
import com.mdau.ushirika.module.benevolence.entity.BenevolenceJoinRequest;
import com.mdau.ushirika.module.benevolence.enums.BenevolenceJoinRequestStatus;
import com.mdau.ushirika.module.benevolence.enums.ClaimStatus;
import com.mdau.ushirika.module.benevolence.repository.BenevolenceClaimRepository;
import com.mdau.ushirika.module.benevolence.repository.BenevolenceJoinRequestRepository;
import com.mdau.ushirika.module.election.entity.ElectionCandidacy;
import com.mdau.ushirika.module.election.enums.CandidacyStatus;
import com.mdau.ushirika.module.election.repository.ElectionCandidacyRepository;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.repository.MembershipApplicationRepository;
import com.mdau.ushirika.module.messaging.entity.ConversationMessage;
import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import com.mdau.ushirika.module.messaging.repository.ConversationMessageRepository;
import com.mdau.ushirika.module.messaging.repository.ConversationThreadRepository;
import com.mdau.ushirika.module.mgr.entity.MgrJoinRequest;
import com.mdau.ushirika.module.mgr.enums.JoinRequestStatus;
import com.mdau.ushirika.module.mgr.repository.MgrJoinRequestRepository;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.entity.ProgramAdminAssignment;
import com.mdau.ushirika.module.program.enums.ProgramType;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import com.mdau.ushirika.module.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "Needs your attention" system — see HANDOFF.md for the original scoping pass and module list.
 * Deliberately computed live from existing data on every call rather than a persisted event log:
 * an item disappears the instant its real condition resolves (Send Form clicked, reply sent,
 * claim decided), with zero risk of drifting from reality. Every item is filtered to only the
 * roles/assignments that can actually act on it — mirrors each domain's own existing
 * authorization rules (see each collect* method) rather than inventing new ones.
 *
 * Phase 1: Applications, Messaging. Phase 2 (this pass): Benevolence (join requests + claims),
 * MGR (join requests), Meetings (attendance excuses), Elections (candidacies).
 */
@Service
@RequiredArgsConstructor
public class ActionItemsService {

    private static final List<UserRole> GLOBAL_ADMIN_ROLES =
            List.of(UserRole.ADMIN, UserRole.SUPERADMIN, UserRole.LEADERSHIP);

    private static final int PREVIEW_LENGTH = 80;

    private final MembershipApplicationRepository applicationRepository;
    private final ConversationThreadRepository threadRepository;
    private final ConversationMessageRepository messageRepository;
    private final BenevolenceJoinRequestRepository benevolenceJoinRequestRepository;
    private final BenevolenceClaimRepository benevolenceClaimRepository;
    private final MgrJoinRequestRepository mgrJoinRequestRepository;
    private final AttendanceExcuseRepository excuseRepository;
    private final ElectionCandidacyRepository candidacyRepository;
    private final ProgramRepository programRepository;
    private final ProgramAdminAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ActionItemsDto getActionItems() {
        User me = currentUser();
        List<ActionItemDto> items = new ArrayList<>();

        int applicationsCount = collectApplications(me, items);
        int messagesCount = collectMessages(me, items);
        int benevolenceCount = collectBenevolence(me, items);
        int mgrCount = collectMgr(me, items);
        int meetingsCount = collectMeetings(me, items);
        int electionsCount = collectElections(me, items);

        items.sort(Comparator.comparing(ActionItemDto::occurredAt).reversed());

        return new ActionItemsDto(items.size(), applicationsCount, messagesCount,
                benevolenceCount, mgrCount, meetingsCount, electionsCount, items);
    }

    // ── Applications ─────────────────────────────────────────────────────────

    private int collectApplications(User me, List<ActionItemDto> items) {
        boolean canSee = GLOBAL_ADMIN_ROLES.contains(me.getRole())
                || me.getRole() == UserRole.SECRETARY
                || me.getCapabilities().contains(Capability.APPLICATIONS);
        if (!canSee) return 0;

        List<MembershipApplication> submitted =
                applicationRepository.findAllByStatus(ApplicationStatus.SUBMITTED, Pageable.unpaged()).getContent();

        for (MembershipApplication app : submitted) {
            String name = app.getUser() != null ? app.getUser().getFullName() : app.getApplicantName();
            LocalDateTime occurredAt = app.getSubmittedAt() != null ? app.getSubmittedAt() : app.getCreatedAt();
            items.add(new ActionItemDto(
                    app.getId().toString(),
                    "APPLICATION",
                    name != null ? name : "New applicant",
                    "New application — " + app.getReferenceNumber(),
                    "/admin/applications",
                    AppClock.serverInstant(occurredAt).toString()
            ));
        }
        return submitted.size();
    }

    // ── Messages ─────────────────────────────────────────────────────────────

    private int collectMessages(User me, List<ActionItemDto> items) {
        int count = 0;

        // General (unassigned) inbox — visible to admin-tier roles broadly.
        if (GLOBAL_ADMIN_ROLES.contains(me.getRole())) {
            for (ConversationThread t : threadRepository.findAllByProgramIdIsNullOrderByLastMessageAtDesc()) {
                count += addUnreadMessageItems(t, "/admin/messages", items);
            }
        }

        // Program-scoped threads — only for the programs this user actually coordinates.
        List<ProgramAdminAssignment> assignments = assignmentRepository.findAllByUserId(me.getId());
        for (ProgramAdminAssignment assignment : assignments) {
            var programId = assignment.getProgram().getId();
            for (ConversationThread t : threadRepository.findAllByProgramIdOrderByLastMessageAtDesc(programId)) {
                count += addUnreadMessageItems(t, "/admin/messages", items);
            }
        }

        return count;
    }

    /**
     * One item per unread member MESSAGE, not per thread — a member sending 3 messages before
     * staff reads any of them surfaces as 3 distinct items (and 3 distinct toasts, since each has
     * its own message ID), matching how a real inbox reads "3 unread" rather than "1 unread
     * conversation". Each item's ID is the message's own ID, so it stays a genuinely new item on
     * every poll until markGeneralThreadRead/markProgramThreadRead bumps staffLastReadAt past it.
     */
    private int addUnreadMessageItems(ConversationThread t, String link, List<ActionItemDto> items) {
        if (t.getLastMessageAt() == null) return 0;
        LocalDateTime since = t.getStaffLastReadAt() != null ? t.getStaffLastReadAt() : t.getCreatedAt();
        List<ConversationMessage> unread =
                messageRepository.findAllByThreadAndFromMemberAndCreatedAtAfterOrderByCreatedAtAsc(t, true, since);

        for (ConversationMessage m : unread) {
            String subtitle = t.getProgram() != null
                    ? preview(m.getBody()) + " — " + t.getProgram().getName()
                    : preview(m.getBody());
            items.add(new ActionItemDto(
                    m.getId().toString(),
                    "MESSAGE",
                    t.getMember().getFullName(),
                    subtitle,
                    link,
                    AppClock.serverInstant(m.getCreatedAt()).toString()
            ));
        }
        return unread.size();
    }

    // ── Benevolence: join requests (coordinator-gated) + claims (finance-gated) ────────────────

    private int collectBenevolence(User me, List<ActionItemDto> items) {
        int count = 0;

        if (isProgramCoordinator(me, ProgramType.BENEVOLENCE)) {
            List<BenevolenceJoinRequest> pending =
                    benevolenceJoinRequestRepository.findByStatusOrderByCreatedAtDesc(BenevolenceJoinRequestStatus.PENDING);
            for (BenevolenceJoinRequest r : pending) {
                items.add(new ActionItemDto(
                        "benevolence-join-" + r.getId(),
                        "BENEVOLENCE",
                        r.getUser().getFullName(),
                        "Wants to join Benevolence — needs Send Form",
                        "/admin/benevolence",
                        AppClock.serverInstant(r.getCreatedAt()).toString()
                ));
            }
            count += pending.size();
        }

        boolean canSeeClaims = GLOBAL_ADMIN_ROLES.contains(me.getRole())
                || me.getRole() == UserRole.FINANCIAL_ADMIN
                || me.getCapabilities().contains(Capability.FINANCE_ADVANCED)
                || isProgramCoordinator(me, ProgramType.BENEVOLENCE);
        if (canSeeClaims) {
            List<BenevolenceClaim> submitted =
                    benevolenceClaimRepository.findAllByStatusOrderBySubmittedAtDesc(ClaimStatus.SUBMITTED, Pageable.unpaged()).getContent();
            for (BenevolenceClaim c : submitted) {
                items.add(new ActionItemDto(
                        "benevolence-claim-" + c.getId(),
                        "BENEVOLENCE",
                        c.getDeceasedName() != null ? "Claim: " + c.getDeceasedName() : "New claim",
                        "Submitted by " + c.getEnrollment().getUser().getFullName() + " — " + c.getReferenceNumber(),
                        "/admin/benevolence",
                        AppClock.serverInstant(c.getSubmittedAt() != null ? c.getSubmittedAt() : c.getCreatedAt()).toString()
                ));
            }
            count += submitted.size();
        }

        return count;
    }

    // ── MGR: join requests (coordinator-gated) ──────────────────────────────────────────────────

    private int collectMgr(User me, List<ActionItemDto> items) {
        if (!isProgramCoordinator(me, ProgramType.MGR)) return 0;

        List<MgrJoinRequest> pending = mgrJoinRequestRepository.findByStatusOrderByCreatedAtAsc(JoinRequestStatus.PENDING);
        for (MgrJoinRequest r : pending) {
            items.add(new ActionItemDto(
                    "mgr-join-" + r.getId(),
                    "MGR",
                    r.getUser().getFullName(),
                    "Wants to join MGR — needs Send Form",
                    "/admin/mgr",
                    AppClock.serverInstant(r.getCreatedAt()).toString()
            ));
        }
        return pending.size();
    }

    // ── Meetings: attendance excuses ────────────────────────────────────────────────────────────

    private int collectMeetings(User me, List<ActionItemDto> items) {
        boolean canSee = GLOBAL_ADMIN_ROLES.contains(me.getRole())
                || me.getRole() == UserRole.CHIEF_WHIP
                || me.getCapabilities().contains(Capability.DISCIPLINE)
                || me.getCapabilities().contains(Capability.MEETINGS_ATTENDANCE);
        if (!canSee) return 0;

        List<AttendanceExcuse> pending =
                excuseRepository.findByStatusOrderByCreatedAtDesc(ExcuseStatus.PENDING, Pageable.unpaged()).getContent();
        for (AttendanceExcuse e : pending) {
            items.add(new ActionItemDto(
                    "excuse-" + e.getId(),
                    "MEETING",
                    e.getAttendanceRecord().getUser().getFullName(),
                    "Submitted an excuse — " + preview(e.getReason()),
                    "/admin/meetings",
                    AppClock.serverInstant(e.getCreatedAt()).toString()
            ));
        }
        return pending.size();
    }

    // ── Elections: candidacies ──────────────────────────────────────────────────────────────────

    private int collectElections(User me, List<ActionItemDto> items) {
        boolean canSee = GLOBAL_ADMIN_ROLES.contains(me.getRole())
                || me.getCapabilities().contains(Capability.ELECTIONS);
        if (!canSee) return 0;

        List<ElectionCandidacy> pending = candidacyRepository.findAllByStatusOrderByCreatedAtAsc(CandidacyStatus.PENDING);
        for (ElectionCandidacy c : pending) {
            items.add(new ActionItemDto(
                    "candidacy-" + c.getId(),
                    "ELECTION",
                    c.getCandidate().getFullName(),
                    "New candidacy — " + c.getSeat().getTitle(),
                    "/admin/elections",
                    AppClock.serverInstant(c.getCreatedAt()).toString()
            ));
        }
        return pending.size();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Mirrors each program module's own requireXCoordinatorAccess() check (SUPERADMIN, or
     * assigned via ProgramAdminAssignment to a program of this type) rather than inventing a
     * separate rule — an admin who can't actually send a form/decide a request shouldn't be
     * shown it as "needs your attention" either. */
    private boolean isProgramCoordinator(User me, ProgramType type) {
        if (me.getRole() == UserRole.SUPERADMIN) return true;
        List<Program> programs = programRepository.findAllByType(type);
        return programs.stream().anyMatch(p -> assignmentRepository.existsByProgramIdAndUserId(p.getId(), me.getId()));
    }

    private String preview(String body) {
        if (body == null) return "";
        return body.length() > PREVIEW_LENGTH ? body.substring(0, PREVIEW_LENGTH) + "…" : body;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }
}
