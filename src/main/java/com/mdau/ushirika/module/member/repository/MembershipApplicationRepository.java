package com.mdau.ushirika.module.member.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipApplicationRepository extends JpaRepository<MembershipApplication, UUID> {

    Optional<MembershipApplication> findByReferenceNumber(String referenceNumber);

    Optional<MembershipApplication> findByUser(User user);

    Page<MembershipApplication> findAllByStatus(ApplicationStatus status, Pageable pageable);

    Page<MembershipApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(ApplicationStatus status);

    /** Candidates for the daily onboarding-reminder scheduler -- filtered further in Java for
     * "not reminded in the last ~24h", since expressing that null-or-before cutoff cleanly as a
     * derived query name gets unwieldy. */
    List<MembershipApplication> findByStatusIn(List<ApplicationStatus> statuses);
}
