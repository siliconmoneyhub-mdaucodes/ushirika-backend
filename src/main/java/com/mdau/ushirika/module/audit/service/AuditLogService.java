package com.mdau.ushirika.module.audit.service;

import com.mdau.ushirika.module.audit.entity.AuditLog;
import com.mdau.ushirika.module.audit.enums.LedgerDirection;
import com.mdau.ushirika.module.audit.repository.AuditLogRepository;
import com.mdau.ushirika.module.auth.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log a non-financial admin action asynchronously so it never blocks the main transaction.
     *
     * @param actor      the authenticated User performing the action
     * @param action     short constant like "REINSTATEMENT_APPROVED", "FINE_WAIVED"
     * @param entityType the domain type e.g. "ReinstatementRequest", "Fine", "MembershipDue"
     * @param entityId   the UUID of the affected record (may be null for bulk actions)
     * @param description human-readable sentence describing what happened
     */
    @Async
    public void log(User actor, String action, String entityType, UUID entityId, String description) {
        log(actor, action, entityType, entityId, description, null, null);
    }

    /**
     * Log a money-moving admin action -- same as {@link #log(User, String, String, UUID, String)}
     * but also records it as a ledger entry (amount + direction), so the Money In & Out view
     * (Phase 2/4 of the Finance Visibility plan) and per-program totals can be computed directly
     * from the audit trail instead of needing a separate ledger table.
     *
     * @param amount    the money amount moved (always positive; direction says which way)
     * @param direction IN (money received) or OUT (money disbursed)
     */
    @Async
    public void log(User actor, String action, String entityType, UUID entityId, String description,
                     BigDecimal amount, LedgerDirection direction) {
        try {
            AuditLog entry = AuditLog.builder()
                    .actorId(actor.getId())
                    .actorName(actor.getFullName())
                    .actorRole(actor.getRole().name())
                    .actorTitle(actor.getOfficialTitle() != null ? actor.getOfficialTitle().name() : null)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .amount(amount)
                    .direction(direction)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("AuditLogService: failed to persist audit entry [{}] for actor {}: {}",
                    action, actor.getId(), e.getMessage());
        }
    }

    /**
     * Log a failed attempt (e.g. login) against an identifier that doesn't match any real
     * account -- there's no User to attach it to, so actorId/actorRole stay null and
     * attemptedIdentifier carries the raw input the caller typed, for security visibility into
     * unknown-account probing without pretending there's a real actor behind it.
     */
    @Async
    public void logUnknownAttempt(String action, String attemptedIdentifier, String description) {
        try {
            AuditLog entry = AuditLog.builder()
                    .actorName(attemptedIdentifier)
                    .action(action)
                    .description(description)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("AuditLogService: failed to persist unknown-attempt audit entry [{}]: {}", action, e.getMessage());
        }
    }
}
