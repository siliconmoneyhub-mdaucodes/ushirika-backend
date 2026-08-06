package com.mdau.ushirika.module.payment.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.payment.entity.MemberCreditBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberCreditBalanceRepository extends JpaRepository<MemberCreditBalance, UUID> {
    Optional<MemberCreditBalance> findByUser(User user);
}
