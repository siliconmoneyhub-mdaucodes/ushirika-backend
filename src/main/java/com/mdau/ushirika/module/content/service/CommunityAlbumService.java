package com.mdau.ushirika.module.content.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.content.dto.*;
import com.mdau.ushirika.module.content.entity.AlbumMedia;
import com.mdau.ushirika.module.content.entity.CommunityAlbum;
import com.mdau.ushirika.module.content.enums.AlbumStatus;
import com.mdau.ushirika.module.content.repository.AlbumMediaRepository;
import com.mdau.ushirika.module.content.repository.CommunityAlbumRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class CommunityAlbumService {

    private final CommunityAlbumRepository albumRepository;
    private final AlbumMediaRepository     mediaRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final Cloudinary cloudinary;
    private final boolean devMode;

    public CommunityAlbumService(
            CommunityAlbumRepository albumRepository,
            AlbumMediaRepository mediaRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            @Value("${app.cloudinary.cloud-name:NOT_SET}") String cloudName,
            @Value("${app.cloudinary.api-key:NOT_SET}")    String apiKey,
            @Value("${app.cloudinary.api-secret:NOT_SET}") String apiSecret
    ) {
        this.albumRepository = albumRepository;
        this.mediaRepository = mediaRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.devMode = "NOT_SET".equals(cloudName) || "NOT_SET".equals(apiKey);
        this.cloudinary = devMode
                ? new Cloudinary()
                : new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", cloudName,
                        "api_key",    apiKey,
                        "api_secret", apiSecret,
                        "secure",     true
                  ));
        if (devMode) log.warn("[Cloudinary DEV] Album image deletes will be simulated.");
    }

    // ─────────────────────────────────────── Public

    @Transactional(readOnly = true)
    public PagedResponse<AlbumSummaryDto> listPublished(Pageable pageable) {
        return PagedResponse.of(
                albumRepository.findAllByStatusOrderByEventDateDesc(AlbumStatus.PUBLISHED, pageable)
                               .map(AlbumSummaryDto::from));
    }

    @Transactional(readOnly = true)
    public CommunityAlbumDto getPublished(UUID id) {
        CommunityAlbum album = findById(id);
        if (album.getStatus() != AlbumStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Album not found: " + id);
        }
        return CommunityAlbumDto.from(album);
    }

    // ─────────────────────────────────────── Admin

    @Transactional(readOnly = true)
    public PagedResponse<AlbumSummaryDto> listAll(AlbumStatus status, Pageable pageable) {
        var page = status != null
                ? albumRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                : albumRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PagedResponse.of(page.map(AlbumSummaryDto::from));
    }

    @Transactional(readOnly = true)
    public CommunityAlbumDto getById(UUID id) {
        return CommunityAlbumDto.from(findById(id));
    }

    @Transactional
    public CommunityAlbumDto create(CommunityAlbumRequest req) {
        CommunityAlbum album = CommunityAlbum.builder()
                .title(req.title())
                .description(req.description())
                .coverImageUrl(req.coverImageUrl())
                .coverImagePublicId(req.coverImagePublicId())
                .eventDate(req.eventDate())
                .location(req.location())
                .build();
        CommunityAlbum saved = albumRepository.save(album);
        auditLogService.log(currentUser(), "ALBUM_CREATED", "CommunityAlbum", saved.getId(),
                "Created album \"" + saved.getTitle() + "\"");
        return CommunityAlbumDto.from(saved);
    }

    @Transactional
    public CommunityAlbumDto update(UUID id, CommunityAlbumRequest req) {
        CommunityAlbum album = findById(id);
        String oldCoverPublicId = album.getCoverImagePublicId();

        album.setTitle(req.title());
        album.setDescription(req.description());
        album.setCoverImageUrl(req.coverImageUrl());
        album.setCoverImagePublicId(req.coverImagePublicId());
        album.setEventDate(req.eventDate());
        album.setLocation(req.location());
        CommunityAlbum saved = albumRepository.save(album);
        CommunityAlbumDto dto = CommunityAlbumDto.from(saved);
        auditLogService.log(currentUser(), "ALBUM_UPDATED", "CommunityAlbum", saved.getId(),
                "Updated album \"" + saved.getTitle() + "\"");

        if (oldCoverPublicId != null && !oldCoverPublicId.equals(req.coverImagePublicId())) {
            destroyOnCloudinary(oldCoverPublicId);
        }
        return dto;
    }

    @Transactional
    public CommunityAlbumDto publish(UUID id) {
        CommunityAlbum album = findById(id);
        if (album.getStatus() == AlbumStatus.PUBLISHED) {
            return CommunityAlbumDto.from(album);
        }
        album.setStatus(AlbumStatus.PUBLISHED);
        if (album.getPublishedAt() == null) {
            album.setPublishedAt(LocalDateTime.now());
        }
        log.info("Album '{}' published", album.getTitle());
        CommunityAlbum saved = albumRepository.save(album);
        auditLogService.log(currentUser(), "ALBUM_PUBLISHED", "CommunityAlbum", saved.getId(),
                "Published album \"" + saved.getTitle() + "\"");
        return CommunityAlbumDto.from(saved);
    }

    @Transactional
    public CommunityAlbumDto unpublish(UUID id) {
        CommunityAlbum album = findById(id);
        album.setStatus(AlbumStatus.DRAFT);
        log.info("Album '{}' unpublished", album.getTitle());
        CommunityAlbum saved = albumRepository.save(album);
        auditLogService.log(currentUser(), "ALBUM_UNPUBLISHED", "CommunityAlbum", saved.getId(),
                "Unpublished album \"" + saved.getTitle() + "\"");
        return CommunityAlbumDto.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        CommunityAlbum album = findById(id);
        if (album.getStatus() == AlbumStatus.PUBLISHED) {
            throw new BadRequestException("Cannot delete a published album. Unpublish it first.");
        }
        String title = album.getTitle();
        if (album.getCoverImagePublicId() != null) {
            destroyOnCloudinary(album.getCoverImagePublicId());
        }
        album.getMedia().forEach(m -> destroyOnCloudinary(m.getPublicId()));
        albumRepository.delete(album);
        auditLogService.log(currentUser(), "ALBUM_DELETED", "CommunityAlbum", id,
                "Deleted album \"" + title + "\"");
        log.info("Album deleted: id={} title={}", id, title);
    }

    // ─────────────────────────────────────── Media management

    @Transactional
    public CommunityAlbumDto addMedia(UUID albumId, AddAlbumMediaRequest req) {
        CommunityAlbum album = findById(albumId);
        int nextOrder = req.sortOrder() != null
                ? req.sortOrder()
                : mediaRepository.findMaxSortOrderByAlbumId(albumId) + 1;

        AlbumMedia media = AlbumMedia.builder()
                .album(album)
                .publicId(req.publicId())
                .url(req.url())
                .format(req.format())
                .caption(req.caption())
                .sortOrder(nextOrder)
                .width(req.width())
                .height(req.height())
                .build();

        AlbumMedia savedMedia = mediaRepository.save(media);
        album.getMedia().add(savedMedia);
        auditLogService.log(currentUser(), "ALBUM_MEDIA_ADDED", "AlbumMedia", savedMedia.getId(),
                "Added media to album \"" + album.getTitle() + "\"");
        return CommunityAlbumDto.from(album);
    }

    @Transactional
    public void removeMedia(UUID albumId, UUID mediaId) {
        AlbumMedia media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media item not found: " + mediaId));
        if (!media.getAlbum().getId().equals(albumId)) {
            throw new BadRequestException("Media item does not belong to this album.");
        }
        String albumTitle = media.getAlbum().getTitle();
        mediaRepository.delete(media);
        destroyOnCloudinary(media.getPublicId());
        auditLogService.log(currentUser(), "ALBUM_MEDIA_REMOVED", "AlbumMedia", mediaId,
                "Removed media from album \"" + albumTitle + "\"");
        log.info("Album media removed: albumId={} mediaId={}", albumId, mediaId);
    }

    // ─────────────────────────────────────── Private

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }

    private void destroyOnCloudinary(String publicId) {
        if (devMode) {
            log.info("[Cloudinary DEV] Simulated delete: publicId={}", publicId);
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Cloudinary delete success: publicId={}", publicId);
        } catch (IOException e) {
            log.error("Cloudinary delete failed for publicId={}: {}", publicId, e.getMessage());
        }
    }

    private CommunityAlbum findById(UUID id) {
        return albumRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Album not found: " + id));
    }
}
