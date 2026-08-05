package com.mdau.ushirika.module.forum.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.forum.dto.*;
import com.mdau.ushirika.module.forum.entity.ForumPost;
import com.mdau.ushirika.module.forum.enums.ForumPostStatus;
import com.mdau.ushirika.module.forum.repository.ForumPostRepository;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForumPostService {

    private final ForumPostRepository forumPostRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.site-url:https://ushirikacommunity.site}")
    private String siteUrl;

    // ── Member: submit story ─────────────────────────────────────────────────────

    @Transactional
    public ForumPostDto submit(SubmitForumPostRequest req) {
        User member = currentUser();
        MemberProfile profile = memberProfileRepository.findByUser(member).orElse(null);

        ForumPost post = ForumPost.builder()
                .member(member)
                .title(req.title())
                .body(req.body())
                .mediaUrls(req.mediaUrls() != null ? req.mediaUrls() : List.of())
                .videoUrl(req.videoUrl())
                .status(ForumPostStatus.PENDING)
                .build();

        forumPostRepository.save(post);
        log.info("Forum post submitted: id={} member={}", post.getId(), member.getEmail());

        return ForumPostDto.from(post, profile);
    }

    // ── Member: my posts ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ForumPostDto> myPosts() {
        User member = currentUser();
        MemberProfile profile = memberProfileRepository.findByUser(member).orElse(null);
        return forumPostRepository.findAllByMemberOrderByCreatedAtDesc(member)
                .stream()
                .map(p -> ForumPostDto.from(p, profile))
                .toList();
    }

    // ── Admin: list all (with optional status filter) ────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<ForumPostDto> listAll(ForumPostStatus status, Pageable pageable) {
        if (status != null) {
            return PagedResponse.of(
                forumPostRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                    .map(p -> ForumPostDto.from(p, memberProfileRepository.findByUser(p.getMember()).orElse(null)))
            );
        }
        return PagedResponse.of(
            forumPostRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(p -> ForumPostDto.from(p, memberProfileRepository.findByUser(p.getMember()).orElse(null)))
        );
    }

    // ── Admin: approve ───────────────────────────────────────────────────────────

    @Transactional
    public ForumPostDto approve(UUID id) {
        User admin = currentUser();
        ForumPost post = findById(id);

        if (post.getStatus() == ForumPostStatus.APPROVED) {
            throw new BadRequestException("This post is already approved.");
        }

        post.setStatus(ForumPostStatus.APPROVED);
        post.setReviewedBy(admin);
        post.setReviewedAt(LocalDateTime.now());
        post.setApprovedAt(LocalDateTime.now());
        forumPostRepository.save(post);

        log.info("Forum post approved: id={} by={}", id, admin.getEmail());
        sendApprovedEmail(post);

        MemberProfile profile = memberProfileRepository.findByUser(post.getMember()).orElse(null);
        return ForumPostDto.from(post, profile);
    }

    // ── Admin: reject ────────────────────────────────────────────────────────────

    @Transactional
    public ForumPostDto reject(UUID id, String adminNotes) {
        User admin = currentUser();
        ForumPost post = findById(id);

        if (post.getStatus() == ForumPostStatus.REJECTED) {
            throw new BadRequestException("This post is already rejected.");
        }

        post.setStatus(ForumPostStatus.REJECTED);
        post.setAdminNotes(adminNotes);
        post.setReviewedBy(admin);
        post.setReviewedAt(LocalDateTime.now());
        forumPostRepository.save(post);

        log.info("Forum post rejected: id={} by={}", id, admin.getEmail());
        sendRejectedEmail(post, adminNotes);

        MemberProfile profile = memberProfileRepository.findByUser(post.getMember()).orElse(null);
        return ForumPostDto.from(post, profile);
    }

    // ── Admin: edit (before or after approval) ───────────────────────────────────

    @Transactional
    public ForumPostDto edit(UUID id, AdminEditForumPostRequest req) {
        ForumPost post = findById(id);

        post.setTitle(req.title());
        post.setBody(req.body());
        post.setMediaUrls(req.mediaUrls() != null ? req.mediaUrls() : List.of());
        post.setVideoUrl(req.videoUrl());
        post.setAdminNotes(req.adminNotes());
        post.setFeatured(req.featured());
        forumPostRepository.save(post);

        log.info("Forum post edited by admin: id={}", id);

        MemberProfile profile = memberProfileRepository.findByUser(post.getMember()).orElse(null);
        return ForumPostDto.from(post, profile);
    }

    // ── Admin: toggle featured ───────────────────────────────────────────────────

    @Transactional
    public ForumPostDto toggleFeatured(UUID id) {
        ForumPost post = findById(id);

        if (post.getStatus() != ForumPostStatus.APPROVED) {
            throw new BadRequestException("Only approved posts can be featured.");
        }

        post.setFeatured(!post.isFeatured());
        forumPostRepository.save(post);

        MemberProfile profile = memberProfileRepository.findByUser(post.getMember()).orElse(null);
        return ForumPostDto.from(post, profile);
    }

    // ── Public: approved posts (featured first, newest next) ────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<ForumPostDto> publicApproved(Pageable pageable) {
        return PagedResponse.of(
            forumPostRepository.findAllByStatusOrderByFeaturedDescApprovedAtDesc(
                    ForumPostStatus.APPROVED, pageable)
                .map(p -> ForumPostDto.from(p, memberProfileRepository.findByUser(p.getMember()).orElse(null)))
        );
    }

    // ── Emails ───────────────────────────────────────────────────────────────────

    private void sendApprovedEmail(ForumPost p) {
        String name  = p.getMember().getFullName();
        String email = p.getMember().getEmail();
        String url   = siteUrl + "/#stories";
        String html  = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#007834">Your Story Is Live!</h2>
              <p>Hi %s,</p>
              <p>Your testimonial <strong>"%s"</strong> has been approved and is now
                 visible to the public on the Ushirika Welfare Organization website.</p>
              <p>Thank you for sharing your story — it helps others see the real
                 impact our community makes.</p>
              <p>
                <a href="%s"
                   style="display:inline-block;background:#007834;color:#fff;padding:10px 22px;
                          border-radius:999px;text-decoration:none;font-weight:600">
                  View It Live
                </a>
              </p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>
            """.formatted(name, p.getTitle(), url);
        emailService.sendPlain(email, name, "Your Story Is Now Live — Ushirika Welfare Organization", html);
    }

    private void sendRejectedEmail(ForumPost p, String reason) {
        String name   = p.getMember().getFullName();
        String email  = p.getMember().getEmail();
        String portal = siteUrl + "/portal/forums";
        String html   = """
            <div style="font-family:sans-serif;max-width:520px;margin:auto">
              <h2 style="color:#B91C1C">Story Needs Revision</h2>
              <p>Hi %s,</p>
              <p>Your testimonial <strong>"%s"</strong> was not approved at this time.</p>
              <p><strong>Admin feedback:</strong> %s</p>
              <p>You are welcome to revise and re-submit your story through your member portal.</p>
              <p>
                <a href="%s"
                   style="display:inline-block;background:#007834;color:#fff;padding:10px 22px;
                          border-radius:999px;text-decoration:none;font-weight:600">
                  Go to My Stories
                </a>
              </p>
              <p>— Ushirika Welfare Organization Team</p>
            </div>
            """.formatted(name, p.getTitle(), reason != null ? reason : "No specific reason provided.", portal);
        emailService.sendPlain(email, name, "Story Under Review — Ushirika Welfare Organization", html);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private ForumPost findById(UUID id) {
        return forumPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Forum post not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
