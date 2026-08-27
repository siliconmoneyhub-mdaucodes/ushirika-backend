package com.mdau.ushirika.module.messaging.service;

import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.messaging.dto.*;
import com.mdau.ushirika.module.messaging.entity.ConversationMessage;
import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import com.mdau.ushirika.module.messaging.repository.ConversationMessageRepository;
import com.mdau.ushirika.module.messaging.repository.ConversationThreadRepository;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import com.mdau.ushirika.module.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessagingService {

    private final ConversationThreadRepository threadRepository;
    private final ConversationMessageRepository messageRepository;
    private final ProgramRepository programRepository;
    private final ProgramAdminAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    // ── Member ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ThreadSummaryDto> listMyThreads() {
        User me = currentUser();
        return threadRepository.findAllByMemberIdOrderByLastMessageAtDesc(me.getId()).stream()
                .map(t -> toSummary(t, t.getMemberLastReadAt()))
                .toList();
    }

    @Transactional
    public ThreadDetailDto startOrGetMyThread(StartThreadRequest req) {
        User me = currentUser();
        ConversationThread thread = findOrCreateThread(me, req.programId());
        return toDetail(thread);
    }

    @Transactional(readOnly = true)
    public ThreadDetailDto getMyThread(UUID threadId) {
        ConversationThread thread = findThread(threadId);
        User me = currentUser();
        if (!thread.getMember().getId().equals(me.getId())) {
            throw new ForbiddenException("This is not your conversation.");
        }
        return toDetail(thread);
    }

    @Transactional
    public MessageDto sendMyMessage(UUID threadId, SendMessageRequest req) {
        ConversationThread thread = findThread(threadId);
        User me = currentUser();
        if (!thread.getMember().getId().equals(me.getId())) {
            throw new ForbiddenException("This is not your conversation.");
        }
        ConversationMessage saved = postMessage(thread, me, true, req.body());
        thread.setMemberLastReadAt(LocalDateTime.now());
        threadRepository.save(thread);
        return MessageDto.from(saved);
    }

    @Transactional
    public void markMyThreadRead(UUID threadId) {
        ConversationThread thread = findThread(threadId);
        User me = currentUser();
        if (!thread.getMember().getId().equals(me.getId())) {
            throw new ForbiddenException("This is not your conversation.");
        }
        thread.setMemberLastReadAt(LocalDateTime.now());
        threadRepository.save(thread);
    }

    // ── Staff: general admin inbox (programId == null) ─────────────────────────

    @Transactional(readOnly = true)
    public List<ThreadSummaryDto> listGeneralThreads() {
        return threadRepository.findAllByProgramIdIsNullOrderByLastMessageAtDesc().stream()
                .map(t -> toSummary(t, t.getStaffLastReadAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ThreadDetailDto getGeneralThread(UUID threadId) {
        ConversationThread thread = requireGeneralThread(threadId);
        return toDetail(thread);
    }

    @Transactional
    public MessageDto sendGeneralReply(UUID threadId, SendMessageRequest req) {
        ConversationThread thread = requireGeneralThread(threadId);
        User staff = currentUser();
        ConversationMessage saved = postMessage(thread, staff, false, req.body());
        thread.setStaffLastReadAt(LocalDateTime.now());
        threadRepository.save(thread);
        return MessageDto.from(saved);
    }

    @Transactional
    public void markGeneralThreadRead(UUID threadId) {
        ConversationThread thread = requireGeneralThread(threadId);
        thread.setStaffLastReadAt(LocalDateTime.now());
        threadRepository.save(thread);
    }

    /** Admin/superadmin starts (or continues) a general conversation with a specific member. */
    @Transactional
    public ThreadDetailDto startGeneralThreadWithMember(UUID memberId, String body) {
        User member = requireMemberRecipient(memberId);
        User staff = currentUser();
        ConversationThread thread = findOrCreateThread(member, null);
        postMessage(thread, staff, false, body);
        thread.setStaffLastReadAt(LocalDateTime.now());
        threadRepository.save(thread);
        return toDetail(thread);
    }

    // ── Staff: program coordinator inbox ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ThreadSummaryDto> listProgramThreads(UUID programId) {
        requireCoordinatorAccess(programId);
        return threadRepository.findAllByProgramIdOrderByLastMessageAtDesc(programId).stream()
                .map(t -> toSummary(t, t.getStaffLastReadAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ThreadDetailDto getProgramThread(UUID programId, UUID threadId) {
        requireCoordinatorAccess(programId);
        ConversationThread thread = requireThreadOnProgram(threadId, programId);
        return toDetail(thread);
    }

    @Transactional
    public MessageDto sendProgramReply(UUID programId, UUID threadId, SendMessageRequest req) {
        requireCoordinatorAccess(programId);
        ConversationThread thread = requireThreadOnProgram(threadId, programId);
        User staff = currentUser();
        ConversationMessage saved = postMessage(thread, staff, false, req.body());
        thread.setStaffLastReadAt(LocalDateTime.now());
        threadRepository.save(thread);
        return MessageDto.from(saved);
    }

    @Transactional
    public void markProgramThreadRead(UUID programId, UUID threadId) {
        requireCoordinatorAccess(programId);
        ConversationThread thread = requireThreadOnProgram(threadId, programId);
        thread.setStaffLastReadAt(LocalDateTime.now());
        threadRepository.save(thread);
    }

    /** A program's coordinator starts (or continues) a conversation with a specific member. */
    @Transactional
    public ThreadDetailDto startProgramThreadWithMember(UUID programId, UUID memberId, String body) {
        requireCoordinatorAccess(programId);
        User member = requireMemberRecipient(memberId);
        User staff = currentUser();
        ConversationThread thread = findOrCreateThread(member, programId);
        postMessage(thread, staff, false, body);
        thread.setStaffLastReadAt(LocalDateTime.now());
        threadRepository.save(thread);
        return toDetail(thread);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ConversationThread findOrCreateThread(User member, UUID programId) {
        if (programId == null) {
            return threadRepository.findByMemberIdAndProgramIdIsNull(member.getId())
                    .orElseGet(() -> threadRepository.save(ConversationThread.builder()
                            .member(member)
                            .build()));
        }
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Program not found: " + programId));
        return threadRepository.findByMemberIdAndProgramId(member.getId(), programId)
                .orElseGet(() -> threadRepository.save(ConversationThread.builder()
                        .member(member)
                        .program(program)
                        .build()));
    }

    private ConversationMessage postMessage(ConversationThread thread, User sender, boolean fromMember, String body) {
        ConversationMessage message = ConversationMessage.builder()
                .thread(thread)
                .sender(sender)
                .fromMember(fromMember)
                .body(body)
                .build();
        ConversationMessage saved = messageRepository.save(message);
        thread.setLastMessageAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now());
        return saved;
    }

    private ConversationThread requireGeneralThread(UUID threadId) {
        ConversationThread thread = findThread(threadId);
        if (thread.getProgram() != null) {
            throw new ResourceNotFoundException("Thread not found: " + threadId);
        }
        return thread;
    }

    private ConversationThread requireThreadOnProgram(UUID threadId, UUID programId) {
        ConversationThread thread = findThread(threadId);
        if (thread.getProgram() == null || !thread.getProgram().getId().equals(programId)) {
            throw new ResourceNotFoundException("Thread not found: " + threadId);
        }
        return thread;
    }

    private ConversationThread findThread(UUID threadId) {
        return threadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + threadId));
    }

    private User requireMemberRecipient(UUID memberId) {
        return userRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
    }

    private void requireCoordinatorAccess(UUID programId) {
        User me = currentUser();
        boolean isAssignedCoordinator = assignmentRepository.existsByProgramIdAndUserId(programId, me.getId());
        boolean isGlobalAdmin = me.getRole() == UserRole.ADMIN || me.getRole() == UserRole.SUPERADMIN;
        if (!isAssignedCoordinator && !isGlobalAdmin) {
            throw new ForbiddenException("You do not coordinate this program");
        }
    }

    private ThreadSummaryDto toSummary(ConversationThread t, LocalDateTime viewerLastReadAt) {
        boolean unread = t.getLastMessageAt() != null
                && (viewerLastReadAt == null || viewerLastReadAt.isBefore(t.getLastMessageAt()));
        List<ConversationMessage> messages = messageRepository.findAllByThreadOrderByCreatedAtAsc(t);
        String preview = messages.isEmpty() ? null : messages.get(messages.size() - 1).getBody();
        return new ThreadSummaryDto(
                t.getId(),
                t.getMember().getId(),
                t.getMember().getFullName(),
                t.getProgram() != null ? t.getProgram().getId() : null,
                t.getProgram() != null ? t.getProgram().getName() : null,
                preview,
                AppClock.serverInstant(t.getLastMessageAt()),
                unread
        );
    }

    private ThreadDetailDto toDetail(ConversationThread t) {
        List<MessageDto> messages = messageRepository.findAllByThreadOrderByCreatedAtAsc(t).stream()
                .map(MessageDto::from)
                .toList();
        return new ThreadDetailDto(
                t.getId(),
                t.getMember().getId(),
                t.getMember().getFullName(),
                t.getProgram() != null ? t.getProgram().getId() : null,
                t.getProgram() != null ? t.getProgram().getName() : null,
                messages
        );
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
