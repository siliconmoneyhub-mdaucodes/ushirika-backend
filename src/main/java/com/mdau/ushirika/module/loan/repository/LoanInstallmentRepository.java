package com.mdau.ushirika.module.loan.repository;

import com.mdau.ushirika.module.loan.entity.LoanApplication;
import com.mdau.ushirika.module.loan.entity.LoanInstallment;
import com.mdau.ushirika.module.loan.enums.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, UUID> {

    List<LoanInstallment> findByLoanOrderByInstallmentNumber(LoanApplication loan);

    long countByLoanAndStatusNotIn(LoanApplication loan, List<InstallmentStatus> excludedStatuses);

    @Query("SELECT COALESCE(SUM(i.amountPaid), 0) FROM LoanInstallment i WHERE i.loan = :loan")
    java.math.BigDecimal sumAmountPaidByLoan(@Param("loan") LoanApplication loan);

    /**
     * Loans with at least one unpaid installment past its due date. Not filtered on the OVERDUE
     * enum value -- nothing in this codebase ever transitions an installment to OVERDUE, so that
     * status is always empty; overdue must be derived from dueDate instead.
     */
    @Query("SELECT COUNT(DISTINCT i.loan) FROM LoanInstallment i " +
           "WHERE i.status IN ('PENDING','PARTIAL') AND i.dueDate < :today")
    long countDistinctLoansWithOverdueInstallment(@Param("today") java.time.LocalDate today);
}
