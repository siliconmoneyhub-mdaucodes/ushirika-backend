package com.mdau.ushirika.module.auth.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByOnboardingLoginToken(String onboardingLoginToken);

    Optional<User> findFirstByRole(UserRole role);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByRole(UserRole role);

    List<User> findAllByRole(UserRole role);

    List<User> findAllByRoleIn(List<UserRole> roles);

    List<User> findAllByActiveTrue();

    long countByRole(UserRole role);

    long countByRoleAndActiveTrueAndMembershipCeasedFalse(UserRole role);

    // Matches "First Last" or "Last First" — handles reversed entry and any casing.
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(CONCAT(u.firstName, ' ', u.lastName)) = LOWER(:fullName) OR " +
           "LOWER(CONCAT(u.lastName, ' ', u.firstName)) = LOWER(:fullName)")
    Optional<User> findByFullNameIgnoreCase(@Param("fullName") String fullName);
}
