package com.mdau.ushirika.module.event.repository;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.event.entity.Event;
import com.mdau.ushirika.module.event.entity.EventRegistration;
import com.mdau.ushirika.module.event.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {

    Optional<EventRegistration> findByEventAndUser(Event event, User user);

    Optional<EventRegistration> findByReferenceCode(String referenceCode);

    boolean existsByEventAndUser(Event event, User user);

    Page<EventRegistration> findAllByUserOrderByRegisteredAtDesc(User user, Pageable pageable);

    Page<EventRegistration> findAllByEventOrderByRegisteredAtAsc(Event event, Pageable pageable);

    Page<EventRegistration> findAllByEventAndStatusOrderByRegisteredAtAsc(
            Event event, RegistrationStatus status, Pageable pageable);

    java.util.List<EventRegistration> findAllByEventAndStatus(Event event, RegistrationStatus status);

    long countByEventAndStatusIn(Event event, java.util.List<RegistrationStatus> statuses);
}
