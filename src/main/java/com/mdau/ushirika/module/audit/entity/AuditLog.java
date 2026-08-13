package com.mdau.ushirika.module.audit.entity;

import com.mdau.ushirika.module.audit.enums.LedgerDirection;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_actor",      columnList = "actor_id"),
        @Index(name = "idx_audit_action",     columnList = "action"),
        @Index(name = "idx_audit_entity",     columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_name", nullable = false, length = 200)
    private String actorName;

    @Column(name = "actor_role", nullable = false, length = 50)
    private String actorRole;

    /** Snapshot of the actor's OfficialTitle at log time, if they held one. Null otherwise --
     * same "snapshot, don't join" rationale as actorName/actorRole, so this stays historically
     * accurate even if the user's title later changes or the account is deactivated. */
    @Column(name = "actor_title", length = 30)
    private String actorTitle;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Money-movement amount, null for non-financial audit rows (most of them). */
    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    /** IN (money received) or OUT (money disbursed), null for non-financial audit rows. */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 10)
    private LedgerDirection direction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
