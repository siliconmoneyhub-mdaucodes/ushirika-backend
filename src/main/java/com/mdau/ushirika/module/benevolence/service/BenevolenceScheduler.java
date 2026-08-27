package com.mdau.ushirika.module.benevolence.service;

import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.benevolence.entity.BenevolenceEnrollment;
import com.mdau.ushirika.module.benevolence.enums.EnrollmentStatus;
import com.mdau.ushirika.module.benevolence.repository.BenevolenceEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenevolenceScheduler {

    private final BenevolenceEnrollmentRepository enrollmentRepo;

    /** Runs daily at 02:00 — promotes members out of probation once probationEndsAt has passed. */
    @Scheduled(cron = "0 0 2 * * *", zone = "America/Chicago")
    @Transactional
    public void promoteProbationToEligible() {
        List<BenevolenceEnrollment> probation = enrollmentRepo.findAllByStatus(EnrollmentStatus.PROBATION);
        LocalDate today = AppClock.today();
        int promoted = 0;
        for (BenevolenceEnrollment e : probation) {
            if (e.getProbationEndsAt() != null && !today.isBefore(e.getProbationEndsAt())) {
                e.setStatus(EnrollmentStatus.ELIGIBLE);
                enrollmentRepo.save(e);
                promoted++;
            }
        }
        if (promoted > 0) {
            log.info("BenevolenceScheduler: promoted {} members from PROBATION to ELIGIBLE", promoted);
        }
    }
}
