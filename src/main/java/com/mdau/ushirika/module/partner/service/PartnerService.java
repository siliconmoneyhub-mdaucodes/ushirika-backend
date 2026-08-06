package com.mdau.ushirika.module.partner.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.partner.dto.PartnerDto;
import com.mdau.ushirika.module.partner.dto.SavePartnerRequest;
import com.mdau.ushirika.module.partner.entity.Partner;
import com.mdau.ushirika.module.partner.repository.PartnerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PartnerService {

    private final PartnerRepository repo;
    private final Cloudinary cloudinary;
    private final boolean devMode;

    public PartnerService(
            PartnerRepository repo,
            @Value("${app.cloudinary.cloud-name:NOT_SET}") String cloudName,
            @Value("${app.cloudinary.api-key:NOT_SET}")    String apiKey,
            @Value("${app.cloudinary.api-secret:NOT_SET}") String apiSecret
    ) {
        this.repo = repo;
        this.devMode = "NOT_SET".equals(cloudName) || "NOT_SET".equals(apiKey);
        this.cloudinary = devMode
                ? new Cloudinary()
                : new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", cloudName,
                        "api_key",    apiKey,
                        "api_secret", apiSecret,
                        "secure",     true));
        if (devMode) log.warn("[Cloudinary DEV] Partner logo uploads will be simulated.");
    }

    // ── Public ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PartnerDto> listPublic() {
        return repo.findAllByActiveTrueOrderBySortOrderAscNameAsc()
                .stream().map(PartnerDto::from).toList();
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PartnerDto> listAll() {
        return repo.findAllByOrderBySortOrderAscNameAsc()
                .stream().map(PartnerDto::from).toList();
    }

    @Transactional
    public PartnerDto create(SavePartnerRequest req) {
        Partner partner = Partner.builder()
                .name(req.name().trim())
                .description(req.description())
                .websiteUrl(req.websiteUrl())
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .active(true)
                .build();
        repo.save(partner);
        return PartnerDto.from(partner);
    }

    @Transactional
    public PartnerDto update(UUID id, SavePartnerRequest req) {
        Partner partner = find(id);
        partner.setName(req.name().trim());
        if (req.description() != null) partner.setDescription(req.description());
        if (req.websiteUrl()   != null) partner.setWebsiteUrl(req.websiteUrl());
        if (req.sortOrder()    != null) partner.setSortOrder(req.sortOrder());
        repo.save(partner);
        return PartnerDto.from(partner);
    }

    @Transactional
    public PartnerDto uploadLogo(UUID id, MultipartFile file) {
        Partner partner = find(id);
        validateImage(file);

        if (partner.getCloudinaryPublicId() != null && !devMode) {
            try {
                cloudinary.uploader().destroy(partner.getCloudinaryPublicId(), ObjectUtils.emptyMap());
            } catch (IOException e) {
                log.warn("Could not delete old Cloudinary logo for partner {}: {}", id, e.getMessage());
            }
        }

        if (devMode) {
            String fakePublicId = "partners/dev_" + System.currentTimeMillis();
            partner.setCloudinaryPublicId(fakePublicId);
            partner.setLogoUrl("https://res.cloudinary.com/dev/image/upload/" + fakePublicId);
        } else {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = cloudinary.uploader().upload(
                        file.getBytes(),
                        ObjectUtils.asMap(
                                "folder",           "partners",
                                "resource_type",    "image",
                                "use_filename",     true,
                                "unique_filename",  true,
                                "transformation",   "c_fit,w_400,h_400,q_auto,f_auto"
                        )
                );
                partner.setCloudinaryPublicId(String.valueOf(result.get("public_id")));
                partner.setLogoUrl(String.valueOf(result.get("secure_url")));
            } catch (IOException e) {
                throw new BadRequestException("Failed to upload logo: " + e.getMessage());
            }
        }

        repo.save(partner);
        return PartnerDto.from(partner);
    }

    @Transactional
    public PartnerDto toggle(UUID id) {
        Partner partner = find(id);
        partner.setActive(!partner.isActive());
        repo.save(partner);
        return PartnerDto.from(partner);
    }

    @Transactional
    public void delete(UUID id) {
        Partner partner = find(id);
        if (partner.getCloudinaryPublicId() != null && !devMode) {
            try {
                cloudinary.uploader().destroy(partner.getCloudinaryPublicId(), ObjectUtils.emptyMap());
            } catch (IOException e) {
                log.warn("Could not delete Cloudinary logo for partner {}: {}", id, e.getMessage());
            }
        }
        repo.delete(partner);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private Partner find(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partner not found: " + id));
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Logo file is required.");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new BadRequestException("Only image files are accepted.");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BadRequestException("Logo must not exceed 5 MB.");
        }
    }
}
