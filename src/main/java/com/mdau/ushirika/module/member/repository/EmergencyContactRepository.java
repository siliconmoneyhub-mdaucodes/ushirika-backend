package com.mdau.ushirika.module.member.repository;

import com.mdau.ushirika.module.member.entity.EmergencyContact;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, java.util.UUID> {
    List<EmergencyContact> findByMemberProfileOrderByPosition(MemberProfile memberProfile);
}
