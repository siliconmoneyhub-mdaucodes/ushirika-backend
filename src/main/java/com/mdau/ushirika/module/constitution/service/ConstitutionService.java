package com.mdau.ushirika.module.constitution.service;

import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.constitution.dto.GoverningDocumentDto;
import com.mdau.ushirika.module.constitution.dto.GoverningDocumentRequest;
import com.mdau.ushirika.module.constitution.entity.GoverningDocument;
import com.mdau.ushirika.module.constitution.enums.DocumentStatus;
import com.mdau.ushirika.module.constitution.repository.GoverningDocumentRepository;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConstitutionService {

    private final GoverningDocumentRepository repo;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public List<GoverningDocumentDto> listPublished() {
        return repo.findAllByStatusOrderBySortOrderAscCreatedAtDesc(DocumentStatus.PUBLISHED)
                   .stream().map(GoverningDocumentDto::from).toList();
    }

    public List<GoverningDocumentDto> listAll() {
        return repo.findAllByOrderBySortOrderAscCreatedAtDesc()
                   .stream().map(GoverningDocumentDto::from).toList();
    }

    @Transactional
    public GoverningDocumentDto create(GoverningDocumentRequest req) {
        GoverningDocument doc = GoverningDocument.builder()
                .title(req.title())
                .documentType(req.documentType())
                .description(req.description())
                .documentVersion(req.documentVersion())
                .fileUrl(req.fileUrl())
                .filePublicId(req.filePublicId())
                .contentText(req.contentText())
                .effectiveDate(req.effectiveDate())
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .build();
        return GoverningDocumentDto.from(repo.save(doc));
    }

    @Transactional
    public GoverningDocumentDto update(UUID id, GoverningDocumentRequest req) {
        GoverningDocument doc = findOrThrow(id);
        doc.setTitle(req.title());
        doc.setDocumentType(req.documentType());
        doc.setDescription(req.description());
        if (req.documentVersion() != null) doc.setDocumentVersion(req.documentVersion());
        if (req.fileUrl() != null) doc.setFileUrl(req.fileUrl());
        if (req.filePublicId() != null) doc.setFilePublicId(req.filePublicId());
        doc.setContentText(req.contentText());
        doc.setEffectiveDate(req.effectiveDate());
        if (req.sortOrder() != null) doc.setSortOrder(req.sortOrder());
        return GoverningDocumentDto.from(repo.save(doc));
    }

    @Transactional
    public GoverningDocumentDto publish(UUID id) {
        GoverningDocument doc = findOrThrow(id);
        doc.setStatus(DocumentStatus.PUBLISHED);
        if (doc.getPublishedAt() == null) doc.setPublishedAt(LocalDateTime.now());
        repo.save(doc);

        User admin = currentUser();
        auditLogService.log(admin, "DOCUMENT_PUBLISHED", "GoverningDocument", doc.getId(),
                "Published " + doc.getDocumentType() + " \"" + doc.getTitle() + "\" (v" + doc.getDocumentVersion()
                        + ") by " + admin.getFullName());

        return GoverningDocumentDto.from(doc);
    }

    @Transactional
    public GoverningDocumentDto unpublish(UUID id) {
        GoverningDocument doc = findOrThrow(id);
        doc.setStatus(DocumentStatus.DRAFT);
        repo.save(doc);

        User admin = currentUser();
        auditLogService.log(admin, "DOCUMENT_UNPUBLISHED", "GoverningDocument", doc.getId(),
                "Unpublished " + doc.getDocumentType() + " \"" + doc.getTitle() + "\" by " + admin.getFullName());

        return GoverningDocumentDto.from(doc);
    }

    @Transactional
    public void delete(UUID id) {
        GoverningDocument doc = findOrThrow(id);
        User admin = currentUser();
        auditLogService.log(admin, "DOCUMENT_DELETED", "GoverningDocument", doc.getId(),
                "Deleted " + doc.getDocumentType() + " \"" + doc.getTitle() + "\" by " + admin.getFullName());
        repo.delete(doc);
    }

    private GoverningDocument findOrThrow(UUID id) {
        return repo.findById(id)
                   .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
