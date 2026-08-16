package com.mdau.ushirika.module.election.repository;

import com.mdau.ushirika.module.election.entity.ElectionCandidacy;
import com.mdau.ushirika.module.election.enums.CandidacyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ElectionCandidacyRepository extends JpaRepository<ElectionCandidacy, UUID> {

    List<ElectionCandidacy> findAllByElectionIdOrderBySeatTitleAscMemberNameAsc(UUID electionId);

    List<ElectionCandidacy> findAllBySeatIdAndStatusOrderByMemberNameAsc(UUID seatId, CandidacyStatus status);

    List<ElectionCandidacy> findAllBySeatIdOrderByMemberNameAsc(UUID seatId);

    Optional<ElectionCandidacy> findByElectionIdAndSeatIdAndCandidateId(UUID electionId, UUID seatId, UUID candidateId);

    List<ElectionCandidacy> findAllByCandidateIdOrderByCreatedAtDesc(UUID candidateId);

    boolean existsByElectionIdAndSeatIdAndCandidateId(UUID electionId, UUID seatId, UUID candidateId);

    List<ElectionCandidacy> findAllByStatusOrderByCreatedAtAsc(CandidacyStatus status);
}
