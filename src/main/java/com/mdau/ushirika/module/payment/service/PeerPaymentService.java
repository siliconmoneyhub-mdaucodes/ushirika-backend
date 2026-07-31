package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.payment.dto.*;
import com.mdau.ushirika.module.payment.entity.MemberContribution;
import com.mdau.ushirika.module.payment.entity.PeerPayment;
import com.mdau.ushirika.module.payment.enums.ContributionSource;
import com.mdau.ushirika.module.payment.enums.PaymentMode;
import com.mdau.ushirika.module.payment.enums.PeerPaymentPurpose;
import com.mdau.ushirika.module.payment.enums.PeerPaymentStatus;
import com.mdau.ushirika.module.payment.repository.MemberContributionRepository;
import com.mdau.ushirika.module.payment.repository.PeerPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PeerPaymentService {

    private final PeerPaymentRepository peerPaymentRepository;
    private final MemberContributionRepository contributionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    // ── Member: report a payment ─────────────────────────────────────────────────

    @Transactional
    public PeerPaymentDto report(ReportPeerPaymentRequest req) {
        User member = currentUser();

        // Guard: same TX reference must never appear across any member — each TX is unique globally
        if (peerPaymentRepository.existsByMemberTxReferenceIgnoreCase(req.memberTxReference())) {
            throw new ConflictException(
                "This transaction reference has already been submitted. " +
                "Each transaction reference can only be used once. " +
                "If you believe this is an error, please contact support.");
        }

        PeerPayment payment = PeerPayment.builder()
                .member(member)
                .amount(req.amount())
                .paymentMode(req.paymentMode())
                .memberTxReference(req.memberTxReference().strip())
                .period(req.period())
                .notes(req.notes())
                .purpose(req.purpose() != null ? req.purpose() : PeerPaymentPurpose.DUES)
                .proofImageUrl(req.proofImageUrl())
                .status(PeerPaymentStatus.PENDING)
                .build();

        peerPaymentRepository.save(payment);

        log.info("Peer payment reported: id={} member={} mode={} amount={}",
                payment.getId(), member.getEmail(), req.paymentMode(), req.amount());

        return PeerPaymentDto.memberView(payment);
    }

    // ── Member: my reports ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<PeerPaymentDto> myReports(Pageable pageable) {
        User member = currentUser();
        return PagedResponse.of(
            peerPaymentRepository.findAllByMemberOrderByCreatedAtDesc(member, pageable)
                .map(PeerPaymentDto::memberView)
        );
    }

    // ── Admin: list all / by status ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<PeerPaymentDto> listAll(PeerPaymentStatus status, Pageable pageable) {
        if (status != null) {
            return PagedResponse.of(
                peerPaymentRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                    .map(PeerPaymentDto::from)
            );
        }
        return PagedResponse.of(
            peerPaymentRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(PeerPaymentDto::from)
        );
    }

    // ── Admin: verify ────────────────────────────────────────────────────────────

    /**
     * Admin enters the TX reference from their own Zelle/Venmo/CashApp app.
     * If it matches (case-insensitive) the member's reference, the payment is
     * verified and a MemberContribution is created.
     * If it does not match, a 400 is returned — admin can retry or reject.
     */
    @Transactional
    public PeerPaymentDto verify(UUID id, VerifyPeerPaymentRequest req) {
        User admin = currentUser();
        PeerPayment payment = findById(id);

        if (payment.getStatus() != PeerPaymentStatus.PENDING) {
            throw new BadRequestException(
                "Only PENDING reports can be verified. Current status: " + payment.getStatus());
        }

        // Two-sided match — case-insensitive, stripped of whitespace
        if (!payment.getMemberTxReference().strip()
                    .equalsIgnoreCase(req.adminTxReference().strip())) {
            throw new BadRequestException(
                "Transaction reference mismatch. The reference you entered (" +
                req.adminTxReference().strip() + ") does not match what the member reported. " +
                "Please check your " + label(payment.getPaymentMode()) + " app and try again, " +
                "or reject the report if the payment was not received.");
        }

        // Idempotency: guard against duplicate MemberContribution
        if (contributionRepository.existsByPeerPaymentId(id)) {
            throw new ConflictException("This report has already been processed.");
        }

        payment.setAdminTxReference(req.adminTxReference().strip());
        payment.setStatus(PeerPaymentStatus.VERIFIED);
        payment.setVerifiedBy(admin);
        payment.setVerifiedAt(LocalDateTime.now());
        peerPaymentRepository.save(payment);

        // Create confirmed contribution
        MemberContribution contribution = MemberContribution.builder()
                .member(payment.getMember())
                .peerPayment(payment)
                .source(ContributionSource.PEER)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .period(payment.getPeriod())
                .notes("Verified " + label(payment.getPaymentMode()) +
                       " payment — TX: " + payment.getMemberTxReference())
                .build();
        contributionRepository.save(contribution);

        log.info("Peer payment verified: id={} member={} mode={} amount={} by={}",
                id, payment.getMember().getEmail(),
                payment.getPaymentMode(), payment.getAmount(), admin.getEmail());

        sendVerifiedEmail(payment);

        return PeerPaymentDto.from(payment);
    }

    // ── Admin: reject ────────────────────────────────────────────────────────────

    @Transactional
    public PeerPaymentDto reject(UUID id, RejectPeerPaymentRequest req) {
        User admin = currentUser();
        PeerPayment payment = findById(id);

        if (payment.getStatus() != PeerPaymentStatus.PENDING) {
            throw new BadRequestException(
                "Only PENDING reports can be rejected. Current status: " + payment.getStatus());
        }

        payment.setStatus(PeerPaymentStatus.REJECTED);
        payment.setRejectionReason(req.reason());
        payment.setVerifiedBy(admin);
        payment.setVerifiedAt(LocalDateTime.now());
        peerPaymentRepository.save(payment);

        log.info("Peer payment rejected: id={} member={} reason={} by={}",
                id, payment.getMember().getEmail(), req.reason(), admin.getEmail());

        sendRejectedEmail(payment, req.reason());

        return PeerPaymentDto.from(payment);
    }

    // ── Emails ───────────────────────────────────────────────────────────────────

    private void sendVerifiedEmail(PeerPayment p) {
        String name   = p.getMember().getFullName();
        String email  = p.getMember().getEmail();
        String method = label(p.getPaymentMode());
        String amount = "$" + p.getAmount().toPlainString();
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#1A4731">Contribution Verified ✓</h2>
              <p>Hi %s,</p>
              <p>Your <strong>%s</strong> payment of <strong>%s</strong> has been
                 verified and recorded against your membership account.</p>
              <p style="color:#555">Transaction reference: <strong>%s</strong></p>
              <p>Thank you for your continued support of Ushirika Welfare DFW.</p>
              <p>— Ushirika Welfare Team</p>
            </div>
            """.formatted(name, method, amount, p.getMemberTxReference());
        emailService.sendPlain(email, name, "Your Contribution Has Been Verified — Ushirika Welfare", html);
    }

    private void sendRejectedEmail(PeerPayment p, String reason) {
        String name   = p.getMember().getFullName();
        String email  = p.getMember().getEmail();
        String method = label(p.getPaymentMode());
        String amount = "$" + p.getAmount().toPlainString();
        String portal = siteUrl + "/portal";
        String html = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#B91C1C">Payment Report Could Not Be Verified</h2>
              <p>Hi %s,</p>
              <p>Your <strong>%s</strong> payment report of <strong>%s</strong>
                 (transaction reference: <strong>%s</strong>) could not be verified.</p>
              <p><strong>Reason:</strong> %s</p>
              <p>If you have already sent the payment, please log in to your portal and
                 re-submit the correct transaction reference from your %s app.</p>
              <p>
                <a href="%s"
                   style="display:inline-block;background:#1A4731;color:#fff;padding:10px 22px;
                          border-radius:999px;text-decoration:none;font-weight:600">
                  Re-submit in Portal
                </a>
              </p>
              <p style="color:#888;font-size:13px">
                If you did not send a payment, please ignore this email.
                Contact <a href="mailto:info@ushirikacommunity.site">info@ushirikacommunity.site</a>
                if you need help.
              </p>
              <p>— Ushirika Welfare Team</p>
            </div>
            """.formatted(name, method, amount, p.getMemberTxReference(), reason, method, portal);
        emailService.sendPlain(email, name,
            "Action Required: Re-submit Your Payment Reference — Ushirika Welfare", html);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static String label(PaymentMode mode) {
        return switch (mode) {
            case ZELLE   -> "Zelle";
            case VENMO   -> "Venmo";
            case CASHAPP -> "CashApp";
        };
    }

    private PeerPayment findById(UUID id) {
        return peerPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment report not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
