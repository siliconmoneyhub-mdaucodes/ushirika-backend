package com.mdau.ushirika.module.attendance.service;

import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.PagedResponse;
import com.mdau.ushirika.module.attendance.dto.*;
import com.mdau.ushirika.module.attendance.enums.FinePaymentStatus;
import com.mdau.ushirika.module.attendance.repository.FinePaymentRepository;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Historical self-reported fine payments from before the card-only payment migration —
 * read-only now, since fines are settled through PaymentBasketService/Stripe instead. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinePaymentService {

    private final FinePaymentRepository finePaymentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<FinePaymentDto> myPayments() {
        User member = currentUser();
        return finePaymentRepository.findAllByMemberOrderByCreatedAtDesc(member).stream()
                .map(FinePaymentDto::memberView)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<FinePaymentDto> listAll(FinePaymentStatus status, Pageable pageable) {
        if (status != null) {
            return PagedResponse.of(
                finePaymentRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable)
                    .map(FinePaymentDto::from));
        }
        return PagedResponse.of(
            finePaymentRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(FinePaymentDto::from));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
