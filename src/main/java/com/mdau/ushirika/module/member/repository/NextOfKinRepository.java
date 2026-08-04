package com.mdau.ushirika.module.member.repository;

import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.entity.NextOfKin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NextOfKinRepository extends JpaRepository<NextOfKin, java.util.UUID> {
    List<NextOfKin> findByMemberProfileOrderByPosition(MemberProfile memberProfile);
}
