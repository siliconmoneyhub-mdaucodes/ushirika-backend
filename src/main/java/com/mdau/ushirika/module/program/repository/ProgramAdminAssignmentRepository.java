package com.mdau.ushirika.module.program.repository;

import com.mdau.ushirika.module.program.entity.ProgramAdminAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProgramAdminAssignmentRepository extends JpaRepository<ProgramAdminAssignment, UUID> {

    List<ProgramAdminAssignment> findAllByProgramId(UUID programId);

    List<ProgramAdminAssignment> findAllByUserId(UUID userId);

    boolean existsByProgramIdAndUserId(UUID programId, UUID userId);

    void deleteByProgramIdAndUserId(UUID programId, UUID userId);
}
