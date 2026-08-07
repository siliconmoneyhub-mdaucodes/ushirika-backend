package com.mdau.ushirika.module.loan.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.loan.entity.LoanApplication;
import com.mdau.ushirika.module.loan.enums.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    List<LoanApplication> findByUserOrderByCreatedAtDesc(User user);

    Page<LoanApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<LoanApplication> findAllByStatusOrderByCreatedAtDesc(LoanStatus status, Pageable pageable);

    Optional<LoanApplication> findByReferenceNumber(String referenceNumber);

    boolean existsByUserAndStatusIn(User user, List<LoanStatus> statuses);

    long countByStatus(LoanStatus status);

    long countByStatusIn(List<LoanStatus> statuses);

    @Query("SELECT COALESCE(SUM(l.totalRepayable - l.totalPaid), 0) FROM LoanApplication l WHERE l.status IN ('DISBURSED','REPAYING')")
    BigDecimal sumOutstandingPrincipal();
}
