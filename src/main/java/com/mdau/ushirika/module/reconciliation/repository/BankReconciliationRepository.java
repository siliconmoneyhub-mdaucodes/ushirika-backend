package com.mdau.ushirika.module.reconciliation.repository;

import com.mdau.ushirika.module.reconciliation.entity.BankReconciliation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankReconciliationRepository extends JpaRepository<BankReconciliation, UUID> {

    /** History listing: :scope omitted (null) returns every row regardless of scope; the literal
     *  sentinel "ORG_WIDE" returns only org-wide (scope IS NULL) rows; anything else filters to
     *  that exact program scope. Kept distinct from findLatestByScope below, which has no "all"
     *  concept -- the summary view always asks for one specific scope, org-wide or a program. */
    @Query("""
            SELECT r FROM BankReconciliation r
            WHERE :scope IS NULL
               OR (:scope = 'ORG_WIDE' AND r.scope IS NULL)
               OR r.scope = :scope
            ORDER BY r.recordedAt DESC
            """)
    Page<BankReconciliation> findByScope(@Param("scope") String scope, Pageable pageable);

    /** Most recent check for a given scope (null = org-wide) -- backs the summary view's
     *  "last recorded" column per program without loading full history. */
    @Query("""
            SELECT r FROM BankReconciliation r
            WHERE (:scope IS NULL AND r.scope IS NULL) OR r.scope = :scope
            ORDER BY r.recordedAt DESC
            """)
    List<BankReconciliation> findLatestByScope(@Param("scope") String scope, Pageable pageable);

    default Optional<BankReconciliation> findMostRecent(String scope) {
        List<BankReconciliation> rows = findLatestByScope(scope, Pageable.ofSize(1));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
