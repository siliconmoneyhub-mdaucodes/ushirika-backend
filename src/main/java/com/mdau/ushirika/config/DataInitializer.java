package com.mdau.ushirika.config;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.constitution.entity.GoverningDocument;
import com.mdau.ushirika.module.constitution.enums.DocumentStatus;
import com.mdau.ushirika.module.constitution.enums.DocumentType;
import com.mdau.ushirika.module.constitution.repository.GoverningDocumentRepository;
import com.mdau.ushirika.module.member.entity.EmergencyContact;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.entity.NextOfKin;
import com.mdau.ushirika.module.member.enums.Country;
import com.mdau.ushirika.module.member.enums.Gender;
import com.mdau.ushirika.module.member.enums.MaritalStatus;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import com.mdau.ushirika.module.payment.entity.ContributionPlan;
import com.mdau.ushirika.module.payment.enums.ContributionFrequency;
import com.mdau.ushirika.module.payment.repository.ContributionPlanRepository;
import com.mdau.ushirika.module.program.entity.Program;
import com.mdau.ushirika.module.program.enums.ProgramStatus;
import com.mdau.ushirika.module.program.enums.ProgramType;
import com.mdau.ushirika.module.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final ContributionPlanRepository planRepository;
    private final ProgramRepository programRepository;
    private final GoverningDocumentRepository governingDocumentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.superadmin.email:admin@ushirikawelfare.org}")
    private String superAdminEmail;

    @Value("${app.superadmin.password:Admin@1234}")
    private String superAdminPassword;

    @Value("${app.superadmin.phone:+254000000000}")
    private String superAdminPhone;

    @Value("${app.test-member.email:member@ushirikawelfare.org}")
    private String testMemberEmail;

    @Value("${app.test-member.password:Member@2025!}")
    private String testMemberPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureSchemaExtensions();
        fixLegacyGenderValues();
        seedSuperAdmin();
        seedTestMember();
        seedContributionPlans();
        seedPrograms();
        seedGoverningDocuments();
    }

    /**
     * ddl-auto=update has proven unreliable in this environment for schema it hasn't already
     * created -- adding columns to an existing table silently doesn't happen, and creating a
     * brand-new table (platform_settings) crashed the whole app at startup when a JPA query hit
     * it before the table existed. Raw, idempotent DDL via JdbcTemplate is the pattern already
     * proven to work here (see fixLegacyGenderValues) -- run explicitly for every schema change
     * from here on instead of trusting Hibernate to apply it automatically.
     */
    private void ensureSchemaExtensions() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS platform_settings (
                    id UUID PRIMARY KEY,
                    registration_fee_amount NUMERIC(10,2) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT now(),
                    updated_at TIMESTAMP NOT NULL DEFAULT now(),
                    created_by VARCHAR(150),
                    updated_by VARCHAR(150),
                    version BIGINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute(
                "ALTER TABLE platform_settings ADD COLUMN IF NOT EXISTS default_display_currency VARCHAR(3) NOT NULL DEFAULT 'USD'");
        jdbcTemplate.execute(
                "ALTER TABLE platform_settings ADD COLUMN IF NOT EXISTS benevolence_probation_months INTEGER");

        jdbcTemplate.execute(
                "ALTER TABLE membership_applications ADD COLUMN IF NOT EXISTS constitution_signature_name VARCHAR(200)");
        jdbcTemplate.execute(
                "ALTER TABLE membership_applications ADD COLUMN IF NOT EXISTS constitution_signature_initials VARCHAR(20)");
        jdbcTemplate.execute(
                "ALTER TABLE membership_applications ADD COLUMN IF NOT EXISTS constitution_signature_date DATE");
        jdbcTemplate.execute(
                "ALTER TABLE membership_applications ADD COLUMN IF NOT EXISTS bylaws_signature_name VARCHAR(200)");
        jdbcTemplate.execute(
                "ALTER TABLE membership_applications ADD COLUMN IF NOT EXISTS bylaws_signature_initials VARCHAR(20)");
        jdbcTemplate.execute(
                "ALTER TABLE membership_applications ADD COLUMN IF NOT EXISTS bylaws_signature_date DATE");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS partners (
                    id UUID PRIMARY KEY,
                    name VARCHAR(150) NOT NULL,
                    description TEXT,
                    website_url VARCHAR(500),
                    logo_url VARCHAR(500),
                    cloudinary_public_id VARCHAR(300),
                    active BOOLEAN NOT NULL DEFAULT true,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT now(),
                    updated_at TIMESTAMP NOT NULL DEFAULT now(),
                    created_by VARCHAR(150),
                    updated_by VARCHAR(150),
                    version BIGINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_partner_active ON partners (active)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_partner_sort_order ON partners (sort_order)");

        jdbcTemplate.execute(
                "ALTER TABLE membership_applications ADD COLUMN IF NOT EXISTS registration_fee_waived BOOLEAN NOT NULL DEFAULT false");
        jdbcTemplate.execute(
                "ALTER TABLE membership_applications ADD COLUMN IF NOT EXISTS registration_fee_waived_at TIMESTAMP");
        jdbcTemplate.execute(
                "ALTER TABLE membership_applications ADD COLUMN IF NOT EXISTS registration_fee_waived_by VARCHAR(200)");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS member_credit_balances (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
                    credit_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT now(),
                    updated_at TIMESTAMP NOT NULL DEFAULT now(),
                    created_by VARCHAR(150),
                    updated_by VARCHAR(150),
                    version BIGINT NOT NULL DEFAULT 0
                )
                """);

        // users_role_check predates the SECRETARY/CHIEF_WHIP/COMPLIANCE roles and was never
        // widened -- inserting or promoting a user to one of those roles violates it and, when
        // hit inside a CommandLineRunner, crashes the whole app at startup. Recreate it with the
        // full current UserRole enum every boot so it can never drift out of sync again.
        jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
        jdbcTemplate.execute("""
                ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN (
                    'SUPERADMIN','ADMIN','FINANCIAL_ADMIN','FINANCIAL_OFFICIAL','LEADERSHIP',
                    'SECRETARY','CHIEF_WHIP','COMPLIANCE','MEMBER','APPLICANT'
                ))
                """);

        // Same stale-check-constraint trap as users_role_check, but predating that pattern
        // entirely -- payment_basket_lines_ledger_check was hand-migrated via V020-V022 as each
        // PaymentBasketLedger value was added, but CASH_PAYMENT and CARD_ENTERED_BY_ADMIN (the
        // manual-payment features) were added straight to the Java enum with no accompanying
        // migration. Confirmed live: every admin card-entry payment failed with "violates check
        // constraint payment_basket_lines_ledger_check" -- the Stripe charge succeeded but our own
        // write of the payment line failed, so the payment was taken but never recorded. Folded
        // into the idempotent-DDL pattern now so it can't silently drift again.
        jdbcTemplate.execute("ALTER TABLE payment_basket_lines DROP CONSTRAINT IF EXISTS payment_basket_lines_ledger_check");
        jdbcTemplate.execute("""
                ALTER TABLE payment_basket_lines ADD CONSTRAINT payment_basket_lines_ledger_check CHECK (ledger IN (
                    'REGISTRATION_FEE','DUES','BENEVOLENCE_ENROLLMENT','BENEVOLENCE_APPLICATION_FEE',
                    'MGR_CONTRIBUTION','FINE',
                    'BENEVOLENCE_REPLENISHMENT','PROGRAM_APPLICATION_PREPAY','GENERAL_CONTRIBUTION',
                    'CASH_PAYMENT','CARD_ENTERED_BY_ADMIN'
                ))
                """);

        // Same stale-check-constraint trap as users_role_check -- official_title is an
        // @Enumerated(STRING) column with no explicit CHECK in the entity, so if Hibernate ever
        // auto-generated one against the old 11-value OfficialTitle set (CHAIRPERSON, PATRON, etc.)
        // it would reject every new 7-title executive value on write. Dropped outright rather than
        // recreated, matching mgr_join_requests_status_check/user_capabilities -- validated at the
        // Java layer instead.
        jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_official_title_check");

        // Dropping the constraint above stopped writes from being rejected, but rows written under
        // the old 11-value OfficialTitle set (CHAIRPERSON, PATRON, SECRETARY_GENERAL, etc.) are
        // still sitting in the column. @Enumerated(STRING) has no tolerance for an unknown name --
        // Hibernate throws IllegalArgumentException deserializing any such row, which crashes every
        // endpoint that loads that User (Applications, Contributions, Officials...), not just ones
        // that touch title directly. Null out anything that isn't one of the current 7 values;
        // affected officials just need a current title re-assigned from the Officials UI.
        jdbcTemplate.update("""
                UPDATE users SET official_title = NULL
                WHERE official_title IS NOT NULL
                AND official_title NOT IN (
                    'EXECUTIVE_CHAIRMAN','EXECUTIVE_VICE_CHAIRMAN','EXECUTIVE_SECRETARY',
                    'EXECUTIVE_VICE_SECRETARY','EXECUTIVE_TREASURER','EXECUTIVE_CHIEF_WHIP',
                    'BENEVOLENCE_COORDINATOR'
                )
                """);

        jdbcTemplate.execute(
                "ALTER TABLE meetings ADD COLUMN IF NOT EXISTS reminder_24h_sent BOOLEAN NOT NULL DEFAULT false");
        jdbcTemplate.execute(
                "ALTER TABLE meetings ADD COLUMN IF NOT EXISTS reminder_6h_sent BOOLEAN NOT NULL DEFAULT false");
        jdbcTemplate.execute(
                "ALTER TABLE events ADD COLUMN IF NOT EXISTS reminder_24h_sent BOOLEAN NOT NULL DEFAULT false");
        jdbcTemplate.execute(
                "ALTER TABLE events ADD COLUMN IF NOT EXISTS reminder_6h_sent BOOLEAN NOT NULL DEFAULT false");

        // Same stale-check-constraint trap as users_role_check -- in_app_notifications.category
        // predates EVENT_REMINDER, so recreate it with the full current category set every boot.
        jdbcTemplate.execute("ALTER TABLE in_app_notifications DROP CONSTRAINT IF EXISTS in_app_notifications_category_check");
        jdbcTemplate.execute("""
                ALTER TABLE in_app_notifications ADD CONSTRAINT in_app_notifications_category_check CHECK (category IN (
                    'ANNOUNCEMENT','MEETING_REMINDER','EVENT_REMINDER','ATTENDANCE_WARNING','FINE',
                    'WELFARE_CLAIM','REPLENISHMENT','MGR_PAYMENT','DUES_REMINDER','ELECTION',
                    'MEMBERSHIP_STATUS','GENERAL'
                ))
                """);

        // migrations/V019__messaging.sql was never actually applied -- this project has no Flyway/
        // Liquibase wired up, so files under migrations/ are historical documentation only, not
        // something that runs automatically. conversation_threads/conversation_messages never
        // existed in production; every attempt to message a member has been failing with
        // "relation conversation_threads does not exist" since the feature was built. Same fix
        // pattern as member_credit_balances above: idempotent raw DDL, run every boot.
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS conversation_threads (
                    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    member_id            UUID NOT NULL REFERENCES users(id),
                    program_id           UUID REFERENCES programs(id),
                    member_last_read_at  TIMESTAMP,
                    staff_last_read_at   TIMESTAMP,
                    last_message_at      TIMESTAMP,
                    version              BIGINT NOT NULL DEFAULT 0,
                    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
                    created_by           VARCHAR(150),
                    updated_by           VARCHAR(150),
                    CONSTRAINT uq_thread_member_program UNIQUE (member_id, program_id)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_thread_member ON conversation_threads (member_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_thread_program ON conversation_threads (program_id)");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS conversation_messages (
                    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    thread_id     UUID NOT NULL REFERENCES conversation_threads(id),
                    sender_id     UUID NOT NULL REFERENCES users(id),
                    from_member   BOOLEAN NOT NULL,
                    body          VARCHAR(2000) NOT NULL,
                    version       BIGINT NOT NULL DEFAULT 0,
                    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
                    created_by    VARCHAR(150),
                    updated_by    VARCHAR(150)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_message_thread ON conversation_messages (thread_id)");

        // ddl-auto=update did not add these columns to the already-existing election_seats/
        // election_candidacies tables -- confirmed live (SQLGrammarException: column
        // "executive_tier" does not exist) when creating an election right after this feature
        // shipped. Column additions are supposed to be the reliable case for ddl-auto=update,
        // but evidently not always; raw idempotent DDL is the only mechanism actually proven to
        // work in this project, so used here too.
        jdbcTemplate.execute("ALTER TABLE election_seats ADD COLUMN IF NOT EXISTS executive_tier BOOLEAN NOT NULL DEFAULT FALSE");
        jdbcTemplate.execute("ALTER TABLE election_candidacies ADD COLUMN IF NOT EXISTS video_url VARCHAR(500)");

        // Backs User.capabilities (@ElementCollection) -- granular admin permissions independently
        // attachable to any user on top of their UserRole. No CHECK constraint on `capability`
        // deliberately -- users_role_check/in_app_notifications_category_check above have already
        // drifted out of sync with their enum twice and crashed startup; Capability is validated at
        // the Java/Jackson layer instead, same as every other @Enumerated(STRING) column without an
        // explicit CHECK in this schema.
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_capabilities (
                    user_id    UUID NOT NULL REFERENCES users(id),
                    capability VARCHAR(40) NOT NULL,
                    PRIMARY KEY (user_id, capability)
                )
                """);

        // Benevolence's own dedicated join-request flow (mirrors mgr_join_requests, which reached
        // production fine, but new tables in this project have repeatedly NOT been created by
        // ddl-auto=update alone -- e.g. conversation_threads/conversation_messages above -- so
        // this gets the same explicit idempotent DDL treatment on principle.
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS benevolence_join_requests (
                    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id         UUID NOT NULL REFERENCES users(id),
                    status          VARCHAR(15) NOT NULL DEFAULT 'PENDING',
                    member_notes    VARCHAR(500),
                    admin_notes     VARCHAR(500),
                    form_sent_by_id UUID REFERENCES users(id),
                    form_sent_at    TIMESTAMP,
                    responded_by_id UUID REFERENCES users(id),
                    responded_at    TIMESTAMP,
                    version         BIGINT NOT NULL DEFAULT 0,
                    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                    created_by      VARCHAR(150),
                    updated_by      VARCHAR(150)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_bjr_user ON benevolence_join_requests (user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_bjr_status ON benevolence_join_requests (status)");

        // MGR join requests are no longer tied to a specific cycle at application time --
        // applications are accepted any time and only get a cycle_id once actually ADMITTED (swept
        // in at that cycle's activation). cycle_id must become nullable and its old (cycle_id,
        // user_id) uniqueness no longer makes sense now that cycle_id starts out null for everyone.
        jdbcTemplate.execute("ALTER TABLE mgr_join_requests DROP CONSTRAINT IF EXISTS uq_mgr_jr_cycle_user");
        jdbcTemplate.execute("ALTER TABLE mgr_join_requests ALTER COLUMN cycle_id DROP NOT NULL");
        jdbcTemplate.execute("ALTER TABLE mgr_join_requests ADD COLUMN IF NOT EXISTS admitted_at TIMESTAMP");

        // Same stale-check-constraint trap as users_role_check -- ddl-auto=update auto-generated
        // mgr_join_requests_status_check against the original PENDING/APPROVED/REJECTED values and
        // never widens it on its own. Confirmed live: approving a request into the new WAITLISTED
        // status threw "violates check constraint mgr_join_requests_status_check". Dropped outright
        // (no CHECK constraint) rather than recreated, matching user_capabilities/other newer
        // @Enumerated(STRING) columns in this schema -- validated at the Java layer instead.
        jdbcTemplate.execute("ALTER TABLE mgr_join_requests DROP CONSTRAINT IF EXISTS mgr_join_requests_status_check");

        // Send Form step (mirrors benevolence_join_requests) + per-cycle waitlist opt-in ask --
        // added when MGR's join flow was rebuilt to add a proper info-form step and replace blind
        // FCFS admission with an automated per-cycle "join this one or keep waiting" invite.
        jdbcTemplate.execute("ALTER TABLE mgr_join_requests ADD COLUMN IF NOT EXISTS form_sent_by_id UUID");
        jdbcTemplate.execute("ALTER TABLE mgr_join_requests ADD COLUMN IF NOT EXISTS form_sent_at TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE mgr_join_requests ADD COLUMN IF NOT EXISTS invited_cycle_id UUID");
        jdbcTemplate.execute("ALTER TABLE mgr_join_requests ADD COLUMN IF NOT EXISTS invited_at TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE mgr_join_requests ADD COLUMN IF NOT EXISTS cycle_opt_in BOOLEAN");
        jdbcTemplate.execute("ALTER TABLE mgr_join_requests ADD COLUMN IF NOT EXISTS cycle_responded_at TIMESTAMP");

        // WHATSAPP added to NotificationChannel -- same stale-check-constraint trap as every other
        // @Enumerated(STRING) column in this schema. Defensive: harmless no-op if no constraint
        // was ever auto-generated for this column, but cheap insurance if one was.
        jdbcTemplate.execute("ALTER TABLE notification_logs DROP CONSTRAINT IF EXISTS notification_logs_channel_check");

        // enrollment_open is retired -- MGR applications are now always accepted (queued via
        // WAITLISTED/ADMITTED status instead of a per-cycle open/closed gate).
        jdbcTemplate.execute("ALTER TABLE mgr_cycles DROP COLUMN IF EXISTS enrollment_open");

        // Money-movement ledger fields on audit_logs -- amount/direction let a money-moving action
        // double as a ledger entry instead of needing a separate ledger table; actor_title snapshots
        // the acting official's title the same way actor_name/actor_role already do, so it stays
        // historically accurate even if the title later changes.
        jdbcTemplate.execute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor_title VARCHAR(30)");
        jdbcTemplate.execute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS amount NUMERIC(12,2)");
        jdbcTemplate.execute("ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS direction VARCHAR(10)");

        // Admin-panel entry OTP -- step-up confirmation required the first time an admin-tier
        // session moves from the member portal into /admin, same shape as the existing
        // email-verification/password-reset OTP columns above.
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS admin_entry_otp VARCHAR(6)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS admin_entry_otp_expiry TIMESTAMP");

        // Bank reconciliation (Finance Visibility plan, Phase 8) -- physical-vs-expected balance
        // checks, org-wide (scope NULL) and per-program (scope = a ledger entityType).
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bank_reconciliations (
                    id                 UUID PRIMARY KEY,
                    scope              VARCHAR(50),
                    physical_balance   NUMERIC(12,2) NOT NULL,
                    expected_balance   NUMERIC(12,2) NOT NULL,
                    variance           NUMERIC(12,2) NOT NULL,
                    note               TEXT,
                    recorded_by_id     UUID NOT NULL REFERENCES users(id),
                    recorded_by_name   VARCHAR(200) NOT NULL,
                    recorded_by_title  VARCHAR(30),
                    recorded_at        TIMESTAMP NOT NULL DEFAULT now()
                )
                """);

        // mgr_slots had no unique constraint on (cycle_id, slot_number) -- only on (cycle_id,
        // user_id) -- so a removed mid-cycle slot could make the next admission reissue an
        // in-use number (see MgrService#admitWaitlistedMembers/#assignSlot, now fixed to seed
        // from MAX(slotNumber) instead of COUNT). This adds the constraint as defense-in-depth,
        // but guarded: only if no existing duplicate would violate it, since this environment has
        // hit boot-crashing constraint additions against dirty data before (see users_role_check
        // below). If a duplicate already exists, this silently skips rather than crashing startup
        // -- the app-level fix above is what actually stops new duplicates.
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM mgr_slots GROUP BY cycle_id, slot_number HAVING COUNT(*) > 1
                    ) AND NOT EXISTS (
                        SELECT 1 FROM pg_constraint WHERE conname = 'uq_mgr_slot_cycle_number'
                    ) THEN
                        ALTER TABLE mgr_slots ADD CONSTRAINT uq_mgr_slot_cycle_number UNIQUE (cycle_id, slot_number);
                    END IF;
                END $$;
                """);

        // Member status lifecycle (Phase 8 of the post-launch build plan) -- active/membershipCeased
        // previously changed silently, with no reason a report or the member themselves could ever
        // see. Denormalized snapshot fields on users for fast reads, full history in its own table.
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS current_status_reason VARCHAR(30)");
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS current_status_changed_at TIMESTAMP");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS member_status_changes (
                    id                  UUID PRIMARY KEY,
                    user_id             UUID NOT NULL REFERENCES users(id),
                    previous_status     VARCHAR(20) NOT NULL,
                    new_status          VARCHAR(20) NOT NULL,
                    reason              VARCHAR(30) NOT NULL,
                    changed_by_user_id  UUID,
                    notes               VARCHAR(500),
                    created_at          TIMESTAMP NOT NULL DEFAULT now(),
                    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
                    created_by          VARCHAR(150),
                    updated_by          VARCHAR(150),
                    version             BIGINT
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_status_change_user ON member_status_changes(user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_status_change_created_at ON member_status_changes(created_at)");
    }

    /**
     * Gender was narrowed from a 3-value enum (including PREFER_NOT_TO_SAY) to MALE/FEMALE-only
     * during the onboarding refactor, but existing rows written under the old enum were never
     * migrated. Hibernate throws IllegalArgumentException hydrating any now-invalid value --
     * which happens on MemberProfile load (e.g. during login), locking those members out
     * entirely. Runs via JdbcTemplate (raw SQL, not the repository/entity) specifically because
     * loading the row through JPA to fix it would hit the exact same crash.
     */
    private void fixLegacyGenderValues() {
        int updated = jdbcTemplate.update(
                "UPDATE member_profiles SET gender = NULL WHERE gender IS NOT NULL AND gender NOT IN ('MALE', 'FEMALE')"
        );
        if (updated > 0) {
            log.warn("Cleared legacy/invalid gender value on {} member_profiles row(s) (e.g. PREFER_NOT_TO_SAY) " +
                    "that predated the Gender enum being narrowed to MALE/FEMALE -- those members can log in " +
                    "again; the profile-completeness check will prompt them to set a real value.", updated);
        }
    }

    private void seedSuperAdmin() {
        // Matched by email, not role — there can be more than one SUPERADMIN-role user
        // (e.g. promoted directly in the DB), and this must only ever touch the one
        // account tied to SUPERADMIN_EMAIL, never whichever SUPERADMIN row it finds first.
        User superAdmin = userRepository.findByEmail(superAdminEmail)
                .orElse(null);

        boolean isNew = (superAdmin == null);

        if (isNew) {
            superAdmin = User.builder()
                    .firstName("Super")
                    .lastName("Admin")
                    .email(superAdminEmail)
                    .phone(superAdminPhone)
                    .role(UserRole.SUPERADMIN)
                    .emailVerified(true)
                    .active(true)
                    .build();
        }

        // Only set the password from env vars when the account is first created — on a fresh
        // DB that's the only way it gets a password at all. Once it exists, redeploys must
        // never touch it again: SUPERADMIN_PASSWORD silently overwriting whatever the real
        // superadmin most recently set via the app (login reset, credential-reset endpoint)
        // was exactly the bug that made the password appear to "change itself" on every deploy.
        if (isNew) {
            superAdmin.setPassword(passwordEncoder.encode(superAdminPassword));
        }
        superAdmin.setRole(UserRole.SUPERADMIN);
        superAdmin.setActive(true);
        superAdmin.setEmailVerified(true);

        userRepository.save(superAdmin);

        if (isNew) {
            log.warn("================================================================");
            log.warn("  SUPERADMIN created  : {}", superAdminEmail);
            log.warn("  Credentials sourced from APP_SUPERADMIN_EMAIL / APP_SUPERADMIN_PASSWORD env vars.");
            log.warn("================================================================");
        } else {
            log.info("SUPERADMIN credentials synced from env vars -> {}", superAdminEmail);
        }
    }

    private void seedTestMember() {
        if (userRepository.existsByEmail(testMemberEmail)) return;

        User member = User.builder()
                .firstName("Wekesa")
                .lastName("Wanjala")
                .email(testMemberEmail)
                .phone("+14695550142")
                .password(passwordEncoder.encode(testMemberPassword))
                .role(UserRole.MEMBER)
                .emailVerified(true)
                .active(true)
                .build();

        member = userRepository.save(member);

        MemberProfile profile = MemberProfile.builder()
                .user(member)
                .idNumber("TEST00000001")
                .dateOfBirth(LocalDate.of(1988, 4, 15))
                .gender(Gender.MALE)
                .street("6702 Ambercrest Dr")
                .city("Arlington")
                .zipCode("76002")
                .country(Country.KENYA)
                .kenyaCounty("Vihiga")
                .kenyaSubCounty("Sabatia")
                .kenyaVillage("Chavakali")
                .maritalStatus(MaritalStatus.MARRIED)
                .spouseName("Aisha Wanjala")
                .occupation("Registered Nurse")
                .employer("Texas Health Resources")
                .heardAboutUs("Friend or member")
                .memberId("UW-2025-0001")
                .memberSince(LocalDate.of(2022, 3, 14))
                .membershipTier("Family")
                .build();

        profile.getNextOfKin().add(NextOfKin.builder()
                .memberProfile(profile).position((short) 1)
                .fullName("Peter Wanjala").phone("+14695550143").relationship("Sibling")
                .build());
        profile.getNextOfKin().add(NextOfKin.builder()
                .memberProfile(profile).position((short) 2)
                .fullName("Aisha Wanjala").phone("+14695550144").relationship("Spouse")
                .build());
        profile.getEmergencyContacts().add(EmergencyContact.builder()
                .memberProfile(profile).position((short) 1)
                .fullName("Aisha Wanjala").phone("+14695550144").relationship("Spouse")
                .build());
        profile.getEmergencyContacts().add(EmergencyContact.builder()
                .memberProfile(profile).position((short) 2)
                .fullName("Peter Wanjala").phone("+14695550143").relationship("Sibling")
                .build());

        memberProfileRepository.save(profile);
        log.info("Test member seeded: {} / password configured via app.test-member.password", testMemberEmail);
    }

    private void seedContributionPlans() {
        if (planRepository.existsByName("Standard")) return;

        ContributionPlan standard = ContributionPlan.builder()
                .name("Standard")
                .description("Individual membership — for a single Luhya community member.")
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .frequency(ContributionFrequency.MONTHLY)
                .features(List.of(
                        "Full bereavement support",
                        "Welfare fund access",
                        "Annual Family Day attendance",
                        "Community voting rights",
                        "Scholarship fund eligibility",
                        "Member directory listing"
                ))
                .badge(null)
                .displayOrder(1)
                .active(true)
                .build();

        ContributionPlan family = ContributionPlan.builder()
                .name("Family")
                .description("Household membership — covers member, spouse, and children under 18.")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .frequency(ContributionFrequency.MONTHLY)
                .features(List.of(
                        "Everything in Standard",
                        "Spouse fully covered",
                        "Children under 18 covered",
                        "Double bereavement payout",
                        "Priority welfare queue",
                        "Family Day group tickets"
                ))
                .badge("Most Common")
                .displayOrder(2)
                .active(true)
                .build();

        ContributionPlan patron = ContributionPlan.builder()
                .name("Patron")
                .description("Extended family membership — covers member, spouse, children, and up to two additional relatives.")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .frequency(ContributionFrequency.MONTHLY)
                .features(List.of(
                        "Everything in Family",
                        "Up to 2 additional relatives covered",
                        "Named in Annual Community Report",
                        "Advisory board eligibility",
                        "Scholarship nomination rights",
                        "VIP Family Day seating"
                ))
                .badge("Extended Family")
                .displayOrder(3)
                .active(true)
                .build();

        planRepository.saveAll(List.of(standard, family, patron));
        log.info("Seeded 3 default contribution plans: Standard $25, Family $50, Patron $100");
    }

    private void seedPrograms() {
        seedProgramIfMissing("mgr", "Merry-Go-Round (MGR)", ProgramType.MGR,
                "A rotating table-banking cycle — members contribute each round and take turns receiving the full payout.");

        seedProgramIfMissing("benevolence", "Benevolence Fund", ProgramType.BENEVOLENCE,
                "Bereavement and hardship support — pay into the fund and name beneficiaries who can claim support when needed.");
    }

    private void seedProgramIfMissing(String slug, String name, ProgramType type, String shortDescription) {
        if (programRepository.existsBySlug(slug)) return;

        Program program = Program.builder()
                .name(name)
                .slug(slug)
                .shortDescription(shortDescription)
                .type(type)
                .status(ProgramStatus.ACTIVE)
                .build();
        programRepository.save(program);
        log.info("Seeded program: {}", name);
    }

    /**
     * One-time seed for the real Constitution/Bylaws text, interim pending the org's annual
     * meeting review. Guarded so it only ever touches a document once: the Constitution seed
     * skips if any CONSTITUTION row already exists, and the Bylaws seed only replaces a row
     * whose title still contains "PLACEHOLDER" — once an admin edits it (through the app, which
     * changes the title), this stops matching and redeploys never touch it again.
     */
    private void seedGoverningDocuments() {
        seedConstitutionIfMissing();
        seedOrFixBylaws();
    }

    private void seedConstitutionIfMissing() {
        if (governingDocumentRepository.existsByDocumentType(DocumentType.CONSTITUTION)) return;

        GoverningDocument doc = GoverningDocument.builder()
                .title("Ushirika Welfare Organization Constitution")
                .documentType(DocumentType.CONSTITUTION)
                .description("Interim constitution pending review at the upcoming annual meeting.")
                .contentText(readSeedText("constitution.txt"))
                .status(DocumentStatus.PUBLISHED)
                .publishedAt(LocalDateTime.now())
                .sortOrder(0)
                .build();
        governingDocumentRepository.save(doc);
        log.info("Seeded published Constitution document from resources/seed/constitution.txt");
    }

    private void seedOrFixBylaws() {
        List<GoverningDocument> placeholders = governingDocumentRepository.findAllByDocumentType(DocumentType.BYLAWS).stream()
                .filter(d -> d.getTitle() != null && d.getTitle().contains("PLACEHOLDER"))
                .toList();

        if (!placeholders.isEmpty()) {
            String text = readSeedText("bylaws.txt");
            for (GoverningDocument doc : placeholders) {
                doc.setTitle("Ushirika Welfare Organization Bylaws");
                doc.setDescription("Interim bylaws pending review at the upcoming annual meeting.");
                doc.setContentText(text);
                doc.setStatus(DocumentStatus.PUBLISHED);
                doc.setPublishedAt(LocalDateTime.now());
            }
            governingDocumentRepository.saveAll(placeholders);
            log.info("Replaced {} placeholder Bylaws document(s) with the real text", placeholders.size());
            return;
        }

        if (governingDocumentRepository.existsByDocumentType(DocumentType.BYLAWS)) return;

        GoverningDocument doc = GoverningDocument.builder()
                .title("Ushirika Welfare Organization Bylaws")
                .documentType(DocumentType.BYLAWS)
                .description("Interim bylaws pending review at the upcoming annual meeting.")
                .contentText(readSeedText("bylaws.txt"))
                .status(DocumentStatus.PUBLISHED)
                .publishedAt(LocalDateTime.now())
                .sortOrder(0)
                .build();
        governingDocumentRepository.save(doc);
        log.info("Seeded published Bylaws document from resources/seed/bylaws.txt");
    }

    private String readSeedText(String resourceName) {
        try (var is = new ClassPathResource("seed/" + resourceName).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read seed resource: " + resourceName, e);
        }
    }
}
