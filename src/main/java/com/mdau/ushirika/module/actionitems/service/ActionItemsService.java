package com.mdau.ushirika.module.actionitems.service;

import com.mdau.ushirika.module.actionitems.dto.ActionItemDto;
import com.mdau.ushirika.module.actionitems.dto.ActionItemsDto;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.Capability;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.repository.MembershipApplicationRepository;
import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import com.mdau.ushirika.module.messaging.repository.ConversationThreadRepository;
import com.mdau.ushirika.module.program.entity.ProgramAdminAssignment;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
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
 * Phase 1 of the "needs your attention" system (Applications + Messaging only — see HANDOFF.md
 * for the full scoping and later-phase module list). Deliberately computed live from existing
 * data on every call rather than a persisted event log: an item disappears the instant its real
 * condition resolves (Send Form clicked, reply sent), with zero risk of drifting from reality.
 * Every item is filtered to only the roles/assignments that can actually act on it.
 */
@Service
@RequiredArgsConstructor
public class ActionItemsService {

    private static final List<UserRole> GLOBAL_ADMIN_ROLES =
            List.of(UserRole.ADMIN, UserRole.SUPERADMIN, UserRole.LEADERSHIP);

    private final MembershipApplicationRepository applicationRepository;
    private final ConversationThreadRepository threadRepository;
    private final ProgramAdminAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ActionItemsDto getActionItems() {
        User me = currentUser();
        List<ActionItemDto> items = new ArrayList<>();

        int applicationsCount = collectApplications(me, items);
        int messagesCount = collectMessages(me, items);

        items.sort(Comparator.comparing(ActionItemDto::occurredAt).reversed());

        return new ActionItemsDto(items.size(), applicationsCount, messagesCount, items);
    }

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
                    occurredAt.toString()
            ));
        }
        return submitted.size();
    }

    private int collectMessages(User me, List<ActionItemDto> items) {
        int count = 0;

        // General (unassigned) inbox — visible to admin-tier roles broadly.
        if (GLOBAL_ADMIN_ROLES.contains(me.getRole())) {
            for (ConversationThread t : threadRepository.findAllByProgramIdIsNullOrderByLastMessageAtDesc()) {
                if (isUnread(t, t.getStaffLastReadAt())) {
                    items.add(messageItem(t, "/admin/messages"));
                    count++;
                }
            }
        }

        // Program-scoped threads — only for the programs this user actually coordinates.
        List<ProgramAdminAssignment> assignments = assignmentRepository.findAllByUserId(me.getId());
        for (ProgramAdminAssignment assignment : assignments) {
            var programId = assignment.getProgram().getId();
            for (ConversationThread t : threadRepository.findAllByProgramIdOrderByLastMessageAtDesc(programId)) {
                if (isUnread(t, t.getStaffLastReadAt())) {
                    items.add(messageItem(t, "/admin/messages"));
                    count++;
                }
            }
        }

        return count;
    }

    private boolean isUnread(ConversationThread t, LocalDateTime staffLastReadAt) {
        return t.getLastMessageAt() != null
                && (staffLastReadAt == null || staffLastReadAt.isBefore(t.getLastMessageAt()));
    }

    private ActionItemDto messageItem(ConversationThread t, String link) {
        String subtitle = t.getProgram() != null
                ? "New message — " + t.getProgram().getName()
                : "New message";
        return new ActionItemDto(
                t.getId().toString(),
                "MESSAGE",
                t.getMember().getFullName(),
                subtitle,
                link,
                t.getLastMessageAt().toString()
        );
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow();
    }
}
