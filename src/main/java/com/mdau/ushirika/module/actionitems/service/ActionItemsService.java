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
import com.mdau.ushirika.module.messaging.entity.ConversationMessage;
import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import com.mdau.ushirika.module.messaging.repository.ConversationMessageRepository;
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

    private static final int PREVIEW_LENGTH = 80;

    private final MembershipApplicationRepository applicationRepository;
    private final ConversationThreadRepository threadRepository;
    private final ConversationMessageRepository messageRepository;
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
                    m.getCreatedAt().toString()
            ));
        }
        return unread.size();
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
