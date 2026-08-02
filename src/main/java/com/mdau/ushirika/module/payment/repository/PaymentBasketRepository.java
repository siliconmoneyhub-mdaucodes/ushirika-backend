package com.mdau.ushirika.module.payment.repository;

import com.mdau.ushirika.module.payment.entity.PaymentBasket;
import com.mdau.ushirika.module.payment.enums.PaymentBasketLedger;
import com.mdau.ushirika.module.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentBasketRepository extends JpaRepository<PaymentBasket, UUID> {
    Optional<PaymentBasket> findBySessionId(String sessionId);

    boolean existsByMemberIdAndStatusAndLines_Ledger(UUID memberId, PaymentStatus status, PaymentBasketLedger ledger);
}
