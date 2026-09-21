package com.mdau.ushirika.module.auth.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByOnboardingLoginToken(String onboardingLoginToken);

    Optional<User> findByActivationTokenHash(String activationTokenHash);

    Optional<User> findByActivationTicketHash(String activationTicketHash);

    Optional<User> findFirstByRole(UserRole role);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByRole(UserRole role);

    List<User> findAllByRole(UserRole role);

    List<User> findAllByRoleIn(List<UserRole> roles);

    List<User> findAllByActiveTrue();

    List<User> findAllByOfficialTitleIsNotNull();

    long countByRole(UserRole role);

    long countByRoleAndActiveTrueAndMembershipCeasedFalse(UserRole role);

    // Matches "First Last" or "Last First" — handles reversed entry and any casing.
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(CONCAT(u.firstName, ' ', u.lastName)) = LOWER(:fullName) OR " +
           "LOWER(CONCAT(u.lastName, ' ', u.firstName)) = LOWER(:fullName)")
    Optional<User> findByFullNameIgnoreCase(@Param("fullName") String fullName);

    /** Type-ahead over active members for the "pay for another member" picker — matches first
     *  name, last name, "first last", or email, case-insensitively; excludes the searcher. */
    @Query("""
            SELECT u FROM User u
            WHERE u.role = com.mdau.ushirika.module.auth.enums.UserRole.MEMBER
              AND u.active = true
              AND u.id <> :excludeId
              AND ( LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) )
            ORDER BY u.firstName, u.lastName
            """)
    List<User> searchActiveMembers(@Param("q") String q, @Param("excludeId") UUID excludeId, Pageable pageable);
}
