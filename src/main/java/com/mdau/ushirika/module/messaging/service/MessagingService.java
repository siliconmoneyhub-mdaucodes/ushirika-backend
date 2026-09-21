package com.mdau.ushirika.module.messaging.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.messaging.dto.*;
import com.mdau.ushirika.module.messaging.entity.ConversationMessage;
import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import com.mdau.ushirika.module.messaging.enums.ThreadPriority;
import com.mdau.ushirika.module.messaging.enums.ThreadStatus;
import com.mdau.ushirika.module.messaging.repository.ConversationMessageRepository;
import com.mdau.ushirika.module.messaging.repository.ConversationThreadRepository;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import com.mdau.ushirika.module.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MessagingService {

    static final int DEFAULT_WINDOW = 50;
    static final int MAX_WINDOW = 100;
    /** Staff lists are capped -- pagination is deliberately not introduced in this pass (see plan §2.2). */
    static final int STAFF_LIST_CAP = 200;
    private static final int PREVIEW_MAX = 140;

    private final ConversationThreadRepository threadRepository;
    private final ConversationMessageRepository messageRepository;
    private final ProgramRepository programRepository;
    private final ProgramAdminAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final MessageNotificationService notificationService;

    // ── Member ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ThreadSummaryDto> listMyThreads() {
        User me = currentUser();
        return toSummaries(threadRepository.findAllByMemberIdOrderByLastMessageAtDesc(me.getId()), false);
    }

    @Transactional
    public ThreadDetailDto startOrGetMyThread(StartThreadRequest req) {
        User me = currentUser();
        ConversationThread thread = findOrCreateThread(me, req.programId(), req.priority());
        return toDetail(thread, null, null);
    }

    @Transactional(readOnly = true)
    public ThreadDetailDto getMyThread(UUID threadId, Instant before, Integer limit) {
        ConversationThread thread = findThread(threadId);
        User me = currentUser();
        requireOwner(thread, me);
        return toDetail(thread, before, limit);
    }

    @Transactional
    public MessageDto sendMyMessage(UUID threadId, SendMessageRequest req) {
        ConversationThread thread = findThread(threadId);
        User me = currentUser();
        requireOwner(thread, me);
        ConversationMessage saved = postMessage(thread, me, true, req.body(), true);
        thread.setMemberLastReadAt(LocalDateTime.now());
        threadRepository.save(thread);
        return MessageDto.from(saved);
    }

    @Transactional
    public void markMyThreadRead(UUID threadId) {
        ConversationThread thread = findThread(threadId);
        User me = currentUser();
        requireOwner(thread, me);
        thread.setMemberLastReadAt(LocalDateTime.now());
        threadRepository.save(thread);
    }

    /** Member changes the priority of their own thread; raising to URGENT alerts staff once. */
    @Transactional
    public ThreadSummaryDto setMyThreadPriority(UUID threadId, ThreadPriority priority) {
        ConversationThread thread = findThread(threadId);
        User me = currentUser();
        requireOwner(thread, me);
        ThreadPriority old = thread.getPriority();
        applyPriority(thread, me, priority);
        if (priority == ThreadPriority.URGENT && old != ThreadPriority.URGENT) {
            notificationService.notifyStaffOfEscalation(thread);
        }
        return toSummaries(List.of(thread), false).get(0);
    }

    // ── Staff: general admin inbox (programId == null) ─────────────────────────

    @Transactional(readOnly = true)
    public List<ThreadSummaryDto> listGeneralThreads(ThreadStatus status, ThreadPriority priority, String q) {
        List<ConversationThread> threads = threadRepository.findGeneralThreadsFiltered(
                name(status), name(priority), STAFF_LIST_CAP);
        return toSummaries(filterByQuery(threads, q), true);
    }

    @Transactional(readOnly = true)
    public ThreadDetailDto getGeneralThread(UUID threadId, Instant before, Integer limit) {
        return toDetail(requireGeneralThread(threadId), before, limit);
    }

    @Transactional
    public MessageDto sendGeneralReply(UUID threadId, SendMessageRequest req) {
        ConversationThread thread = requireGeneralThread(threadId);
        User staff = currentUser();
        ConversationMessage saved = postMessage(thread, staff, false, req.body(), true);
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
    public ThreadDetailDto startGeneralThreadWithMember(UUID memberId, String body, ThreadPriority priority) {
        User member = requireMemberRecipient(memberId);
        User staff = currentUser();
        return startStaffThread(member, staff, null, body, priority);
    }

    @Transactional
    public ThreadSummaryDto setGeneralThreadPriority(UUID threadId, ThreadPriority priority) {
        ConversationThread thread = requireGeneralThread(threadId);
        applyPriority(thread, currentUser(), priority);
        return toSummaries(List.of(thread), true).get(0);
    }

    @Transactional
    public ThreadSummaryDto closeGeneralThread(UUID threadId, String note) {
        ConversationThread thread = requireGeneralThread(threadId);
        return closeThread(thread, currentUser(), note);
    }

    @Transactional
    public ThreadSummaryDto reopenGeneralThread(UUID threadId) {
        return reopenThread(requireGeneralThread(threadId));
    }

    // ── Staff: program coordinator inbox ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ThreadSummaryDto> listProgramThreads(UUID programId, ThreadStatus status, ThreadPriority priority, String q) {
        requireCoordinatorAccess(programId);
        List<ConversationThread> threads = threadRepository.findProgramThreadsFiltered(
                programId, name(status), name(priority), STAFF_LIST_CAP);
        return toSummaries(filterByQuery(threads, q), true);
    }

    @Transactional(readOnly = true)
    public ThreadDetailDto getProgramThread(UUID programId, UUID threadId, Instant before, Integer limit) {
        requireCoordinatorAccess(programId);
        return toDetail(requireThreadOnProgram(threadId, programId), before, limit);
    }

    @Transactional
    public MessageDto sendProgramReply(UUID programId, UUID threadId, SendMessageRequest req) {
        requireCoordinatorAccess(programId);
        ConversationThread thread = requireThreadOnProgram(threadId, programId);
        User staff = currentUser();
        ConversationMessage saved = postMessage(thread, staff, false, req.body(), true);
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
    public ThreadDetailDto startProgramThreadWithMember(UUID programId, UUID memberId, String body, ThreadPriority priority) {
        requireCoordinatorAccess(programId);
        User member = requireMemberRecipient(memberId);
        User staff = currentUser();
        return startStaffThread(member, staff, programId, body, priority);
    }

    @Transactional
    public ThreadSummaryDto setProgramThreadPriority(UUID programId, UUID threadId, ThreadPriority priority) {
        requireCoordinatorAccess(programId);
        ConversationThread thread = requireThreadOnProgram(threadId, programId);
        applyPriority(thread, currentUser(), priority);
        return toSummaries(List.of(thread), true).get(0);
    }

    @Transactional
    public ThreadSummaryDto closeProgramThread(UUID programId, UUID threadId, String note) {
        requireCoordinatorAccess(programId);
        ConversationThread thread = requireThreadOnProgram(threadId, programId);
        return closeThread(thread, currentUser(), note);
    }

    @Transactional
    public ThreadSummaryDto reopenProgramThread(UUID programId, UUID threadId) {
        requireCoordinatorAccess(programId);
        return reopenThread(requireThreadOnProgram(threadId, programId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ThreadDetailDto startStaffThread(User member, User staff, UUID programId, String body, ThreadPriority priority) {
        ConversationThread thread = findOrCreateThread(member, programId, priority);
        if (priority != null && thread.getPriority() != priority) {
            applyPriority(thread, staff, priority);
        }
        postMessage(thread, staff, false, body, true);
        thread.setStaffLastReadAt(LocalDateTime.now());
        threadRepository.save(thread);
        return toDetail(thread, null, null);
    }

    private ConversationThread findOrCreateThread(User member, UUID programId, ThreadPriority priority) {
        ThreadPriority initial = priority != null ? priority : ThreadPriority.NORMAL;
        if (programId == null) {
            return threadRepository.findByMemberIdAndProgramIdIsNull(member.getId())
                    .orElseGet(() -> threadRepository.save(ConversationThread.builder()
                            .member(member)
                            .referenceNumber(threadRepository.nextReferenceNumber())
                            .priority(initial)
                            .build()));
        }
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Program not found: " + programId));
        return threadRepository.findByMemberIdAndProgramId(member.getId(), programId)
                .orElseGet(() -> threadRepository.save(ConversationThread.builder()
                        .member(member)
                        .program(program)
                        .referenceNumber(threadRepository.nextReferenceNumber())
                        .priority(initial)
                        .build()));
    }

    private ConversationMessage postMessage(ConversationThread thread, User sender, boolean fromMember,
                                            String body, boolean notify) {
        ConversationMessage message = ConversationMessage.builder()
                .thread(thread)
                .sender(sender)
                .fromMember(fromMember)
                .body(body)
                .build();
        ConversationMessage saved = messageRepository.save(message);
        thread.setLastMessageAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now());
        // A new message on a closed conversation reopens it.
        if (thread.getStatus() == ThreadStatus.CLOSED) {
            thread.setStatus(ThreadStatus.OPEN);
            thread.setClosedAt(null);
            thread.setClosedById(null);
            thread.setClosedByName(null);
        }
        if (notify) {
            // Own try/catch inside the service too, but never let a notification failure roll back the write.
            try {
                notificationService.onMessagePosted(thread, saved);
            } catch (Exception ignored) {
                // MessageNotificationService already logs
            }
        }
        return saved;
    }

    private void applyPriority(ConversationThread thread, User by, ThreadPriority priority) {
        if (priority == null) throw new BadRequestException("Priority is required.");
        if (thread.getPriority() == priority) return;
        thread.setPriority(priority);
        thread.setPrioritySetById(by.getId());
        thread.setPrioritySetAt(LocalDateTime.now());
        threadRepository.save(thread);
    }

    private ThreadSummaryDto closeThread(ConversationThread thread, User staff, String note) {
        if (thread.getStatus() != ThreadStatus.CLOSED) {
            // The optional closing note is shown to the member as the last staff message of the thread.
            // It is posted BEFORE the status flips (posting reopens) and does not trigger an email alert.
            if (note != null && !note.isBlank()) {
                postMessage(thread, staff, false, "Conversation closed: " + note.trim(), false);
            }
            thread.setStatus(ThreadStatus.CLOSED);
            thread.setClosedAt(LocalDateTime.now());
            thread.setClosedById(staff.getId());
            thread.setClosedByName(staff.getFullName());
            thread.setStaffLastReadAt(LocalDateTime.now());
            threadRepository.save(thread);
        }
        return toSummaries(List.of(thread), true).get(0);
    }

    private ThreadSummaryDto reopenThread(ConversationThread thread) {
        if (thread.getStatus() != ThreadStatus.OPEN) {
            thread.setStatus(ThreadStatus.OPEN);
            thread.setClosedAt(null);
            thread.setClosedById(null);
            thread.setClosedByName(null);
            threadRepository.save(thread);
        }
        return toSummaries(List.of(thread), true).get(0);
    }

    private void requireOwner(ConversationThread thread, User me) {
        if (!thread.getMember().getId().equals(me.getId())) {
            throw new ForbiddenException("This is not your conversation.");
        }
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

    /**
     * B-8: staff may only start a conversation with an actual, active member. "Member" here means a
     * non-applicant account that has a member profile -- the same definition MemberProfileRepository.
     * findAllMemberUsers() uses -- so officials (Secretary, Chief Whip, ...) who are also members
     * remain reachable, while applicants, deactivated accounts and pure admin accounts are rejected.
     */
    private User requireMemberRecipient(UUID memberId) {
        User target = userRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        if (target.getRole() == UserRole.APPLICANT || memberProfileRepository.findByUser(target).isEmpty()) {
            throw new BadRequestException("You can only start a conversation with an active member.");
        }
        if (!target.isActive() || target.isMembershipCeased()) {
            throw new BadRequestException("That member's account is not active.");
        }
        return target;
    }

    private void requireCoordinatorAccess(UUID programId) {
        User me = currentUser();
        boolean isAssignedCoordinator = assignmentRepository.existsByProgramIdAndUserId(programId, me.getId());
        boolean isGlobalAdmin = me.getRole() == UserRole.ADMIN || me.getRole() == UserRole.SUPERADMIN;
        if (!isAssignedCoordinator && !isGlobalAdmin) {
            throw new ForbiddenException("You do not coordinate this program");
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }

    /** Free-text filter on member name or reference number, applied in Java over the capped result. */
    private List<ConversationThread> filterByQuery(List<ConversationThread> threads, String q) {
        if (q == null || q.isBlank()) return threads;
        String needle = q.trim().toLowerCase(Locale.ROOT);
        return threads.stream()
                .filter(t -> (t.getReferenceNumber() != null && t.getReferenceNumber().toLowerCase(Locale.ROOT).contains(needle))
                        || t.getMember().getFullName().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    /**
     * One last-message query + one unread-count query + one member-code query for the whole list,
     * regardless of how many threads there are (the old code loaded every message of every thread).
     */
    List<ThreadSummaryDto> toSummaries(List<ConversationThread> threads, boolean staffSide) {
        if (threads.isEmpty()) return List.of();
        List<UUID> threadIds = threads.stream().map(ConversationThread::getId).toList();

        Map<UUID, Object[]> lastByThread = new HashMap<>();
        for (Object[] row : messageRepository.findLastMessagePerThread(threadIds)) {
            lastByThread.put(toUuid(row[0]), row);
        }
        Map<UUID, Integer> unreadByThread = new HashMap<>();
        List<Object[]> unreadRows = staffSide
                ? messageRepository.countUnreadForStaff(threadIds)
                : messageRepository.countUnreadForMember(threadIds);
        for (Object[] row : unreadRows) {
            unreadByThread.put(toUuid(row[0]), ((Number) row[1]).intValue());
        }
        Map<UUID, String> codeByUser = memberCodes(threads.stream().map(t -> t.getMember().getId()).distinct().toList());

        List<ThreadSummaryDto> out = new ArrayList<>(threads.size());
        for (ConversationThread t : threads) {
            Object[] last = lastByThread.get(t.getId());
            String preview = last != null ? preview((String) last[1]) : null;
            boolean lastFromMember = last != null && Boolean.TRUE.equals(last[3]);
            int unreadCount = unreadByThread.getOrDefault(t.getId(), 0);
            out.add(new ThreadSummaryDto(
                    t.getId(),
                    t.getReferenceNumber(),
                    t.getMember().getId(),
                    t.getMember().getFullName(),
                    codeByUser.get(t.getMember().getId()),
                    t.getProgram() != null ? t.getProgram().getId() : null,
                    t.getProgram() != null ? t.getProgram().getName() : null,
                    t.getPriority() != null ? t.getPriority() : ThreadPriority.NORMAL,
                    t.getStatus() != null ? t.getStatus() : ThreadStatus.OPEN,
                    preview,
                    AppClock.serverInstant(t.getLastMessageAt()),
                    lastFromMember,
                    unreadCount > 0,
                    unreadCount,
                    AppClock.serverInstant(t.getCreatedAt())
            ));
        }
        return out;
    }

    /** Newest {@code limit} messages strictly before {@code before} (default: now), returned oldest -> newest. */
    ThreadDetailDto toDetail(ConversationThread t, Instant before, Integer limit) {
        int lim = limit == null ? DEFAULT_WINDOW : Math.max(1, Math.min(limit, MAX_WINDOW));
        LocalDateTime cursor = before != null
                ? LocalDateTime.ofInstant(before, ZoneOffset.UTC)
                : LocalDateTime.now(ZoneOffset.UTC).plusDays(1);

        // Fetch one extra row to learn whether older messages exist, without a separate count query.
        List<ConversationMessage> newestFirst = new ArrayList<>(messageRepository
                .findAllByThreadIdAndCreatedAtLessThanOrderByCreatedAtDesc(t.getId(), cursor, PageRequest.of(0, lim + 1)));
        boolean hasMore = newestFirst.size() > lim;
        if (hasMore) newestFirst = new ArrayList<>(newestFirst.subList(0, lim));
        Collections.reverse(newestFirst);

        List<MessageDto> messages = newestFirst.stream().map(MessageDto::from).toList();
        Instant oldestLoadedAt = hasMore && !newestFirst.isEmpty()
                ? AppClock.serverInstant(newestFirst.get(0).getCreatedAt())
                : null;

        return new ThreadDetailDto(
                t.getId(),
                t.getReferenceNumber(),
                t.getMember().getId(),
                t.getMember().getFullName(),
                memberCodes(List.of(t.getMember().getId())).get(t.getMember().getId()),
                t.getProgram() != null ? t.getProgram().getId() : null,
                t.getProgram() != null ? t.getProgram().getName() : null,
                t.getPriority() != null ? t.getPriority() : ThreadPriority.NORMAL,
                t.getStatus() != null ? t.getStatus() : ThreadStatus.OPEN,
                AppClock.serverInstant(t.getClosedAt()),
                t.getClosedByName(),
                messages,
                hasMore,
                oldestLoadedAt
        );
    }

    private Map<UUID, String> memberCodes(Collection<UUID> userIds) {
        Map<UUID, String> codes = new HashMap<>();
        for (MemberProfile p : memberProfileRepository.findAllByUserIdIn(userIds)) {
            if (p.getMemberId() != null && p.getUser() != null) codes.put(p.getUser().getId(), p.getMemberId());
        }
        return codes;
    }

    private static String preview(String body) {
        if (body == null) return null;
        return body.length() > PREVIEW_MAX ? body.substring(0, PREVIEW_MAX) + "…" : body;
    }

    private static UUID toUuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(String.valueOf(o));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
