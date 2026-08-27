package com.mdau.ushirika.module.dues.service;

import com.mdau.ushirika.common.util.AppClock;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.dues.entity.MembershipDue;
import com.mdau.ushirika.module.dues.enums.DuesStatus;
import com.mdau.ushirika.module.dues.repository.MembershipDueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * On January 1 of each year, creates a $100 dues record (due October 31) for
 * every active member who does not already have one for that year.
 * This covers Year 2+ renewals — Year 1 is created at approval time by
 * {@link MembershipDuesService#createInitialDues(User)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnualDuesRenewalScheduler {

    private final MembershipDueRepository dueRepository;
    private final UserRepository          userRepository;

    @Scheduled(cron = "0 0 1 1 1 *", zone = "America/Chicago")
    @Transactional
    public void createAnnualDues() {
        int year = AppClock.today().getYear();
        LocalDate dueDate = LocalDate.of(year, 10, 31);

        List<User> activeMembers = userRepository.findAllByActiveTrue();

        int created = 0;
        for (User user : activeMembers) {
            // SUPERADMIN is a system-administration role, not a membership tier -- an institutional
            // account (e.g. a shared org inbox promoted for break-glass access) or a developer's own
            // admin account should not silently accrue personal membership dues just for existing.
            // A superadmin who is also a genuine dues-paying community member should be tracked as
            // such deliberately (e.g. via the admin dues tools), not by this blanket yearly sweep.
            if (user.getRole() == UserRole.SUPERADMIN) continue;
            if (dueRepository.findByUserAndYear(user, year).isPresent()) continue;

            dueRepository.save(MembershipDue.builder()
                    .user(user)
                    .year(year)
                    .amount(MembershipDuesService.ANNUAL_FEE)
                    .dueDate(dueDate)
                    .status(DuesStatus.PENDING)
                    .build());
            created++;
        }
        log.info("AnnualDuesRenewalScheduler: created {} dues records for year {}", created, year);
    }
}
