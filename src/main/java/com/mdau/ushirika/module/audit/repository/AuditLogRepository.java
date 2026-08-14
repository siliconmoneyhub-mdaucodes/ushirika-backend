package com.mdau.ushirika.module.audit.repository;

import com.mdau.ushirika.module.audit.entity.AuditLog;
import com.mdau.ushirika.module.audit.enums.LedgerDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actorId IS NULL OR a.actorId = :actorId)
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findWithFilters(
            @Param("actorId")    UUID   actorId,
            @Param("action")     String action,
            @Param("entityType") String entityType,
            Pageable pageable
    );

    /** Money In & Out view -- only rows a ledger backfill (Finance Visibility plan, Phase 2)
     * actually populated with amount/direction; every other audit row (the vast majority) is
     * non-financial and stays out of this view entirely.
     * <p>from/to are REQUIRED (never null) -- a "(:from IS NULL OR a.createdAt >= :from)" guard
     * here reproducibly threw "could not determine data type of parameter $1" against Postgres,
     * because Hibernate binds the same named parameter as two separate JDBC positions and the
     * IS-NULL-only position at PREPARE time has no typed column to infer from. Callers resolve
     * "no filter" to a wide sentinel range instead of passing null. */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.direction IS NOT NULL
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:direction IS NULL OR a.direction = :direction)
              AND a.createdAt >= :from
              AND a.createdAt <= :to
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findLedgerEntries(
            @Param("entityType") String entityType,
            @Param("direction")  LedgerDirection direction,
            @Param("from")       LocalDateTime from,
            @Param("to")         LocalDateTime to,
            Pageable pageable
    );

    /** Every distinct entityType that has ever appeared on a ledger row -- backs the program
     * filter dropdown on the Money In & Out page without hardcoding the list client-side. */
    @Query("SELECT DISTINCT a.entityType FROM AuditLog a WHERE a.direction IS NOT NULL ORDER BY a.entityType")
    List<String> findDistinctLedgerEntityTypes();

    /** Per-program totals for a date range -- feeds Phase 4's per-program + org-wide balance
     * totals directly from the ledger, grouped by entityType and direction.
     * <p>from/to are REQUIRED (never null) -- see findLedgerEntries' Javadoc for why; same fix. */
    @Query("""
            SELECT a.entityType, a.direction, COALESCE(SUM(a.amount), 0)
            FROM AuditLog a
            WHERE a.direction IS NOT NULL
              AND a.createdAt >= :from
              AND a.createdAt <= :to
            GROUP BY a.entityType, a.direction
            """)
    List<Object[]> sumLedgerByEntityTypeAndDirection(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to
    );
}
