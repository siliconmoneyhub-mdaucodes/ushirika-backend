package com.mdau.ushirika.module.attendance.repository;

import com.mdau.ushirika.module.attendance.entity.AttendanceExcuse;
import com.mdau.ushirika.module.attendance.entity.AttendanceRecord;
import com.mdau.ushirika.module.attendance.enums.ExcuseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttendanceExcuseRepository extends JpaRepository<AttendanceExcuse, UUID> {

    List<AttendanceExcuse> findByAttendanceRecord_User_IdOrderByCreatedAtDesc(UUID userId);

    boolean existsByAttendanceRecordAndStatus(AttendanceRecord attendanceRecord, ExcuseStatus status);

    Page<AttendanceExcuse> findByStatusOrderByCreatedAtDesc(ExcuseStatus status, Pageable pageable);

    Page<AttendanceExcuse> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
