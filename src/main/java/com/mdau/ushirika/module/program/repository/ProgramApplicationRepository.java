package com.mdau.ushirika.module.program.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.entity.ProgramApplication;
import com.mdau.ushirika.module.program.enums.ProgramApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramApplicationRepository extends JpaRepository<ProgramApplication, UUID> {

    List<ProgramApplication> findAllByApplicant(User applicant);

    List<ProgramApplication> findAllByApplicantId(UUID applicantId);

    boolean existsByProgramAndApplicant(Program program, User applicant);

    List<ProgramApplication> findAllByProgramIdAndStatusIn(UUID programId, List<ProgramApplicationStatus> statuses);

    Optional<ProgramApplication> findByProgramIdAndApplicantId(UUID programId, UUID applicantId);
}
