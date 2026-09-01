package com.mdau.ushirika.module.content.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.content.dto.ArticleDto;
import com.mdau.ushirika.module.content.dto.ArticleRequest;
import com.mdau.ushirika.module.content.dto.ArticleSummaryDto;
import com.mdau.ushirika.module.content.entity.Article;
import com.mdau.ushirika.module.content.enums.ArticleStatus;
import com.mdau.ushirika.module.content.enums.ArticleType;
import com.mdau.ushirika.module.content.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    // ─────────────────────────────────────── Public

    @Transactional(readOnly = true)
    public PagedResponse<ArticleSummaryDto> listPublished(ArticleType type, Pageable pageable) {
        var page = type != null
                ? articleRepository.findAllByStatusAndTypeOrderByPublishedAtDesc(
                        ArticleStatus.PUBLISHED, type, pageable)
                : articleRepository.findAllByStatusOrderByPublishedAtDesc(
                        ArticleStatus.PUBLISHED, pageable);
        return PagedResponse.of(page.map(ArticleSummaryDto::from));
    }

    @Transactional(readOnly = true)
    public ArticleDto getPublishedBySlug(String slug) {
        return ArticleDto.from(
                articleRepository.findBySlugAndStatus(slug, ArticleStatus.PUBLISHED)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Article not found: " + slug)));
    }

    // ─────────────────────────────────────── Admin

    @Transactional(readOnly = true)
    public PagedResponse<ArticleSummaryDto> listAll(ArticleStatus status, Pageable pageable) {
        var page = status != null
                ? articleRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                : articleRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PagedResponse.of(page.map(ArticleSummaryDto::from));
    }

    @Transactional(readOnly = true)
    public ArticleDto getById(UUID id) {
        return ArticleDto.from(findById(id));
    }

    @Transactional
    public ArticleDto create(ArticleRequest req) {
        String slug = resolveSlug(req.slug(), req.title());

        Article article = Article.builder()
                .title(req.title())
                .slug(slug)
                .excerpt(req.excerpt())
                .type(req.type())
                .coverImageUrl(req.coverImageUrl())
                .coverImagePublicId(req.coverImagePublicId())
                .content(req.content() != null ? req.content() : new ArrayList<>())
                .tags(req.tags()    != null ? req.tags()    : new ArrayList<>())
                .build();

        Article saved = articleRepository.save(article);
        auditLogService.log(currentUser(), "ARTICLE_CREATED", "Article", saved.getId(),
                "Created article \"" + saved.getTitle() + "\"");
        return ArticleDto.from(saved);
    }

    @Transactional
    public ArticleDto update(UUID id, ArticleRequest req) {
        Article article = findById(id);

        String slug = req.slug() != null && !req.slug().isBlank()
                ? req.slug().toLowerCase().trim()
                : article.getSlug();

        // If slug changed, check uniqueness
        if (!slug.equals(article.getSlug()) && articleRepository.existsBySlug(slug)) {
            throw new ConflictException("Slug already in use: " + slug);
        }

        article.setTitle(req.title());
        article.setSlug(slug);
        article.setExcerpt(req.excerpt());
        article.setType(req.type());
        article.setCoverImageUrl(req.coverImageUrl());
        article.setCoverImagePublicId(req.coverImagePublicId());
        article.setContent(req.content() != null ? req.content() : new ArrayList<>());
        article.setTags(req.tags()    != null ? req.tags()    : new ArrayList<>());

        Article saved = articleRepository.save(article);
        auditLogService.log(currentUser(), "ARTICLE_UPDATED", "Article", saved.getId(),
                "Updated article \"" + saved.getTitle() + "\"");
        return ArticleDto.from(saved);
    }

    @Transactional
    public ArticleDto updateStatus(UUID id, ArticleStatus newStatus) {
        Article article = findById(id);

        if (newStatus == ArticleStatus.PUBLISHED && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        article.setStatus(newStatus);

        log.info("Article '{}' status → {}", article.getSlug(), newStatus);
        Article saved = articleRepository.save(article);
        auditLogService.log(currentUser(), "ARTICLE_STATUS_" + newStatus.name(), "Article", saved.getId(),
                "Article \"" + saved.getTitle() + "\" status changed to " + newStatus);
        return ArticleDto.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Article article = findById(id);
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            throw new BadRequestException(
                    "Cannot delete a published article. Archive it first.");
        }
        String title = article.getTitle();
        String slug = article.getSlug();
        articleRepository.delete(article);
        auditLogService.log(currentUser(), "ARTICLE_DELETED", "Article", id,
                "Deleted article \"" + title + "\"");
        log.info("Article deleted: id={} slug={}", id, slug);
    }

    // ─────────────────────────────────────── Private

    private Article findById(UUID id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }

    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9\\-]");
    private static final Pattern MULTI_DASH     = Pattern.compile("-{2,}");

    private String resolveSlug(String requested, String title) {
        String base = (requested != null && !requested.isBlank())
                ? requested.toLowerCase().trim()
                : slugify(title);

        base = NON_SLUG_CHARS.matcher(base).replaceAll("-");
        base = MULTI_DASH.matcher(base).replaceAll("-");
        base = base.replaceAll("^-|-$", "");

        if (!articleRepository.existsBySlug(base)) {
            return base;
        }
        // Append incrementing suffix until unique
        int suffix = 2;
        while (articleRepository.existsBySlug(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    private String slugify(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }
}
