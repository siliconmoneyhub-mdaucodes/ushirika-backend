package com.mdau.ushirika.module.messaging.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.messaging.dto.SendMessageRequest;
import com.mdau.ushirika.module.messaging.dto.StartThreadRequest;
import com.mdau.ushirika.module.messaging.dto.ThreadDetailDto;
import com.mdau.ushirika.module.messaging.dto.ThreadSummaryDto;
import com.mdau.ushirika.module.messaging.entity.ConversationMessage;
import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import com.mdau.ushirika.module.messaging.enums.ThreadPriority;
import com.mdau.ushirika.module.messaging.enums.ThreadStatus;
import com.mdau.ushirika.module.messaging.repository.ConversationMessageRepository;
import com.mdau.ushirika.module.messaging.repository.ConversationThreadRepository;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import com.mdau.ushirika.module.program.repository.ProgramRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessagingServiceTest {

    @Mock ConversationThreadRepository threadRepository;
    @Mock ConversationMessageRepository messageRepository;
    @Mock ProgramRepository programRepository;
    @Mock ProgramAdminAssignmentRepository assignmentRepository;
    @Mock UserRepository userRepository;
    @Mock MemberProfileRepository memberProfileRepository;
    @Mock MessageNotificationService notificationService;

    MessagingService service;
    User member;
    User admin;

    @BeforeEach
    void setUp() {
        service = new MessagingService(threadRepository, messageRepository, programRepository, assignmentRepository,
                userRepository, memberProfileRepository, notificationService);
        member = user("member@example.test", UserRole.MEMBER);
        admin = user("admin@example.test", UserRole.ADMIN);
        when(userRepository.findByEmail("member@example.test")).thenReturn(Optional.of(member));
        when(userRepository.findByEmail("admin@example.test")).thenReturn(Optional.of(admin));
        when(threadRepository.save(any(ConversationThread.class))).thenAnswer(inv -> {
            ConversationThread t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });
        when(messageRepository.save(any(ConversationMessage.class))).thenAnswer(inv -> {
            ConversationMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(LocalDateTime.now());
            return m;
        });
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void newThreadGetsAReferenceNumberAndRequestedPriority() {
        login("member@example.test");
        when(threadRepository.findByMemberIdAndProgramIdIsNull(member.getId())).thenReturn(Optional.empty());
        when(threadRepository.nextReferenceNumber()).thenReturn("MSG-000042");

        ThreadDetailDto detail = service.startOrGetMyThread(new StartThreadRequest(null, ThreadPriority.HIGH));

        assertEquals("MSG-000042", detail.referenceNumber());
        assertEquals(ThreadPriority.HIGH, detail.priority());
        assertEquals(ThreadStatus.OPEN, detail.status());
        assertFalse(detail.hasMore());
        assertNull(detail.oldestLoadedAt());
    }

    @Test
    void existingThreadKeepsItsReferenceNumber() {
        login("member@example.test");
        ConversationThread existing = thread(ThreadStatus.OPEN);
        when(threadRepository.findByMemberIdAndProgramIdIsNull(member.getId())).thenReturn(Optional.of(existing));

        ThreadDetailDto detail = service.startOrGetMyThread(new StartThreadRequest(null, null));

        assertEquals("MSG-000007", detail.referenceNumber());
        verify(threadRepository, never()).nextReferenceNumber();
    }

    @Test
    void unreadCountComesOnlyFromTheViewersSideQuery() {
        ConversationThread t = thread(ThreadStatus.OPEN);
        t.setLastMessageAt(LocalDateTime.now());
        when(messageRepository.countUnreadForStaff(any())).thenReturn(List.<Object[]>of(new Object[]{t.getId(), 3L}));
        when(messageRepository.countUnreadForMember(any())).thenReturn(List.of());
        when(messageRepository.findLastMessagePerThread(any()))
                .thenReturn(List.<Object[]>of(new Object[]{t.getId(), "Hello there", null, Boolean.TRUE}));

        ThreadSummaryDto staffView = service.toSummaries(List.of(t), true).get(0);
        ThreadSummaryDto memberView = service.toSummaries(List.of(t), false).get(0);

        assertEquals(3, staffView.unreadCount());
        assertTrue(staffView.unread());
        assertTrue(staffView.lastMessageFromMember());
        assertEquals("Hello there", staffView.lastMessagePreview());
        assertEquals(0, memberView.unreadCount());
        assertFalse(memberView.unread());
    }

    @Test
    void newMessageReopensAClosedThread() {
        login("admin@example.test");
        ConversationThread closed = thread(ThreadStatus.CLOSED);
        closed.setClosedAt(LocalDateTime.now());
        closed.setClosedByName("Someone");
        when(threadRepository.findById(closed.getId())).thenReturn(Optional.of(closed));

        service.sendGeneralReply(closed.getId(), new SendMessageRequest("Following up"));

        assertEquals(ThreadStatus.OPEN, closed.getStatus());
        assertNull(closed.getClosedAt());
        assertNull(closed.getClosedByName());
        verify(notificationService).onMessagePosted(any(), any());
    }

    @Test
    void closingRecordsWhoClosedIt() {
        login("admin@example.test");
        ConversationThread open = thread(ThreadStatus.OPEN);
        when(threadRepository.findById(open.getId())).thenReturn(Optional.of(open));

        ThreadSummaryDto dto = service.closeGeneralThread(open.getId(), null);

        assertEquals(ThreadStatus.CLOSED, dto.status());
        assertEquals(ThreadStatus.CLOSED, open.getStatus());
        assertEquals(admin.getId(), open.getClosedById());
        assertNotNull(open.getClosedAt());
    }

    @Test
    void applicantRecipientIsRejected() {
        login("admin@example.test");
        User applicant = user("applicant@example.test", UserRole.APPLICANT);
        when(userRepository.findById(applicant.getId())).thenReturn(Optional.of(applicant));
        when(memberProfileRepository.findByUser(applicant)).thenReturn(Optional.of(new MemberProfile()));

        assertThrows(BadRequestException.class,
                () -> service.startGeneralThreadWithMember(applicant.getId(), "hi", null));
    }

    @Test
    void userWithoutMemberProfileIsRejected() {
        login("admin@example.test");
        User otherAdmin = user("other-admin@example.test", UserRole.ADMIN);
        when(userRepository.findById(otherAdmin.getId())).thenReturn(Optional.of(otherAdmin));
        when(memberProfileRepository.findByUser(otherAdmin)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> service.startGeneralThreadWithMember(otherAdmin.getId(), "hi", null));
    }

    @Test
    void deactivatedMemberIsRejected() {
        login("admin@example.test");
        member.setActive(false);
        when(userRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(memberProfileRepository.findByUser(member)).thenReturn(Optional.of(new MemberProfile()));

        assertThrows(BadRequestException.class,
                () -> service.startGeneralThreadWithMember(member.getId(), "hi", null));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null));
    }

    private User user(String email, UserRole role) {
        User u = User.builder().email(email).firstName("Test").lastName("User").role(role).build();
        u.setId(UUID.randomUUID());
        return u;
    }

    private ConversationThread thread(ThreadStatus status) {
        ConversationThread t = ConversationThread.builder()
                .member(member).referenceNumber("MSG-000007").status(status).build();
        t.setId(UUID.randomUUID());
        t.setCreatedAt(LocalDateTime.now().minusDays(1));
        return t;
    }
}
