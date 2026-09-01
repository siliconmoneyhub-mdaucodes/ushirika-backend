package com.mdau.ushirika.module.leadership.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.leadership.dto.LeadershipOfficialDto;
import com.mdau.ushirika.module.leadership.dto.PublicLeadershipDto;
import com.mdau.ushirika.module.leadership.dto.SaveOfficialRequest;
import com.mdau.ushirika.module.leadership.entity.LeadershipOfficial;
import com.mdau.ushirika.module.leadership.enums.LeadershipTeam;
import com.mdau.ushirika.module.leadership.repository.LeadershipOfficialRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class LeadershipService {

    private final LeadershipOfficialRepository repo;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final Cloudinary cloudinary;
    private final boolean devMode;

    public LeadershipService(
            LeadershipOfficialRepository repo,
            UserRepository userRepository,
            AuditLogService auditLogService,
            @Value("${app.cloudinary.cloud-name:NOT_SET}") String cloudName,
            @Value("${app.cloudinary.api-key:NOT_SET}")    String apiKey,
            @Value("${app.cloudinary.api-secret:NOT_SET}") String apiSecret
    ) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.devMode = "NOT_SET".equals(cloudName) || "NOT_SET".equals(apiKey);
        this.cloudinary = devMode
                ? new Cloudinary()
                : new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", cloudName,
                        "api_key",    apiKey,
                        "api_secret", apiSecret,
                        "secure",     true));
        if (devMode) log.warn("[Cloudinary DEV] Leadership image uploads will be simulated.");
    }

    // ── Public ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicLeadershipDto getPublic() {
        List<LeadershipOfficialDto> executive  = byTeam(LeadershipTeam.EXECUTIVE);
        List<LeadershipOfficialDto> hospitality = byTeam(LeadershipTeam.HOSPITALITY);
        List<LeadershipOfficialDto> compliance  = byTeam(LeadershipTeam.COMPLIANCE);
        return new PublicLeadershipDto(executive, hospitality, compliance);
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LeadershipOfficialDto> listAll() {
        return repo.findAllByOrderByTeamAscSortOrderAscNameAsc()
                .stream().map(LeadershipOfficialDto::from).toList();
    }

    @Transactional
    public LeadershipOfficialDto create(SaveOfficialRequest req) {
        LeadershipOfficial official = LeadershipOfficial.builder()
                .name(req.name().trim())
                .role(req.role().trim())
                .team(req.team())
                .bio(req.bio())
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .active(true)
                .build();
        repo.save(official);
        auditLogService.log(currentUser(), "LEADERSHIP_CREATED", "LeadershipOfficial", official.getId(),
                "Created leadership entry for \"" + official.getName() + "\"");
        return LeadershipOfficialDto.from(official);
    }

    @Transactional
    public LeadershipOfficialDto update(UUID id, SaveOfficialRequest req) {
        LeadershipOfficial official = find(id);
        official.setName(req.name().trim());
        official.setRole(req.role().trim());
        official.setTeam(req.team());
        if (req.bio()       != null) official.setBio(req.bio());
        if (req.sortOrder() != null) official.setSortOrder(req.sortOrder());
        repo.save(official);
        auditLogService.log(currentUser(), "LEADERSHIP_UPDATED", "LeadershipOfficial", official.getId(),
                "Updated leadership entry for \"" + official.getName() + "\"");
        return LeadershipOfficialDto.from(official);
    }

    @Transactional
    public LeadershipOfficialDto uploadImage(UUID id, MultipartFile file) {
        LeadershipOfficial official = find(id);
        validateImage(file);

        // Delete old image from Cloudinary if present
        if (official.getCloudinaryPublicId() != null && !devMode) {
            try {
                cloudinary.uploader().destroy(official.getCloudinaryPublicId(), ObjectUtils.emptyMap());
            } catch (IOException e) {
                log.warn("Could not delete old Cloudinary image for official {}: {}", id, e.getMessage());
            }
        }

        if (devMode) {
            String fakePublicId = "leadership/dev_" + System.currentTimeMillis();
            official.setCloudinaryPublicId(fakePublicId);
            official.setImageUrl("https://res.cloudinary.com/dev/image/upload/" + fakePublicId);
        } else {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = cloudinary.uploader().upload(
                        file.getBytes(),
                        ObjectUtils.asMap(
                                "folder",           "leadership",
                                "resource_type",    "image",
                                "use_filename",     true,
                                "unique_filename",  true,
                                "transformation",   "c_fill,g_face,w_600,h_600,q_auto,f_auto"
                        )
                );
                official.setCloudinaryPublicId(String.valueOf(result.get("public_id")));
                official.setImageUrl(String.valueOf(result.get("secure_url")));
            } catch (IOException e) {
                throw new BadRequestException("Failed to upload image: " + e.getMessage());
            }
        }

        repo.save(official);
        auditLogService.log(currentUser(), "LEADERSHIP_IMAGE_UPDATED", "LeadershipOfficial", official.getId(),
                "Updated image for leadership entry \"" + official.getName() + "\"");
        return LeadershipOfficialDto.from(official);
    }

    @Transactional
    public LeadershipOfficialDto toggle(UUID id) {
        LeadershipOfficial official = find(id);
        official.setActive(!official.isActive());
        repo.save(official);
        auditLogService.log(currentUser(), official.isActive() ? "LEADERSHIP_ACTIVATED" : "LEADERSHIP_DEACTIVATED",
                "LeadershipOfficial", official.getId(),
                (official.isActive() ? "Activated " : "Deactivated ") + "leadership entry \"" + official.getName() + "\"");
        return LeadershipOfficialDto.from(official);
    }

    @Transactional
    public void delete(UUID id) {
        LeadershipOfficial official = find(id);
        if (official.getCloudinaryPublicId() != null && !devMode) {
            try {
                cloudinary.uploader().destroy(official.getCloudinaryPublicId(), ObjectUtils.emptyMap());
            } catch (IOException e) {
                log.warn("Could not delete Cloudinary image for official {}: {}", id, e.getMessage());
            }
        }
        auditLogService.log(currentUser(), "LEADERSHIP_DELETED", "LeadershipOfficial", official.getId(),
                "Deleted leadership entry \"" + official.getName() + "\"");
        repo.delete(official);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private List<LeadershipOfficialDto> byTeam(LeadershipTeam team) {
        return repo.findAllByTeamAndActiveTrueOrderBySortOrderAscNameAsc(team)
                .stream().map(LeadershipOfficialDto::from).toList();
    }

    private LeadershipOfficial find(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Official not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Image file is required.");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new BadRequestException("Only image files are accepted.");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BadRequestException("Image must not exceed 5 MB.");
        }
    }
}
