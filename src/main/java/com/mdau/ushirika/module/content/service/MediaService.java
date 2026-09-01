package com.mdau.ushirika.module.content.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.content.dto.MediaAssetDto;
import com.mdau.ushirika.module.content.entity.MediaAsset;
import com.mdau.ushirika.module.content.repository.MediaAssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class MediaService {

    private final MediaAssetRepository mediaAssetRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final Cloudinary cloudinary;
    private final boolean devMode;

    public MediaService(
            MediaAssetRepository mediaAssetRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            @Value("${app.cloudinary.cloud-name:NOT_SET}") String cloudName,
            @Value("${app.cloudinary.api-key:NOT_SET}")    String apiKey,
            @Value("${app.cloudinary.api-secret:NOT_SET}") String apiSecret
    ) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.devMode = "NOT_SET".equals(cloudName) || "NOT_SET".equals(apiKey);

        if (devMode) {
            this.cloudinary = new Cloudinary();
            log.warn("[Cloudinary DEV] No Cloudinary credentials configured — uploads will be simulated");
        } else {
            this.cloudinary = new Cloudinary(com.cloudinary.utils.ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key",    apiKey,
                    "api_secret", apiSecret,
                    "secure",     true
            ));
        }
    }

    @Transactional
    public MediaAssetDto upload(MultipartFile file, String folder) {
        validateFile(file);

        if (devMode) {
            String fakePublicId = folder + "/dev_" + System.currentTimeMillis();
            String fakeUrl = "https://res.cloudinary.com/dev/image/upload/" + fakePublicId;
            MediaAsset asset = saveAsset(fakePublicId, fakeUrl, folder, file.getOriginalFilename(),
                    "jpg", file.getSize(), null, null);
            log.info("[Cloudinary DEV] Simulated upload: publicId={}", fakePublicId);
            auditLogService.log(asset.getUploadedBy(), "MEDIA_UPLOADED", "MediaAsset", asset.getId(),
                    "Uploaded media \"" + asset.getOriginalFilename() + "\" to folder \"" + folder + "\"");
            return MediaAssetDto.from(asset);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "auto",
                            "use_filename", true,
                            "unique_filename", true
                    )
            );

            String publicId = String.valueOf(result.get("public_id"));
            String url      = String.valueOf(result.get("secure_url"));
            String format   = String.valueOf(result.get("format"));
            Long bytes      = result.get("bytes") instanceof Number n ? n.longValue() : null;
            Integer width   = result.get("width")  instanceof Number n ? n.intValue()  : null;
            Integer height  = result.get("height") instanceof Number n ? n.intValue()  : null;

            MediaAsset asset = saveAsset(publicId, url, folder,
                    file.getOriginalFilename(), format, bytes, width, height);

            log.info("Cloudinary upload success: publicId={} url={}", publicId, url);
            auditLogService.log(asset.getUploadedBy(), "MEDIA_UPLOADED", "MediaAsset", asset.getId(),
                    "Uploaded media \"" + asset.getOriginalFilename() + "\" to folder \"" + folder + "\"");
            return MediaAssetDto.from(asset);

        } catch (IOException e) {
            throw new BadRequestException("Failed to upload file to Cloudinary: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(String publicId) {
        MediaAsset asset = mediaAssetRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Media asset not found with publicId: " + publicId));
        UUID assetId = asset.getId();
        String filename = asset.getOriginalFilename();

        if (!devMode) {
            try {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                log.info("Cloudinary delete success: publicId={}", publicId);
            } catch (IOException e) {
                // Log but still remove from our DB — Cloudinary state may be stale
                log.error("Cloudinary delete failed for publicId={}: {}", publicId, e.getMessage());
            }
        } else {
            log.info("[Cloudinary DEV] Simulated delete: publicId={}", publicId);
        }

        mediaAssetRepository.delete(asset);
        auditLogService.log(currentUser(), "MEDIA_DELETED", "MediaAsset", assetId,
                "Deleted media \"" + filename + "\" (publicId=" + publicId + ")");
    }

    @Transactional(readOnly = true)
    public PagedResponse<MediaAssetDto> listAssets(String folder, Pageable pageable) {
        var page = folder != null && !folder.isBlank()
                ? mediaAssetRepository.findAllByFolderOrderByCreatedAtDesc(folder, pageable)
                : mediaAssetRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PagedResponse.of(page.map(MediaAssetDto::from));
    }

    // ─── Private

    private MediaAsset saveAsset(String publicId, String url, String folder,
                                 String originalFilename, String format,
                                 Long sizeBytes, Integer width, Integer height) {
        User uploader = currentUser();
        MediaAsset asset = MediaAsset.builder()
                .publicId(publicId)
                .url(url)
                .folder(folder)
                .originalFilename(originalFilename)
                .format(format)
                .sizeBytes(sizeBytes)
                .width(width)
                .height(height)
                .uploadedBy(uploader)
                .build();
        return mediaAssetRepository.save(asset);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required and must not be empty.");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
            throw new BadRequestException("Only image files and PDFs are accepted.");
        }
        // 10 MB cap
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BadRequestException("File must not exceed 10 MB.");
        }
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
