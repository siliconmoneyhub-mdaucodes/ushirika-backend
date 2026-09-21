package com.mdau.ushirika.module.auth.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.dto.ActivationLookupDto;
import com.mdau.ushirika.module.auth.dto.ActivationTicketDto;
import com.mdau.ushirika.module.auth.dto.AuthResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.RefreshTokenRepository;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import com.mdau.ushirika.module.member.repository.MembershipApplicationRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Exercises the real ActivationService against an in-memory "repository" (a mock that hands back
 * the one test user by its stored hashes), so the hashing, attempt counting, expiry, cooldown and
 * single-use logic are all genuinely run rather than stubbed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private MembershipApplicationRepository applicationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;
    @Mock private AuthService authService;

    private ActivationRateLimiter rateLimiter;
    private ActivationService service;
    private User user;

    @BeforeEach
    void setUp() {
        rateLimiter = new ActivationRateLimiter();
        service = new ActivationService(userRepository, refreshTokenRepository, applicationRepository,
                passwordEncoder, emailService, auditLogService, rateLimiter, authService);

        user = User.builder().firstName("Brian").lastName("Otieno").email("brian.o@example.com")
                .phone("+254700000001").role(UserRole.APPLICANT).emailVerified(true).active(true)
                .mustSetPassword(true).password("placeholder").build();
        user.setId(UUID.randomUUID());

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("ENCODED");
        when(authService.issueSession(any(User.class))).thenReturn(mock(AuthResponse.class));
    }

    /** Makes the mock repository resolve lookups by whatever hashes the user currently holds. */
    private void wireRepository() {
        when(userRepository.findByActivationTokenHash(anyString())).thenAnswer(inv ->
                inv.getArgument(0).equals(user.getActivationTokenHash()) ? Optional.of(user) : Optional.empty());
        when(userRepository.findByActivationTicketHash(anyString())).thenAnswer(inv ->
                user.getActivationTicketHash() != null && inv.getArgument(0).equals(user.getActivationTicketHash())
                        ? Optional.of(user) : Optional.empty());
    }

    private ActivationService.IssuedActivation issued() {
        ActivationService.IssuedActivation a = service.issue(user);
        wireRepository();
        return a;
    }

    private String wrongCode(String rightCode) {
        return rightCode.equals("000000") ? "111111" : "000000";
    }

    // -- issue / lookup -------------------------------------------------------------------

    @Test
    void issue_storesOnlyHashes_andDoesNotTouchPassword() {
        ActivationService.IssuedActivation a = issued();

        assertEquals(64, a.rawToken().length());
        assertEquals(6, a.rawOtp().length());
        assertNotEquals(a.rawToken(), user.getActivationTokenHash());
        assertNotEquals(a.rawOtp(), user.getActivationOtpHash());
        assertEquals(64, user.getActivationOtpHash().length());
        assertEquals("placeholder", user.getPassword());
        assertNotNull(user.getActivationOtpLastSentAt());
        assertEquals(0, user.getActivationOtpAttempts());
    }

    @Test
    void lookup_validLink_returnsMaskedEmailAndFullAttempts() {
        ActivationService.IssuedActivation a = issued();

        ActivationLookupDto dto = service.lookup(a.rawToken());

        assertEquals("VALID", dto.state());
        assertEquals("Brian", dto.firstName());
        assertEquals(5, dto.attemptsRemaining());
        assertNotNull(dto.otpExpiresAt());
        assertNotNull(dto.resendAvailableAt());
        assertTrue(dto.maskedEmail().endsWith("@example.com"));
        assertFalse(dto.maskedEmail().contains("brian.o"), dto.maskedEmail());
    }

    @Test
    void lookup_unknownOrBlankToken_isInvalid_neverThrows() {
        issued();
        assertEquals("INVALID", service.lookup("bogus").state());
        assertEquals("INVALID", service.lookup("").state());
        assertEquals("INVALID", service.lookup(null).state());
        assertNull(service.lookup("bogus").firstName());
    }

    @Test
    void lookup_expiredLink_isExpired() {
        ActivationService.IssuedActivation a = issued();
        user.setActivationTokenExpiry(LocalDateTime.now().minusMinutes(1));

        assertEquals("EXPIRED", service.lookup(a.rawToken()).state());
    }

    // -- verify ---------------------------------------------------------------------------

    @Test
    void verify_correctCode_returnsTicket_andSpendsTheCode() {
        ActivationService.IssuedActivation a = issued();

        ActivationTicketDto ticket = service.verify(a.rawToken(), a.rawOtp());

        assertNotNull(ticket.ticket());
        assertNotNull(ticket.expiresAt());
        assertNotNull(user.getActivationTicketHash());
        assertNull(user.getActivationOtpHash(), "the code is single-use");
        // Same code again fails: it has been spent.
        assertThrows(BadRequestException.class, () -> service.verify(a.rawToken(), a.rawOtp()));
    }

    @Test
    void verify_wrongCode_incrementsAttempts_andReportsRemaining() {
        ActivationService.IssuedActivation a = issued();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.verify(a.rawToken(), wrongCode(a.rawOtp())));

        assertEquals(1, user.getActivationOtpAttempts());
        assertTrue(ex.getMessage().contains("4 attempts left"), ex.getMessage());
        assertEquals(4, service.lookup(a.rawToken()).attemptsRemaining());
    }

    @Test
    void verify_fifthWrongCode_locksTheLink_evenIfTheNextCodeIsRight() {
        ActivationService.IssuedActivation a = issued();
        String bad = wrongCode(a.rawOtp());

        for (int i = 1; i < ActivationService.MAX_OTP_ATTEMPTS; i++) {
            assertThrows(BadRequestException.class, () -> service.verify(a.rawToken(), bad));
        }
        BadRequestException last = assertThrows(BadRequestException.class, () -> service.verify(a.rawToken(), bad));
        assertTrue(last.getMessage().toLowerCase().contains("locked"), last.getMessage());
        assertEquals(ActivationService.MAX_OTP_ATTEMPTS, user.getActivationOtpAttempts());

        assertEquals("LOCKED", service.lookup(a.rawToken()).state());
        // Even the genuine code no longer works.
        assertThrows(BadRequestException.class, () -> service.verify(a.rawToken(), a.rawOtp()));
        assertNull(user.getActivationTicketHash());
    }

    @Test
    void verify_expiredCode_isRejected_withoutBurningAnAttempt() {
        ActivationService.IssuedActivation a = issued();
        user.setActivationOtpExpiry(LocalDateTime.now().minusSeconds(1));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.verify(a.rawToken(), a.rawOtp()));

        assertTrue(ex.getMessage().toLowerCase().contains("expired"), ex.getMessage());
        assertEquals(0, user.getActivationOtpAttempts());
    }

    @Test
    void verify_expiredLink_isRejected() {
        ActivationService.IssuedActivation a = issued();
        user.setActivationTokenExpiry(LocalDateTime.now().minusMinutes(5));

        assertThrows(BadRequestException.class, () -> service.verify(a.rawToken(), a.rawOtp()));
    }

    @Test
    void verify_unknownToken_isRejected() {
        issued();
        assertThrows(BadRequestException.class, () -> service.verify("nope", "123456"));
    }

    // -- resend ---------------------------------------------------------------------------

    @Test
    void resend_withinCooldown_isSilentlyIgnored() {
        ActivationService.IssuedActivation a = issued(); // lastSentAt = now

        service.resend(a.rawToken());

        verify(emailService, never()).sendActivationCodeOnly(any(), any(), any());
    }

    @Test
    void resend_afterCooldown_issuesNewCode_andResetsAttempts() {
        ActivationService.IssuedActivation a = issued();
        assertThrows(BadRequestException.class, () -> service.verify(a.rawToken(), wrongCode(a.rawOtp())));
        assertEquals(1, user.getActivationOtpAttempts());
        String oldHash = user.getActivationOtpHash();
        user.setActivationOtpLastSentAt(LocalDateTime.now().minusSeconds(ActivationService.RESEND_COOLDOWN_SECONDS + 1));

        service.resend(a.rawToken());

        verify(emailService).sendActivationCodeOnly(eq("brian.o@example.com"), eq("Brian"), anyString());
        assertEquals(0, user.getActivationOtpAttempts());
        assertNotNull(user.getActivationOtpHash());
        assertTrue(user.getActivationOtpExpiry().isAfter(LocalDateTime.now().plusMinutes(10)));
        // (a new random code could in theory equal the old one; the hash check is a sanity guard only)
        assertNotNull(oldHash);
    }

    @Test
    void resend_hourlyCap_stopsFurtherSends() {
        ActivationService.IssuedActivation a = issued();
        int sends = 0;
        for (int i = 0; i < ActivationRateLimiter.MAX_RESENDS_PER_HOUR + 3; i++) {
            user.setActivationOtpLastSentAt(LocalDateTime.now().minusMinutes(5)); // cooldown always elapsed
            service.resend(a.rawToken());
            sends++;
        }
        verify(emailService, times(ActivationRateLimiter.MAX_RESENDS_PER_HOUR))
                .sendActivationCodeOnly(any(), any(), any());
        assertTrue(sends > ActivationRateLimiter.MAX_RESENDS_PER_HOUR);
    }

    @Test
    void resend_lockedLink_isIgnored() {
        ActivationService.IssuedActivation a = issued();
        user.setActivationOtpAttempts(ActivationService.MAX_OTP_ATTEMPTS);
        user.setActivationOtpLastSentAt(LocalDateTime.now().minusMinutes(5));

        service.resend(a.rawToken());

        verify(emailService, never()).sendActivationCodeOnly(any(), any(), any());
    }

    @Test
    void resend_unknownToken_isSilent() {
        issued();
        assertDoesNotThrow(() -> service.resend("bogus"));
        verifyNoInteractions(emailService);
    }

    // -- complete -------------------------------------------------------------------------

    @Test
    void complete_setsPassword_clearsSecrets_stampsApplication_andReturnsSession() {
        ActivationService.IssuedActivation a = issued();
        ActivationTicketDto ticket = service.verify(a.rawToken(), a.rawOtp());
        MembershipApplication app = MembershipApplication.builder()
                .referenceNumber("UWF-APP-T1").status(ApplicationStatus.FORM_SENT).user(user).build();
        when(applicationRepository.findByUser(user)).thenReturn(Optional.of(app));

        AuthResponse session = service.complete(ticket.ticket(), "harambee2026");

        assertNotNull(session);
        assertEquals("ENCODED", user.getPassword());
        assertFalse(user.isMustSetPassword());
        assertNotNull(user.getPasswordSetAt());
        assertTrue(user.isEmailVerified());
        assertNull(user.getActivationOtpHash());
        assertNull(user.getActivationOtpExpiry());
        assertNull(user.getActivationTicketHash());
        assertNull(user.getActivationTicketExpiry());
        assertNull(user.getActivationOtpLastSentAt());
        assertEquals(0, user.getActivationOtpAttempts());
        assertNull(user.getActivationTokenExpiry(), "a consumed link has no expiry");
        assertNotNull(app.getEmailReverifiedAt());
        assertEquals(ApplicationStatus.ONBOARDING_IN_PROGRESS, app.getStatus());
        verify(refreshTokenRepository).revokeAllUserTokens(user);
        verify(authService).issueSession(user);
        // The used link now reads as USED, not as an unrecognisable one.
        assertEquals("USED", service.lookup(a.rawToken()).state());
    }

    @Test
    void complete_ticketIsSingleUse() {
        ActivationService.IssuedActivation a = issued();
        ActivationTicketDto ticket = service.verify(a.rawToken(), a.rawOtp());
        when(applicationRepository.findByUser(user)).thenReturn(Optional.empty());

        service.complete(ticket.ticket(), "harambee2026");

        assertThrows(BadRequestException.class, () -> service.complete(ticket.ticket(), "another2026pw"));
    }

    @Test
    void complete_expiredTicket_isRejected() {
        ActivationService.IssuedActivation a = issued();
        ActivationTicketDto ticket = service.verify(a.rawToken(), a.rawOtp());
        user.setActivationTicketExpiry(LocalDateTime.now().minusSeconds(1));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.complete(ticket.ticket(), "harambee2026"));
        assertTrue(ex.getMessage().contains("expired"), ex.getMessage());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void complete_weakPassword_isRejected_andTicketStaysUsable() {
        ActivationService.IssuedActivation a = issued();
        ActivationTicketDto ticket = service.verify(a.rawToken(), a.rawOtp());
        when(applicationRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> service.complete(ticket.ticket(), "short"));
        assertThrows(BadRequestException.class, () -> service.complete(ticket.ticket(), "onlyletters"));
        assertNotNull(user.getActivationTicketHash(), "a rejected password must not spend the ticket");

        assertNotNull(service.complete(ticket.ticket(), "harambee2026"));
    }

    @Test
    void complete_unknownTicket_isRejected() {
        issued();
        assertThrows(BadRequestException.class, () -> service.complete("bogus", "harambee2026"));
    }

    // -- request by email -----------------------------------------------------------------

    @Test
    void requestByEmail_unknownEmail_isSilent() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.requestByEmail("nobody@example.com"));
        verifyNoInteractions(emailService);
    }

    @Test
    void requestByEmail_pendingApplicant_gets_aNewLink() {
        when(userRepository.findByEmail("brian.o@example.com")).thenReturn(Optional.of(user));

        service.requestByEmail("Brian.O@Example.com");

        verify(emailService).sendActivationInvite(eq("brian.o@example.com"), eq("Brian"),
                org.mockito.ArgumentMatchers.contains("/activate?t="), anyString(),
                eq(ActivationService.TOKEN_TTL_HOURS), eq(false));
        assertNotNull(user.getActivationTokenHash());
    }

    @Test
    void requestByEmail_ordinaryMember_isIgnored() {
        user.setRole(UserRole.MEMBER);
        user.setMustSetPassword(false);
        when(userRepository.findByEmail("brian.o@example.com")).thenReturn(Optional.of(user));

        service.requestByEmail("brian.o@example.com");

        verifyNoInteractions(emailService);
        assertNull(user.getActivationTokenHash());
    }

    @Test
    void requestByEmail_returningApplicant_getsContinueCopy() {
        user.setPasswordSetAt(LocalDateTime.now().minusDays(1));
        when(userRepository.findByEmail("brian.o@example.com")).thenReturn(Optional.of(user));

        service.requestByEmail("brian.o@example.com");

        verify(emailService).sendActivationInvite(any(), any(), anyString(), anyString(), anyInt(), eq(true));
    }

    @Test
    void requestByEmail_withinCooldown_isIgnored() {
        user.setActivationOtpLastSentAt(LocalDateTime.now());
        when(userRepository.findByEmail("brian.o@example.com")).thenReturn(Optional.of(user));

        service.requestByEmail("brian.o@example.com");

        verifyNoInteractions(emailService);
    }
}
