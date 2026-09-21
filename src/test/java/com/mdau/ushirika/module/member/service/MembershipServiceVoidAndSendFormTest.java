package com.mdau.ushirika.module.member.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.auth.service.ActivationService;
import com.mdau.ushirika.module.member.dto.AdminApplicationDto;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.member.repository.MembershipApplicationRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the two gaps behind the "can't send form / can't delete a wrong-email application"
 * support incident: sendForm must fail with a plain message on an email OR phone collision
 * (not a raw DB constraint violation), and voidApplication must clear a duplicate + free the
 * account it created.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipServiceVoidAndSendFormTest {

    @Mock private MembershipApplicationRepository applicationRepository;
    @Mock private MemberProfileRepository profileRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.mdau.ushirika.module.audit.service.AuditLogService auditLogService;
    @Mock private com.mdau.ushirika.module.notification.service.EmailService emailService;
    @Mock private com.mdau.ushirika.module.dues.service.MembershipDuesService membershipDuesService;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private com.mdau.ushirika.module.payment.repository.PaymentBasketRepository paymentBasketRepository;
    @Mock private com.mdau.ushirika.module.member.repository.ApplicationApprovalRepository approvalRepository;
    @Mock private com.mdau.ushirika.module.program.service.ProgramApplicationService programApplicationService;
    @Mock private ActivationService activationService;

    private MembershipService service;

    @BeforeEach
    void setUp() {
        service = new MembershipService(
                applicationRepository, profileRepository, approvalRepository, userRepository,
                emailService, membershipDuesService, passwordEncoder, paymentBasketRepository,
                programApplicationService, auditLogService, activationService);

        when(activationService.issue(any())).thenReturn(new ActivationService.IssuedActivation(
                "raw-token", "123456", LocalDateTime.now().plusHours(72), LocalDateTime.now().plusMinutes(15)));
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID()); // what JPA does on a real save
            return saved;
        });

        User admin = User.builder().firstName("Ada").lastName("Admin").email("admin@ushirika.test")
                .role(UserRole.SUPERADMIN).build();
        when(userRepository.findByEmail("admin@ushirika.test")).thenReturn(Optional.of(admin));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@ushirika.test", "x"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MembershipApplication submittedPublicApp() {
        return MembershipApplication.builder()
                .referenceNumber("UWF-APP-TEST0001")
                .applicantName("Barbara Weke")
                .applicantEmail("barbaraweke@gmail.com")
                .applicantPhone("+16823006172")
                .status(ApplicationStatus.SUBMITTED)
                .build();
    }

    @Test
    void sendForm_phoneAlreadyTaken_throwsPlainConflict_notRawConstraintViolation() {
        UUID id = UUID.randomUUID();
        MembershipApplication app = submittedPublicApp();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(userRepository.existsByEmail("barbaraweke@gmail.com")).thenReturn(false);
        when(userRepository.existsByPhone("+16823006172")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.sendForm(id, true, true));
        assertTrue(ex.getMessage().contains("+16823006172"), ex.getMessage());
        assertTrue(ex.getMessage().toLowerCase().contains("phone"), ex.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendForm_emailAlreadyTaken_throwsPlainConflict() {
        UUID id = UUID.randomUUID();
        MembershipApplication app = submittedPublicApp();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(userRepository.existsByEmail("barbaraweke@gmail.com")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.sendForm(id, true, true));
        assertTrue(ex.getMessage().contains("barbaraweke@gmail.com"), ex.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendForm_success_issuesActivationAndNeverEmailsAPassword() {
        UUID id = UUID.randomUUID();
        MembershipApplication app = submittedPublicApp();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);

        service.sendForm(id, true, false);

        assertEquals(ApplicationStatus.FORM_SENT, app.getStatus());
        assertTrue(app.getUser().isMustSetPassword(), "new applicant must be flagged to choose a password");
        verify(activationService).issue(app.getUser());
        verify(emailService, never()).sendFormSentCredentials(any(), any(), any(), any());

        // Whatever arity of sendActivationInvite was used, the link and code must be in it.
        var invite = org.mockito.Mockito.mockingDetails(emailService).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("sendActivationInvite"))
                .findFirst().orElseThrow(() -> new AssertionError("no activation invite was sent"));
        Object[] args = invite.getArguments();
        assertEquals("barbaraweke@gmail.com", args[0]);
        assertTrue(((String) args[2]).endsWith("/activate?t=raw-token"), String.valueOf(args[2]));
        assertEquals("123456", args[3]);
    }

    @Test
    void voidApplication_formSentDuplicate_removesAccountAndVoids() {
        UUID id = UUID.randomUUID();
        User orphan = User.builder().firstName("Barbara").lastName("Weke")
                .email("wekebarbara@gmail.com").phone("+16823006172")
                .role(UserRole.APPLICANT).build();
        MembershipApplication app = MembershipApplication.builder()
                .referenceNumber("UWF-APP-TEST0002")
                .status(ApplicationStatus.FORM_SENT)
                .user(orphan)
                .build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));
        MemberProfile profile = MemberProfile.builder().user(orphan).build();
        when(profileRepository.findByUser(orphan)).thenReturn(Optional.of(profile));

        AdminApplicationDto result = service.voidApplication(id, true, "Duplicate — wrong email");

        assertEquals(ApplicationStatus.VOIDED, app.getStatus());
        assertNull(app.getUser());
        verify(profileRepository).delete(profile);
        verify(userRepository).delete(orphan);
        verify(auditLogService).logAbout(any(), eq("APPLICATION_VOIDED"), eq("MembershipApplication"),
                any(), any(), any(), any());
        assertNotNull(result);
    }

    @Test
    void voidApplication_approvedMember_refuses() {
        UUID id = UUID.randomUUID();
        MembershipApplication app = MembershipApplication.builder()
                .referenceNumber("UWF-APP-TEST0003")
                .status(ApplicationStatus.APPROVED)
                .build();
        when(applicationRepository.findById(id)).thenReturn(Optional.of(app));

        assertThrows(BadRequestException.class, () -> service.voidApplication(id, true, null));
        verify(userRepository, never()).delete(any(User.class));
    }
}
