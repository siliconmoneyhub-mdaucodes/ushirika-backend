package com.mdau.ushirika.module.messaging.service;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.messaging.entity.ConversationMessage;
import com.mdau.ushirika.module.messaging.entity.ConversationThread;
import com.mdau.ushirika.module.messaging.enums.ThreadPriority;
import com.mdau.ushirika.module.messaging.repository.ConversationThreadRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.notification.service.InAppNotificationService;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.entity.ProgramAdminAssignment;
import com.mdau.ushirika.module.program.repository.ProgramAdminAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageNotificationServiceTest {

    @Mock ConversationThreadRepository threadRepository;
    @Mock UserRepository userRepository;
    @Mock ProgramAdminAssignmentRepository assignmentRepository;
    @Mock MemberProfileRepository memberProfileRepository;
    @Mock EmailService emailService;
    @Mock InAppNotificationService inAppNotificationService;

    MessageNotificationService service;
    User member;
    User admin;

    @BeforeEach
    void setUp() {
        service = new MessageNotificationService(threadRepository, userRepository, assignmentRepository,
                memberProfileRepository, emailService, inAppNotificationService);
        ReflectionTestUtils.setField(service, "siteUrl", "https://example.test");
        member = user("member@example.test", UserRole.MEMBER);
        admin = user("admin@example.test", UserRole.ADMIN);
        when(userRepository.findAllByRoleIn(any())).thenReturn(List.of(admin));
    }

    // ── staff side ───────────────────────────────────────────────────────────

    @Test
    void secondMemberMessageInsideWindowSendsNoSecondEmail() {
        ConversationThread thread = thread(null, ThreadPriority.NORMAL);
        service.onMessagePosted(thread, message(true));
        service.onMessagePosted(thread, message(true));
        verify(emailService, times(1)).sendPlain(eq("admin@example.test"), any(), any(), any());
        assertNotNull(thread.getStaffAlertSentAt());
    }

    @Test
    void staffAlertSubjectCarriesReferenceAndPriorityAndNeverTheBody() {
        ConversationThread thread = thread(null, ThreadPriority.URGENT);
        ConversationMessage m = message(true);
        m.setBody("secret bereavement details");
        service.onMessagePosted(thread, m);
        verify(emailService).sendPlain(eq("admin@example.test"), any(),
                eq("[URGENT] [MSG-000123] New message from Brian Wafula"),
                argThat(html -> !html.contains("secret bereavement details") && html.contains("MSG-000123")));
    }

    @Test
    void urgentBypassesFifteenMinuteWindowButNotThreeMinuteFloor() {
        LocalDateTime now = LocalDateTime.now();
        ConversationThread normal = thread(null, ThreadPriority.NORMAL);
        normal.setStaffAlertSentAt(now.minusMinutes(5));
        assertFalse(MessageNotificationService.shouldAlertStaff(normal, now, false));

        ConversationThread urgent = thread(null, ThreadPriority.URGENT);
        urgent.setStaffAlertSentAt(now.minusMinutes(5));
        assertTrue(MessageNotificationService.shouldAlertStaff(urgent, now, false));

        urgent.setStaffAlertSentAt(now.minusMinutes(1));
        assertFalse(MessageNotificationService.shouldAlertStaff(urgent, now, false));
        assertFalse(MessageNotificationService.shouldAlertStaff(urgent, now, true));
    }

    @Test
    void noStaffAlertWhileStaffAreActivelyReading() {
        LocalDateTime now = LocalDateTime.now();
        ConversationThread thread = thread(null, ThreadPriority.NORMAL);
        thread.setStaffLastReadAt(now.minusSeconds(30));
        assertFalse(MessageNotificationService.shouldAlertStaff(thread, now, false));
    }

    @Test
    void coordinatorlessProgramFallsBackToAdminsAndSuperadmins() {
        Program program = Program.builder().name("Benevolence").build();
        program.setId(UUID.randomUUID());
        when(assignmentRepository.findAllByProgramId(program.getId())).thenReturn(List.of());
        ConversationThread thread = thread(program, ThreadPriority.NORMAL);

        List<User> recipients = service.resolveStaffRecipients(thread);
        assertEquals(List.of(admin), recipients);
    }

    @Test
    void programThreadGoesToAssignedCoordinatorsOnly() {
        Program program = Program.builder().name("MGR").build();
        program.setId(UUID.randomUUID());
        User coordinator = user("coord@example.test", UserRole.MEMBER);
        when(assignmentRepository.findAllByProgramId(program.getId()))
                .thenReturn(List.of(ProgramAdminAssignment.builder().program(program).user(coordinator).build()));
        ConversationThread thread = thread(program, ThreadPriority.NORMAL);

        assertEquals(List.of(coordinator), service.resolveStaffRecipients(thread));
    }

    @Test
    void inactiveStaffAreSkipped() {
        admin.setActive(false);
        assertTrue(service.resolveStaffRecipients(thread(null, ThreadPriority.NORMAL)).isEmpty());
    }

    // ── member side ──────────────────────────────────────────────────────────

    @Test
    void memberGetsInAppNotificationEveryTimeButEmailOnlyOncePerWindow() {
        ConversationThread thread = thread(null, ThreadPriority.NORMAL);
        service.onMessagePosted(thread, message(false));
        service.onMessagePosted(thread, message(false));
        verify(inAppNotificationService, times(2)).createForUser(eq(member.getId()), any(), any(), any(), eq("/portal/messages"));
        verify(emailService, times(1)).sendPlain(eq("member@example.test"), any(), any(), any());
    }

    @Test
    void memberReadingSinceLastAlertResetsTheWindow() {
        LocalDateTime now = LocalDateTime.now();
        ConversationThread thread = thread(null, ThreadPriority.NORMAL);
        thread.setMemberAlertSentAt(now.minusMinutes(10));
        assertFalse(MessageNotificationService.shouldAlertMember(thread, now));

        thread.setMemberLastReadAt(now.minusMinutes(5)); // read after the alert, but not "actively" reading
        assertTrue(MessageNotificationService.shouldAlertMember(thread, now));
    }

    @Test
    void memberAlertedAgainOnceWindowHasPassed() {
        LocalDateTime now = LocalDateTime.now();
        ConversationThread thread = thread(null, ThreadPriority.NORMAL);
        thread.setMemberAlertSentAt(now.minusMinutes(31));
        assertTrue(MessageNotificationService.shouldAlertMember(thread, now));
    }

    @Test
    void escalationEmailsStaffOnce() {
        ConversationThread thread = thread(null, ThreadPriority.URGENT);
        service.notifyStaffOfEscalation(thread);
        service.notifyStaffOfEscalation(thread);
        verify(emailService, times(1)).sendPlain(eq("admin@example.test"), any(),
                eq("[URGENT] MSG-000123 escalated by Brian Wafula"), any());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private User user(String email, UserRole role) {
        User u = User.builder().email(email).firstName(role == UserRole.MEMBER ? "Brian" : "Staff")
                .lastName(role == UserRole.MEMBER ? "Wafula" : "User").role(role).build();
        u.setId(UUID.randomUUID());
        return u;
    }

    private ConversationThread thread(Program program, ThreadPriority priority) {
        ConversationThread t = ConversationThread.builder()
                .member(member).program(program).priority(priority).referenceNumber("MSG-000123").build();
        t.setId(UUID.randomUUID());
        return t;
    }

    private ConversationMessage message(boolean fromMember) {
        return ConversationMessage.builder().fromMember(fromMember).body("hello").build();
    }
}
