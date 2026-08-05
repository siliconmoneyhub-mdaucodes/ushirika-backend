package com.mdau.ushirika.module.constitution.repository;

import com.mdau.ushirika.module.constitution.entity.GoverningDocument;
import com.mdau.ushirika.module.constitution.enums.DocumentStatus;
import com.mdau.ushirika.module.constitution.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GoverningDocumentRepository extends JpaRepository<GoverningDocument, UUID> {

    /** Public listing — published documents ordered by sort_order then created_at. */
    List<GoverningDocument> findAllByStatusOrderBySortOrderAscCreatedAtDesc(DocumentStatus status);

    /** Admin listing — all documents ordered by sort_order then created_at. */
    List<GoverningDocument> findAllByOrderBySortOrderAscCreatedAtDesc();

    /** Onboarding gate — is there a published bylaws/constitution document to accept? */
    boolean existsByStatusAndDocumentTypeIn(DocumentStatus status, List<DocumentType> documentTypes);

    /** Startup seed guard — has any document of this type ever been created (any status)? */
    boolean existsByDocumentType(DocumentType documentType);

    List<GoverningDocument> findAllByDocumentType(DocumentType documentType);
}
