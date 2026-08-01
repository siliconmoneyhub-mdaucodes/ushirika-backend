package com.mdau.ushirika.module.program.repository;

import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.enums.ProgramStatus;
import com.mdau.ushirika.module.program.enums.ProgramType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramRepository extends JpaRepository<Program, UUID> {

    boolean existsBySlug(String slug);

    Optional<Program> findBySlug(String slug);

    List<Program> findAllByStatus(ProgramStatus status);

    List<Program> findAllByOrderByNameAsc();

    List<Program> findAllByType(ProgramType type);
}
