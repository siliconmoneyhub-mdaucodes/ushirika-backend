package com.mdau.ushirika.module.dues.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.dues.entity.MembershipDue;
import com.mdau.ushirika.module.dues.enums.DuesStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipDueRepository extends JpaRepository<MembershipDue, UUID> {

    Optional<MembershipDue> findByUserAndYear(User user, int year);

    List<MembershipDue> findByUserOrderByYearDesc(User user);

    Page<MembershipDue> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<MembershipDue> findAllByStatusOrderByCreatedAtDesc(DuesStatus status, Pageable pageable);

    Page<MembershipDue> findAllByYearOrderByCreatedAtDesc(int year, Pageable pageable);

    Page<MembershipDue> findAllByYearAndStatusOrderByCreatedAtDesc(int year, DuesStatus status, Pageable pageable);

    @Query("SELECT d FROM MembershipDue d WHERE d.status = :status AND d.dueDate < :today")
    List<MembershipDue> findOverdue(@Param("today") LocalDate today, @Param("status") DuesStatus status);

    List<MembershipDue> findByStatusAndDueDate(DuesStatus status, LocalDate dueDate);

    /** Calendar query: dues for a user with due date within [from, to]. */
    List<MembershipDue> findByUserAndDueDateBetween(User user, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM MembershipDue d WHERE d.year = :year")
    BigDecimal sumAmountByYear(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(d.paidAmount), 0) FROM MembershipDue d WHERE d.year = :year")
    BigDecimal sumPaidAmountByYear(@Param("year") int year);
}
