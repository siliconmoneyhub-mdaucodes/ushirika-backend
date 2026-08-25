package com.mdau.ushirika.module.election.service;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ForbiddenException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.module.audit.service.AuditLogService;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.election.dto.*;
import com.mdau.ushirika.module.election.entity.*;
import com.mdau.ushirika.module.election.enums.CandidacyStatus;
import com.mdau.ushirika.module.election.enums.ElectionStatus;
import com.mdau.ushirika.module.election.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElectionService {

    private final ElectionRepository            electionRepo;
    private final ElectionSeatRepository        seatRepo;
    private final ElectionCandidacyRepository   candidacyRepo;
    private final ElectionVoteReceiptRepository  receiptRepo;
    private final ElectionVoteTallyRepository   tallyRepo;
    private final ElectionResultRepository      resultRepo;
    private final AuditLogService               auditLogService;

    private static final Set<ElectionStatus> LIVE_TALLY_STATUSES = Set.of(
            ElectionStatus.VOTING_OPEN, ElectionStatus.VOTING_CLOSED,
            ElectionStatus.RESULTS_PUBLISHED, ElectionStatus.COMPLETED);

    // ── Public ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<ElectionDto> getActivePublic() {
        return electionRepo.findFirstByStatusOrderByYearDesc(ElectionStatus.NOMINATIONS_OPEN)
                .or(() -> electionRepo.findFirstByStatusOrderByYearDesc(ElectionStatus.NOMINATIONS_CLOSED))
                .or(() -> electionRepo.findFirstByStatusOrderByYearDesc(ElectionStatus.VOTING_OPEN))
                .or(() -> electionRepo.findFirstByStatusOrderByYearDesc(ElectionStatus.VOTING_CLOSED))
                .or(() -> electionRepo.findFirstByStatusOrderByYearDesc(ElectionStatus.RESULTS_PUBLISHED))
                .map(e -> buildDto(e, false));
    }

    @Transactional(readOnly = true)
    public List<ElectionSummaryDto> listPublishedResults() {
        return electionRepo.findAllByOrderByYearDescCreatedAtDesc().stream()
                .filter(e -> e.getStatus() == ElectionStatus.RESULTS_PUBLISHED
                          || e.getStatus() == ElectionStatus.COMPLETED)
                .map(ElectionSummaryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ElectionDto getPublicResults(UUID electionId) {
        Election e = findElection(electionId);
        if (e.getStatus() != ElectionStatus.RESULTS_PUBLISHED
                && e.getStatus() != ElectionStatus.COMPLETED) {
            throw new ForbiddenException("Results have not been published yet.");
        }
        return buildDto(e, true);
    }

    // ── Portal (member actions) ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CandidacyDto> getMyCandidacies(UUID userId) {
        return candidacyRepo.findAllByCandidateIdOrderByCreatedAtDesc(userId)
                .stream().map(CandidacyDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> getMyVotedSeats(UUID electionId, UUID userId) {
        return receiptRepo.findVotedSeatIdsByElectionAndVoter(electionId, userId);
    }

    @Transactional
    public CandidacyDto declareCandidacy(UUID electionId, UUID userId, DeclareCandidacyRequest req) {
        Election election = findElection(electionId);
        if (election.getStatus() != ElectionStatus.NOMINATIONS_OPEN) {
            throw new BadRequestException("Nominations are not currently open.");
        }
        ElectionSeat seat = findSeat(req.seatId());
        if (!seat.getElection().getId().equals(electionId)) {
            throw new BadRequestException("Seat does not belong to this election.");
        }
        if (candidacyRepo.existsByElectionIdAndSeatIdAndCandidateId(electionId, req.seatId(), userId)) {
            throw new ConflictException("You have already declared candidacy for this seat.");
        }
        if (seat.isExecutiveTier()) {
            if (req.statement() == null || req.statement().isBlank()) {
                throw new BadRequestException("A manifesto statement is required to run for an executive committee seat.");
            }
            if (req.photoUrl() == null || req.photoUrl().isBlank()) {
                throw new BadRequestException("A profile photo is required to run for an executive committee seat.");
            }
            if (req.videoUrl() == null || req.videoUrl().isBlank()) {
                throw new BadRequestException("A manifesto video is required to run for an executive committee seat.");
            }
        }
        User candidate = getCurrentUser();
        ElectionCandidacy candidacy = ElectionCandidacy.builder()
                .election(election)
                .seat(seat)
                .candidate(candidate)
                .memberName(candidate.getFullName())
                .memberId(null)
                .statement(req.statement())
                .photoUrl(req.photoUrl())
                .videoUrl(req.videoUrl())
                .status(CandidacyStatus.PENDING)
                .build();
        candidacyRepo.save(candidacy);
        log.info("Candidacy declared: {} for seat '{}' in election '{}'",
                candidate.getEmail(), seat.getTitle(), election.getTitle());
        auditLogService.log(candidate, "CANDIDACY_DECLARED", "ElectionCandidacy", candidacy.getId(),
                candidate.getFullName() + " declared candidacy for seat '" + seat.getTitle()
                        + "' in election '" + election.getTitle() + "'");
        return CandidacyDto.from(candidacy);
    }

    @Transactional
    public CandidacyDto withdrawCandidacy(UUID candidacyId, UUID userId) {
        ElectionCandidacy candidacy = findCandidacy(candidacyId);
        if (!candidacy.getCandidate().getId().equals(userId)) {
            throw new ForbiddenException("You can only withdraw your own candidacy.");
        }
        if (candidacy.getElection().getStatus() != ElectionStatus.NOMINATIONS_OPEN) {
            throw new BadRequestException("Candidacy withdrawal is only allowed during the nominations phase.");
        }
        if (candidacy.getStatus() == CandidacyStatus.WITHDRAWN) {
            throw new BadRequestException("Candidacy is already withdrawn.");
        }
        candidacy.setStatus(CandidacyStatus.WITHDRAWN);
        candidacy.setWithdrawnAt(LocalDateTime.now());
        candidacyRepo.save(candidacy);
        auditLogService.log(candidacy.getCandidate(), "CANDIDACY_WITHDRAWN", "ElectionCandidacy", candidacy.getId(),
                candidacy.getCandidate().getFullName() + " withdrew candidacy for seat '"
                        + candidacy.getSeat().getTitle() + "'");
        return CandidacyDto.from(candidacy);
    }

    @Transactional
    public String castVote(UUID electionId, UUID userId, CastVoteRequest req) {
        Election election = findElection(electionId);
        if (election.getStatus() != ElectionStatus.VOTING_OPEN) {
            throw new BadRequestException("Voting is not currently open.");
        }
        ElectionSeat seat = findSeat(req.seatId());
        if (!seat.getElection().getId().equals(electionId)) {
            throw new BadRequestException("Seat does not belong to this election.");
        }
        if (receiptRepo.existsByElectionIdAndSeatIdAndVoterId(electionId, req.seatId(), userId)) {
            throw new ConflictException("You have already voted for this seat.");
        }
        ElectionCandidacy candidacy = findCandidacy(req.candidacyId());
        if (!candidacy.getSeat().getId().equals(req.seatId())) {
            throw new BadRequestException("Candidate is not running for this seat.");
        }
        if (candidacy.getStatus() != CandidacyStatus.APPROVED) {
            throw new BadRequestException("This candidate is not approved.");
        }

        User voter = getCurrentUser();

        // Secret ballot: receipt (who voted for seat) and tally (who got votes) are separate
        ElectionVoteReceipt receipt = ElectionVoteReceipt.builder()
                .election(election)
                .seat(seat)
                .voter(voter)
                .build();
        receiptRepo.save(receipt);

        // Atomic increment — no OL contention under concurrent votes
        tallyRepo.incrementVoteCount(candidacy.getId());

        log.info("Vote cast: election={} seat='{}' voter={} (candidacy not logged for ballot secrecy)",
                electionId, seat.getTitle(), userId);
        // Ballot secrecy: audit the fact that a vote was cast, never who it was cast for —
        // entityId points at the seat, not the candidacy, so no voter->candidate link is ever
        // reconstructable from the audit trail.
        auditLogService.log(voter, "VOTE_CAST", "ElectionSeat", seat.getId(),
                voter.getFullName() + " cast a vote for seat '" + seat.getTitle()
                        + "' in election '" + election.getTitle() + "'");
        return "Vote recorded. Thank you for participating.";
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ElectionSummaryDto> listAll() {
        return electionRepo.findAllByOrderByYearDescCreatedAtDesc()
                .stream().map(ElectionSummaryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ElectionDto getAdmin(UUID electionId) {
        return buildDto(findElection(electionId), true);
    }

    @Transactional
    public ElectionDto create(CreateElectionRequest req) {
        // Only one active election allowed at a time
        if (electionRepo.existsByStatusIn(List.of(
                ElectionStatus.NOMINATIONS_OPEN, ElectionStatus.NOMINATIONS_CLOSED,
                ElectionStatus.VOTING_OPEN, ElectionStatus.VOTING_CLOSED))) {
            throw new ConflictException("An election is already in progress. Complete or cancel it first.");
        }
        Election election = Election.builder()
                .title(req.title())
                .year(req.year())
                .nominationsStart(req.nominationsStart())
                .nominationsEnd(req.nominationsEnd())
                .votingStart(req.votingStart())
                .votingEnd(req.votingEnd())
                .notes(req.notes())
                .status(ElectionStatus.DRAFT)
                .build();
        electionRepo.save(election);

        if (req.seats() != null) {
            for (CreateElectionRequest.SeatRequest sr : req.seats()) {
                ElectionSeat seat = ElectionSeat.builder()
                        .election(election)
                        .title(sr.title())
                        .description(sr.description())
                        .maxWinners(sr.maxWinners() > 0 ? sr.maxWinners() : 1)
                        .sortOrder(sr.sortOrder())
                        .executiveTier(sr.executiveTier())
                        .build();
                seatRepo.save(seat);
                election.getSeats().add(seat);

                // Create tally placeholder rows created on candidacy approval
            }
        }
        log.info("Election created: '{}' ({})", election.getTitle(), election.getYear());
        User admin = getCurrentUser();
        auditLogService.log(admin, "ELECTION_CREATED", "Election", election.getId(),
                "Election '" + election.getTitle() + "' (" + election.getYear() + ") created by "
                        + admin.getFullName());
        return buildDto(election, true);
    }

    @Transactional
    public ElectionDto update(UUID electionId, UpdateElectionRequest req) {
        Election election = findElection(electionId);
        if (election.getStatus() != ElectionStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT elections can be updated via this endpoint.");
        }
        if (req.title()             != null) election.setTitle(req.title());
        if (req.nominationsStart()  != null) election.setNominationsStart(req.nominationsStart());
        if (req.nominationsEnd()    != null) election.setNominationsEnd(req.nominationsEnd());
        if (req.votingStart()       != null) election.setVotingStart(req.votingStart());
        if (req.votingEnd()         != null) election.setVotingEnd(req.votingEnd());
        if (req.notes()             != null) election.setNotes(req.notes());
        electionRepo.save(election);
        User admin = getCurrentUser();
        auditLogService.log(admin, "ELECTION_UPDATED", "Election", election.getId(),
                "Election '" + election.getTitle() + "' updated by " + admin.getFullName());
        return buildDto(election, true);
    }

    @Transactional
    public ElectionDto advanceStatus(UUID electionId) {
        Election election = findElection(electionId);
        ElectionStatus next = switch (election.getStatus()) {
            case DRAFT              -> ElectionStatus.NOMINATIONS_OPEN;
            case NOMINATIONS_OPEN   -> ElectionStatus.NOMINATIONS_CLOSED;
            case NOMINATIONS_CLOSED -> ElectionStatus.VOTING_OPEN;
            case VOTING_OPEN        -> ElectionStatus.VOTING_CLOSED;
            case VOTING_CLOSED      -> throw new BadRequestException("Declare results first, then the election advances to RESULTS_PUBLISHED.");
            default -> throw new BadRequestException("Cannot advance from status: " + election.getStatus());
        };
        election.setStatus(next);
        electionRepo.save(election);
        log.info("Election '{}' advanced to {}", election.getTitle(), next);
        User admin = getCurrentUser();
        auditLogService.log(admin, "ELECTION_ADVANCED", "Election", election.getId(),
                "Election '" + election.getTitle() + "' advanced to " + next + " by " + admin.getFullName());
        return buildDto(election, true);
    }

    @Transactional
    public ElectionDto cancelElection(UUID electionId) {
        Election election = findElection(electionId);
        if (election.getStatus() == ElectionStatus.COMPLETED) {
            throw new BadRequestException("Completed elections cannot be cancelled.");
        }
        election.setStatus(ElectionStatus.CANCELLED);
        electionRepo.save(election);
        User admin = getCurrentUser();
        auditLogService.log(admin, "ELECTION_CANCELLED", "Election", election.getId(),
                "Election '" + election.getTitle() + "' cancelled by " + admin.getFullName());
        return buildDto(election, true);
    }

    @Transactional
    public CandidacyDto reviewCandidacy(UUID candidacyId, ReviewCandidacyRequest req) {
        ElectionCandidacy candidacy = findCandidacy(candidacyId);
        if (candidacy.getElection().getStatus() != ElectionStatus.NOMINATIONS_OPEN
                && candidacy.getElection().getStatus() != ElectionStatus.NOMINATIONS_CLOSED) {
            throw new BadRequestException("Candidacy review is only allowed during the nominations phase.");
        }
        if (candidacy.getStatus() != CandidacyStatus.PENDING) {
            throw new BadRequestException("Only PENDING candidacies can be reviewed.");
        }
        boolean approve = "APPROVE".equalsIgnoreCase(req.decision());
        candidacy.setStatus(approve ? CandidacyStatus.APPROVED : CandidacyStatus.REJECTED);
        candidacy.setRejectionReason(approve ? null : req.rejectionReason());
        candidacy.setReviewedBy(currentEmail());
        candidacy.setReviewedAt(LocalDateTime.now());
        candidacyRepo.save(candidacy);
        auditLogService.log(getCurrentUser(), "CANDIDACY_" + candidacy.getStatus().name(), "ElectionCandidacy",
                candidacy.getId(),
                "Candidacy of " + candidacy.getMemberName() + " for seat '" + candidacy.getSeat().getTitle()
                        + "' " + candidacy.getStatus().name().toLowerCase() + " by " + currentEmail());

        if (approve) {
            // Ensure a tally row exists for this candidacy
            tallyRepo.findByCandidacyId(candidacy.getId()).orElseGet(() -> {
                ElectionVoteTally tally = ElectionVoteTally.builder()
                        .candidacy(candidacy)
                        .voteCount(0L)
                        .build();
                return tallyRepo.save(tally);
            });
        }
        return CandidacyDto.from(candidacy);
    }

    /**
     * Compute and persist final results. Call after VOTING_CLOSED.
     * Auto-detects winners by vote count; tie-breaker entries override where needed.
     */
    @Transactional
    public ElectionDto declareResults(UUID electionId, DeclareResultsRequest req) {
        Election election = findElection(electionId);
        if (election.getStatus() != ElectionStatus.VOTING_CLOSED) {
            throw new BadRequestException("Results can only be declared after voting closes.");
        }

        Map<UUID, DeclareResultsRequest.TieBreakerEntry> tieMap = new HashMap<>();
        if (req != null && req.tieBreakers() != null) {
            req.tieBreakers().forEach(tb -> tieMap.put(tb.candidacyId(), tb));
        }

        // Wipe any previous draft results
        resultRepo.deleteAllByElectionId(electionId);

        List<ElectionSeat> seats = seatRepo.findAllByElectionIdOrderBySortOrderAscTitleAsc(electionId);
        for (ElectionSeat seat : seats) {
            List<ElectionVoteTally> tallies = tallyRepo.findAllByCandidacySeatIdOrderByVoteCountDesc(seat.getId());
            int rank = 1;
            for (ElectionVoteTally tally : tallies) {
                ElectionCandidacy candidacy = tally.getCandidacy();
                boolean autoWinner = rank <= seat.getMaxWinners();

                DeclareResultsRequest.TieBreakerEntry tb = tieMap.get(candidacy.getId());
                boolean winner  = tb != null ? tb.winner() : autoWinner;
                boolean tieBroken = tb != null;
                String notes = tb != null ? tb.notes() : null;

                ElectionResult result = ElectionResult.builder()
                        .election(election)
                        .seat(seat)
                        .candidacy(candidacy)
                        .memberName(candidacy.getMemberName())
                        .memberId(candidacy.getMemberId())
                        .seatTitle(seat.getTitle())
                        .voteCount(tally.getVoteCount())
                        .rank(rank)
                        .winner(winner)
                        .tieBroken(tieBroken)
                        .notes(notes)
                        .build();
                resultRepo.save(result);
                rank++;
            }
        }

        election.setStatus(ElectionStatus.RESULTS_PUBLISHED);
        election.setResultsDeclaredAt(LocalDateTime.now());
        electionRepo.save(election);
        log.info("Results declared for election '{}'", election.getTitle());
        User admin = getCurrentUser();
        auditLogService.log(admin, "ELECTION_RESULTS_DECLARED", "Election", election.getId(),
                "Results declared for election '" + election.getTitle() + "' by " + admin.getFullName());
        return buildDto(election, true);
    }

    @Transactional
    public ElectionDto completeElection(UUID electionId) {
        Election election = findElection(electionId);
        if (election.getStatus() != ElectionStatus.RESULTS_PUBLISHED) {
            throw new BadRequestException("Only RESULTS_PUBLISHED elections can be completed.");
        }
        election.setStatus(ElectionStatus.COMPLETED);
        election.setCompletedAt(LocalDateTime.now());
        electionRepo.save(election);
        User admin = getCurrentUser();
        auditLogService.log(admin, "ELECTION_COMPLETED", "Election", election.getId(),
                "Election '" + election.getTitle() + "' marked completed by " + admin.getFullName());
        return buildDto(election, true);
    }

    /** Admin view of all candidacies for an election with live tally counts. */
    @Transactional(readOnly = true)
    public List<CandidacyDto> listCandidacies(UUID electionId) {
        Map<UUID, Long> tallies = buildTallyMap(electionId);
        return candidacyRepo.findAllByElectionIdOrderBySeatTitleAscMemberNameAsc(electionId)
                .stream()
                .map(c -> CandidacyDto.from(c, tallies.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> getLiveTallies(UUID electionId) {
        Election election = findElection(electionId);
        if (!LIVE_TALLY_STATUSES.contains(election.getStatus())) {
            throw new ForbiddenException("Tallies are not available until voting opens.");
        }
        return buildTallyMap(electionId);
    }

    /**
     * Full election view (seats, candidates, running vote counts) for the member-facing
     * live-results feed. Individual ballots stay secret -- this only ever exposes aggregate
     * counts per candidacy, never who voted for whom.
     */
    @Transactional(readOnly = true)
    public ElectionDto getLiveResults(UUID electionId) {
        Election election = findElection(electionId);
        if (!LIVE_TALLY_STATUSES.contains(election.getStatus())) {
            throw new ForbiddenException("Live results are not available until voting opens.");
        }
        return buildDto(election, true);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ElectionDto buildDto(Election election, boolean includeTallies) {
        Map<UUID, Long> tallyMap = includeTallies ? buildTallyMap(election.getId()) : Map.of();
        List<ElectionSeat> seats = seatRepo.findAllByElectionIdOrderBySortOrderAscTitleAsc(election.getId());
        List<ElectionSeatDto> seatDtos = seats.stream()
                .map(seat -> {
                    List<CandidacyDto> candidacyDtos = candidacyRepo
                            .findAllBySeatIdOrderByMemberNameAsc(seat.getId())
                            .stream()
                            .map(c -> CandidacyDto.from(c, tallyMap.getOrDefault(c.getId(), 0L)))
                            .toList();
                    return ElectionSeatDto.from(seat, candidacyDtos);
                })
                .toList();
        List<ElectionResultDto> results = resultRepo
                .findAllByElectionIdOrderBySeatTitleAscRankAsc(election.getId())
                .stream().map(ElectionResultDto::from).toList();
        return ElectionDto.from(election, seatDtos, results);
    }

    private Map<UUID, Long> buildTallyMap(UUID electionId) {
        List<ElectionSeat> seats = seatRepo.findAllByElectionIdOrderBySortOrderAscTitleAsc(electionId);
        Map<UUID, Long> map = new HashMap<>();
        for (ElectionSeat seat : seats) {
            tallyRepo.findAllByCandidacySeatIdOrderByVoteCountDesc(seat.getId())
                    .forEach(t -> map.put(t.getCandidacy().getId(), t.getVoteCount()));
        }
        return map;
    }

    private Election findElection(UUID id) {
        return electionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found: " + id));
    }

    private ElectionSeat findSeat(UUID id) {
        return seatRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + id));
    }

    private ElectionCandidacy findCandidacy(UUID id) {
        return candidacyRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidacy not found: " + id));
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User u) return u;
        throw new ForbiddenException("Authentication required.");
    }

    private String currentEmail() {
        try { return getCurrentUser().getEmail(); } catch (Exception e) { return "system"; }
    }
}
