package com.mdau.ushirika.module.payment.service;

import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.payment.dto.PeerPaymentDto;
import com.mdau.ushirika.module.payment.enums.PeerPaymentStatus;
import com.mdau.ushirika.module.payment.repository.PeerPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Historical self-reported Zelle/Venmo/CashApp payments from before the card-only payment
 * migration — read-only now, since all payments go through PaymentBasketService/Stripe instead. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeerPaymentService {

    private final PeerPaymentRepository peerPaymentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PagedResponse<PeerPaymentDto> myReports(Pageable pageable) {
        User member = currentUser();
        return PagedResponse.of(
            peerPaymentRepository.findAllByMemberOrderByCreatedAtDesc(member, pageable)
                .map(PeerPaymentDto::memberView)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<PeerPaymentDto> listAll(PeerPaymentStatus status, Pageable pageable) {
        if (status != null) {
            return PagedResponse.of(
                peerPaymentRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                    .map(PeerPaymentDto::from)
            );
        }
        return PagedResponse.of(
            peerPaymentRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(PeerPaymentDto::from)
        );
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
