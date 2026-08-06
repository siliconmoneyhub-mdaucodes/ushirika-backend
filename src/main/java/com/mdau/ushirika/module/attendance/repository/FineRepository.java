package com.mdau.ushirika.module.attendance.repository;

import com.mdau.ushirika.module.attendance.entity.Fine;
import com.mdau.ushirika.module.attendance.enums.FineStatus;
import com.mdau.ushirika.module.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface FineRepository extends JpaRepository<Fine, UUID> {

    Page<Fine> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(FineStatus status);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fine f WHERE f.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") FineStatus status);

    Page<Fine> findByStatusOrderByCreatedAtDesc(FineStatus status, Pageable pageable);

    List<Fine> findByUserOrderByCreatedAtDesc(User user);

    List<Fine> findByUserAndStatusOrderByDueDateAsc(User user, FineStatus status);

    List<Fine> findByStatusAndDueDate(FineStatus status, java.time.LocalDate dueDate);

    /** Calendar query: pending fines for a user with due date within [from, to]. */
    List<Fine> findByUserAndStatusAndDueDateBetween(User user, FineStatus status, java.time.LocalDate from, java.time.LocalDate to);
}
