package com.mdau.ushirika.module.member.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberProfileRepository extends JpaRepository<MemberProfile, UUID> {

    Optional<MemberProfile> findByUser(User user);

    Optional<MemberProfile> findByMemberId(String memberId);

    boolean existsByIdNumber(String idNumber);

    long countByMemberIdNotNull();

    /**
     * Every user with an approved membership, regardless of their current {@code role} --
     * a member elected/appointed as Secretary, Chief Whip, Compliance, or Financial
     * Admin/Official is still a member and must keep receiving member-wide communications.
     */
    @Query("select mp.user from MemberProfile mp")
    List<User> findAllMemberUsers();
}
