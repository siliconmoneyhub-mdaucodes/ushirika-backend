package com.mdau.ushirika.module.benevolence.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.benevolence.dto.ClaimCategoryDto;
import com.mdau.ushirika.module.benevolence.dto.SaveClaimCategoryRequest;
import com.mdau.ushirika.module.benevolence.entity.BenevolenceClaimCategory;
import com.mdau.ushirika.module.benevolence.repository.BenevolenceClaimCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BenevolenceClaimCategoryService {

    private final BenevolenceClaimCategoryRepository repo;
    private final UserRepository userRepo;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ClaimCategoryDto> listAll() {
        return repo.findAllByOrderBySortOrderAscNameAsc()
                .stream().map(ClaimCategoryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimCategoryDto> listActive() {
        return repo.findAllByActiveTrueOrderBySortOrderAscNameAsc()
                .stream().map(ClaimCategoryDto::from).toList();
    }

    @Transactional
    public ClaimCategoryDto create(SaveClaimCategoryRequest req) {
        if (repo.existsByNameIgnoreCase(req.name())) {
            throw new ConflictException("A claim category named '" + req.name() + "' already exists.");
        }
        BenevolenceClaimCategory cat = BenevolenceClaimCategory.builder()
                .name(req.name().trim())
                .description(req.description())
                .eventDateLabel(req.eventDateLabel() != null ? req.eventDateLabel() : "Event Date")
                .eventPersonLabel(req.eventPersonLabel() != null ? req.eventPersonLabel() : "Person Name")
                .requiresDocuments(Boolean.TRUE.equals(req.requiresDocuments()))
                .active(req.active() == null || req.active())
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .build();
        repo.save(cat);
        User admin = currentUser();
        auditLogService.log(admin, "CLAIM_CATEGORY_CREATED", "BenevolenceClaimCategory", cat.getId(),
                "Claim category '" + cat.getName() + "' created by " + admin.getFullName());
        return ClaimCategoryDto.from(cat);
    }

    @Transactional
    public ClaimCategoryDto update(UUID id, SaveClaimCategoryRequest req) {
        BenevolenceClaimCategory cat = find(id);
        if (!cat.getName().equalsIgnoreCase(req.name())
                && repo.existsByNameIgnoreCase(req.name())) {
            throw new ConflictException("A claim category named '" + req.name() + "' already exists.");
        }
        cat.setName(req.name().trim());
        if (req.description()       != null) cat.setDescription(req.description());
        if (req.eventDateLabel()    != null) cat.setEventDateLabel(req.eventDateLabel());
        if (req.eventPersonLabel()  != null) cat.setEventPersonLabel(req.eventPersonLabel());
        if (req.requiresDocuments() != null) cat.setRequiresDocuments(req.requiresDocuments());
        if (req.active()            != null) cat.setActive(req.active());
        if (req.sortOrder()         != null) cat.setSortOrder(req.sortOrder());
        repo.save(cat);
        User admin = currentUser();
        auditLogService.log(admin, "CLAIM_CATEGORY_UPDATED", "BenevolenceClaimCategory", cat.getId(),
                "Claim category '" + cat.getName() + "' updated by " + admin.getFullName());
        return ClaimCategoryDto.from(cat);
    }

    @Transactional
    public ClaimCategoryDto toggleActive(UUID id) {
        BenevolenceClaimCategory cat = find(id);
        cat.setActive(!cat.isActive());
        repo.save(cat);
        User admin = currentUser();
        auditLogService.log(admin, cat.isActive() ? "CLAIM_CATEGORY_ACTIVATED" : "CLAIM_CATEGORY_DEACTIVATED",
                "BenevolenceClaimCategory", cat.getId(),
                "Claim category '" + cat.getName() + "' " + (cat.isActive() ? "activated" : "deactivated")
                        + " by " + admin.getFullName());
        return ClaimCategoryDto.from(cat);
    }

    @Transactional
    public void delete(UUID id) {
        BenevolenceClaimCategory cat = find(id);
        repo.delete(cat);
        User admin = currentUser();
        auditLogService.log(admin, "CLAIM_CATEGORY_DELETED", "BenevolenceClaimCategory", id,
                "Claim category '" + cat.getName() + "' deleted by " + admin.getFullName());
    }

    private BenevolenceClaimCategory find(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim category not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
