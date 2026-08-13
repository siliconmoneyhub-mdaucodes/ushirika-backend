package com.mdau.ushirika.module.donation.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.audit.enums.LedgerDirection;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.donation.dto.*;
import com.mdau.ushirika.module.donation.entity.Donation;
import com.mdau.ushirika.module.donation.entity.DonationCampaign;
import com.mdau.ushirika.module.donation.enums.CampaignStatus;
import com.mdau.ushirika.module.donation.enums.DonationStatus;
import com.mdau.ushirika.module.donation.repository.DonationCampaignRepository;
import com.mdau.ushirika.module.donation.repository.DonationRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import com.mdau.ushirika.module.payment.service.StripeService;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DonationService {

    private final DonationRepository donationRepository;
    private final DonationCampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final StripeService stripeService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    // ─────────────────────────────────────── Campaigns — public/member

    @Transactional(readOnly = true)
    public PagedResponse<CampaignDto> listPublicCampaigns(Pageable pageable) {
        return PagedResponse.of(
                campaignRepository.findAllByStatusAndIsPublicTrueOrderByCreatedAtDesc(
                        CampaignStatus.ACTIVE, pageable)
                        .map(c -> CampaignDto.from(c, campaignRepository.sumCompletedDonations(c.getId()))));
    }

    @Transactional(readOnly = true)
    public CampaignDto getPublicCampaign(UUID id) {
        DonationCampaign campaign = findCampaignById(id);
        if (campaign.getStatus() != CampaignStatus.ACTIVE || !campaign.isPublic()) {
            throw new ResourceNotFoundException("Campaign not found.");
        }
        return CampaignDto.from(campaign, campaignRepository.sumCompletedDonations(id));
    }

    // ─────────────────────────────────────── Campaigns — admin

    @Transactional(readOnly = true)
    public PagedResponse<CampaignDto> listAllCampaigns(Pageable pageable) {
        return PagedResponse.of(campaignRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(c -> CampaignDto.from(c, campaignRepository.sumCompletedDonations(c.getId()))));
    }

    @Transactional
    public CampaignDto createCampaign(CampaignRequest req) {
        DonationCampaign campaign = DonationCampaign.builder()
                .title(req.title())
                .description(req.description())
                .goalAmount(req.goalAmount())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .coverImageUrl(req.coverImageUrl())
                .isPublic(req.isPublic())
                .build();
        return CampaignDto.from(campaignRepository.save(campaign), BigDecimal.ZERO);
    }

    @Transactional
    public CampaignDto updateCampaign(UUID id, CampaignRequest req) {
        DonationCampaign campaign = findCampaignById(id);
        campaign.setTitle(req.title());
        campaign.setDescription(req.description());
        campaign.setGoalAmount(req.goalAmount());
        campaign.setStartDate(req.startDate());
        campaign.setEndDate(req.endDate());
        campaign.setCoverImageUrl(req.coverImageUrl());
        campaign.setPublic(req.isPublic());
        return CampaignDto.from(campaignRepository.save(campaign),
                campaignRepository.sumCompletedDonations(id));
    }

    @Transactional
    public CampaignDto updateCampaignStatus(UUID id, CampaignStatus newStatus) {
        DonationCampaign campaign = findCampaignById(id);
        campaign.setStatus(newStatus);
        return CampaignDto.from(campaignRepository.save(campaign),
                campaignRepository.sumCompletedDonations(id));
    }

    // ─────────────────────────────────────── Donations — initialize

    /**
     * Guest donation init — no authentication required.
     * donorName and donorEmail must be supplied in the request.
     */
    @Transactional
    public DonationInitResponse initializeGuest(DonationInitRequest req) {
        if (req.donorEmail() == null || req.donorEmail().isBlank()) {
            throw new BadRequestException("Donor email is required for guest donations.");
        }
        if (req.donorName() == null || req.donorName().isBlank()) {
            throw new BadRequestException("Donor name is required for guest donations.");
        }
        return initialize(null, req.donorName(), req.donorEmail(), req);
    }

    /**
     * Member donation init — uses authenticated user's name and email by default.
     * Optionally overridden by request fields (e.g. donating on behalf of someone).
     */
    @Transactional
    public DonationInitResponse initializeMember(DonationInitRequest req) {
        User member = currentUser();
        String name  = (req.donorName()  != null && !req.donorName().isBlank())
                ? req.donorName()  : member.getFullName();
        String email = (req.donorEmail() != null && !req.donorEmail().isBlank())
                ? req.donorEmail() : member.getEmail();
        return initialize(member, name, email, req);
    }

    private DonationInitResponse initialize(User donor, String name, String email, DonationInitRequest req) {
        DonationCampaign campaign = null;
        if (req.campaignId() != null) {
            campaign = findCampaignById(req.campaignId());
            if (campaign.getStatus() != CampaignStatus.ACTIVE) {
                throw new BadRequestException("This campaign is not currently accepting donations.");
            }
            if (!campaign.isPublic() && donor == null) {
                throw new BadRequestException("This campaign is for Ushirika members only.");
            }
        }

        // Persist the donation first so we have its ID for Stripe metadata
        Donation donation = Donation.builder()
                .campaign(campaign)
                .donor(donor)
                .donorName(name)
                .donorEmail(email)
                .amount(req.amount())
                .currency("USD")
                .message(req.message())
                .stripeSessionId("pending") // placeholder — updated after session creation
                .successUrl(req.successUrl())
                .build();
        donationRepository.save(donation);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("purpose", "DONATION");
        metadata.put("donationId", donation.getId().toString());
        metadata.put("campaignId", campaign != null ? campaign.getId().toString() : "general");
        metadata.put("donorName", name);

        String productName = campaign != null
                ? "Ushirika Welfare Organization — " + campaign.getTitle()
                : "Ushirika Welfare Organization — General Donation";

        StripeService.StripeCheckoutResult result = stripeService.createCheckoutSession(
                email, req.amount(), productName, req.successUrl(), req.cancelUrl(), metadata);

        // Now update with real session ID
        donation.setStripeSessionId(result.sessionId());
        donationRepository.save(donation);

        log.info("Donation checkout created: sessionId={} amount={} USD campaign={} donor={}",
                result.sessionId(), req.amount(),
                campaign != null ? campaign.getTitle() : "general", email);

        return new DonationInitResponse(result.sessionId(), result.checkoutUrl());
    }

    // ─────────────────────────────────────── Webhook handler

    /**
     * Called by StripeWebhookController when checkout.session.completed arrives with purpose=DONATION.
     * Must be idempotent — Stripe may deliver the same event more than once.
     */
    @Transactional
    public void handleSessionCompleted(Session session) {
        String sessionId = session.getId();
        Donation donation = donationRepository.findByStripeSessionId(sessionId).orElse(null);

        if (donation == null) {
            log.warn("Donation webhook: no Donation found for sessionId={}", sessionId);
            return;
        }
        if (donation.getStatus() == DonationStatus.COMPLETED) {
            log.info("Donation webhook: already processed sessionId={}", sessionId);
            return;
        }

        BigDecimal amountUsd = BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100));

        donation.setStatus(DonationStatus.COMPLETED);
        donation.setAmount(amountUsd);
        donation.setDonatedAt(LocalDateTime.now());
        donationRepository.save(donation);

        emailService.sendPlain(
                donation.getDonorEmail(), donation.getDonorName(),
                "Thank You for Your Donation — Ushirika Welfare Organization",
                "Dear " + firstWord(donation.getDonorName()) + ",\n\n" +
                "We have received your donation of USD " + amountUsd +
                (donation.getCampaign() != null
                        ? " towards \"" + donation.getCampaign().getTitle() + "\""
                        : " (General Donation)") + ".\n\n" +
                "Your generosity makes a real difference to our community. Thank you!\n\n" +
                "Warmly,\nUshirika Welfare Organization"
        );

        log.info("Donation completed via Stripe: sessionId={} amount={} USD donor={}",
                sessionId, amountUsd, donation.getDonorEmail());

        // AuditLog.actorId is NOT NULL, and a donor need not be a registered platform User
        // (donorName/donorEmail are plain fields precisely because anonymous/guest donations are
        // allowed) -- only member donations can be ledger-logged today. Anonymous donations still
        // complete and email-confirm normally, they just don't yet appear in Money In & Out /
        // per-program totals (Finance Visibility plan, Phases 2/4); a known, documented gap rather
        // than a silent one.
        if (donation.getDonor() != null) {
            auditLogService.log(donation.getDonor(), "DONATION_COMPLETED", "DONATION", donation.getId(),
                    "Donation of $" + amountUsd + " from " + donation.getDonorName()
                            + (donation.getCampaign() != null ? " towards \"" + donation.getCampaign().getTitle() + "\"" : ""),
                    amountUsd, LedgerDirection.IN);
        }
    }

    // ─────────────────────────────────────── Queries — member

    @Transactional(readOnly = true)
    public PagedResponse<DonationDto> myDonations(Pageable pageable) {
        User member = currentUser();
        return PagedResponse.of(donationRepository.findAllByDonorOrderByCreatedAtDesc(member, pageable)
                .map(DonationDto::from));
    }

    // ─────────────────────────────────────── Queries — admin

    @Transactional(readOnly = true)
    public PagedResponse<DonationDto> listAllDonations(DonationStatus status, Pageable pageable) {
        var page = status != null
                ? donationRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                : donationRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PagedResponse.of(page.map(DonationDto::from));
    }

    @Transactional(readOnly = true)
    public PagedResponse<DonationDto> listCampaignDonations(
            UUID campaignId, DonationStatus status, Pageable pageable) {
        DonationCampaign campaign = findCampaignById(campaignId);
        var page = status != null
                ? donationRepository.findAllByCampaignAndStatusOrderByCreatedAtDesc(campaign, status, pageable)
                : donationRepository.findAllByCampaignOrderByCreatedAtDesc(campaign, pageable);
        return PagedResponse.of(page.map(DonationDto::from));
    }

    // ─────────────────────────────────────── Private

    private DonationCampaign findCampaignById(UUID id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found: " + id));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }

    private String firstWord(String name) {
        if (name == null || name.isBlank()) return "Friend";
        return name.split(" ")[0];
    }
}
