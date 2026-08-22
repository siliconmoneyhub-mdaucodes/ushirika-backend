package com.mdau.ushirika.module.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

/**
 * One-time cleanup of the accounts, applications, payments, and other records created during
 * pre-launch development and QA testing -- reviewed row-by-row against a database backup on
 * 2026-08-21 and confirmed with the org's superadmin before this was written. See the Developer
 * panel's "Test Data Cleanup" tool for the preview/execute UI.
 *
 * Every DELETE below is keyed to an explicit, hardcoded list of confirmed-test identifiers
 * (emails, or exact primary keys for the handful of admin-only tables like elections/mgr_cycles
 * whose content was individually read and verified) -- never to "everyone except the accounts
 * we're keeping." That distinction matters: a real member could sign up between this being
 * written and it being run, and a blanket exclusion list would delete them right along with the
 * test data. Matching only these specific known-test values means anything created after the
 * 2026-08-21 review is untouched no matter when this actually executes.
 *
 * Deletion order matters -- every step here runs child tables before the parent they reference,
 * because the schema (Hibernate ddl-auto, no Flyway/Liquibase) does not have ON DELETE CASCADE
 * on these foreign keys. Getting the order wrong fails loudly with a constraint violation rather
 * than corrupting anything, since execute() runs the whole list in one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataCleanupService {

    private final NamedParameterJdbcTemplate jdbc;

    /** The 16 confirmed-test accounts (seeded/dev/QA identities) -- see class Javadoc. */
    private static final List<String> TEST_EMAILS = List.of(
            "member@ushirikawelfare.org",
            "muneneprince414@gmail.com",
            "mdaupius@gmail.com",
            "admin@ushirikawelfare.org",
            "mdau910+livetest1@gmail.com",
            "siliconmoneyhub@gmail.com",
            "mulae747@gmail.com",
            "lezivibe@gmail.com",
            "piusmdau@gmail.com",
            "lezisign@gmail.com",
            "mdau910+brianwafula@gmail.com",
            "joe.wamoto@outlook.com",
            "malosh47@gmail.com",
            "lynnanderson512@gmail.com",
            "alucheri@yahoo.com",
            "hesbornmaghanga44@gmail.com"
    );

    /** Rejected test applications that never got far enough to create a user account, so they
     *  have no user_id to match on -- membership_applications is matched by applicant_email
     *  instead, which covers these too. */
    private static final List<String> ORPHAN_APPLICATION_EMAILS = List.of(
            "nyakioann160@gmail.com",
            "josephinemurunda13@gmail.com"
    );

    /** The two accounts being kept (Joe Jones -- founder/superadmin who will run onboarding
     *  going forward -- and the developer's own working superadmin login). Each has a couple of
     *  leftover Stripe test-mode payment_baskets rows from development, which the org confirmed
     *  should be cleared too even though the accounts themselves stay. Used only for that one
     *  payment_baskets/payment_basket_lines step -- everywhere else, only TEST_EMAILS applies. */
    private static final List<String> KEEP_EMAILS = List.of(
            "joejones1dfw@gmail.com",
            "mdaucodes@gmail.com"
    );

    /** Exact primary keys, individually confirmed by reading their content in the 2026-08-21
     *  backup, for the handful of tables that are wholesale test data but aren't keyed to a
     *  specific test user (admin-only tables where "created_by" is often the kept superadmin
     *  account, so it can't be used to distinguish test rows from real future ones). */
    private static final List<String> TEST_ELECTION_IDS = List.of(
            "002609b3-1c8c-4196-9494-9d773493d361", "691c4e32-cb67-49fe-8d48-0680c2a64542");
    private static final List<String> TEST_MGR_CYCLE_IDS = List.of(
            "e103fd61-c630-41d9-93d9-b0dfe149d598", "ed5641fd-75a9-491e-92b6-f685bea0f57c",
            "e0b9f31e-2cd4-44e0-b213-2faaa66cf8b1");
    private static final List<String> TEST_MEETING_IDS = List.of("23bf8d2c-65d8-4f58-bd23-2dde3c97e002");
    private static final List<String> TEST_EVENT_IDS = List.of("edf84977-a825-4d87-a59d-0b10704fc0f3");

    private record Step(String table, String description, String whereSql, MapSqlParameterSource params) {}

    /** Counts what execute() would delete, table by table, without changing anything. */
    public LinkedHashMap<String, Long> preview() {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        for (Step step : buildSteps()) {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + step.table() + " WHERE " + step.whereSql(),
                    step.params(), Long.class);
            counts.put(step.table() + " -- " + step.description(), count == null ? 0 : count);
        }
        return counts;
    }

    /** Actually deletes the rows preview() counted, in one transaction -- all or nothing. */
    @Transactional
    public LinkedHashMap<String, Long> execute() {
        LinkedHashMap<String, Long> deleted = new LinkedHashMap<>();
        for (Step step : buildSteps()) {
            int count = jdbc.update("DELETE FROM " + step.table() + " WHERE " + step.whereSql(), step.params());
            deleted.put(step.table() + " -- " + step.description(), (long) count);
            log.info("Test data cleanup: deleted {} rows from {} ({})", count, step.table(), step.description());
        }
        return deleted;
    }

    private List<Step> buildSteps() {
        MapSqlParameterSource emails = new MapSqlParameterSource("emails", TEST_EMAILS);
        MapSqlParameterSource appEmails = new MapSqlParameterSource("emails",
                Stream.concat(TEST_EMAILS.stream(), ORPHAN_APPLICATION_EMAILS.stream()).toList());
        MapSqlParameterSource paymentEmails = new MapSqlParameterSource("emails",
                Stream.concat(TEST_EMAILS.stream(), KEEP_EMAILS.stream()).toList());
        MapSqlParameterSource electionIds = new MapSqlParameterSource("ids", TEST_ELECTION_IDS);
        MapSqlParameterSource cycleIds = new MapSqlParameterSource("ids", TEST_MGR_CYCLE_IDS);
        MapSqlParameterSource meetingIds = new MapSqlParameterSource("ids", TEST_MEETING_IDS);
        MapSqlParameterSource eventIds = new MapSqlParameterSource("ids", TEST_EVENT_IDS);
        MapSqlParameterSource contactPattern = new MapSqlParameterSource("pattern", "mdau910+captcha%");

        String userSub = "(SELECT id FROM users WHERE email IN (:emails))";
        String paymentUserSub = "(SELECT id FROM users WHERE email IN (:emails))";

        List<Step> steps = new ArrayList<>();

        // ── Children of member_profiles / payment_baskets / conversation_threads / benevolence_enrollments / membership_applications ──
        steps.add(new Step("member_next_of_kin", "next-of-kin for deleted member profiles",
                "member_profile_id IN (SELECT id FROM member_profiles WHERE user_id IN " + userSub + ")", emails));
        steps.add(new Step("member_emergency_contacts", "emergency contacts for deleted member profiles",
                "member_profile_id IN (SELECT id FROM member_profiles WHERE user_id IN " + userSub + ")", emails));
        steps.add(new Step("payment_basket_lines", "line items for deleted payment baskets (incl. kept accounts' test baskets)",
                "basket_id IN (SELECT id FROM payment_baskets WHERE member_id IN " + paymentUserSub + ")", paymentEmails));
        steps.add(new Step("conversation_messages", "messages in deleted conversation threads",
                "thread_id IN (SELECT id FROM conversation_threads WHERE member_id IN " + userSub + ")", emails));
        steps.add(new Step("enrollment_payments", "payments for deleted Benevolence enrollments",
                "enrollment_id IN (SELECT id FROM benevolence_enrollments WHERE user_id IN " + userSub + ")", emails));
        steps.add(new Step("benevolence_beneficiaries", "beneficiaries for deleted Benevolence enrollments",
                "enrollment_id IN (SELECT id FROM benevolence_enrollments WHERE user_id IN " + userSub + ")", emails));
        steps.add(new Step("application_approvals", "approval decisions for deleted applications",
                "application_id IN (SELECT id FROM membership_applications WHERE applicant_email IN (:emails))", appEmails));

        // ── Test election runs (exact IDs) ──
        steps.add(new Step("election_vote_tallies", "vote tallies for test elections",
                "candidacy_id IN (SELECT id FROM election_candidacies WHERE election_id IN (:ids))", electionIds));
        steps.add(new Step("election_results", "results for test elections", "election_id IN (:ids)", electionIds));
        steps.add(new Step("election_vote_receipts", "vote receipts for test elections", "election_id IN (:ids)", electionIds));
        steps.add(new Step("election_candidacies", "candidacies for test elections", "election_id IN (:ids)", electionIds));
        steps.add(new Step("election_seats", "seats for test elections", "election_id IN (:ids)", electionIds));
        steps.add(new Step("elections", "test election runs", "id IN (:ids)", electionIds));

        // ── Test MGR cycles (exact IDs) ──
        steps.add(new Step("mgr_contributions", "contributions for test MGR cycles", "cycle_id IN (:ids)", cycleIds));
        steps.add(new Step("mgr_slots", "slots for test MGR cycles", "cycle_id IN (:ids)", cycleIds));
        steps.add(new Step("mgr_join_requests", "join requests for test MGR cycles",
                "cycle_id IN (:ids) OR invited_cycle_id IN (:ids)", cycleIds));
        steps.add(new Step("mgr_cycles", "test MGR cycles", "id IN (:ids)", cycleIds));

        // ── Test Member Stories submission (forum_posts) ──
        steps.add(new Step("forum_post_media", "media for test forum/story posts",
                "post_id IN (SELECT id FROM forum_posts WHERE member_id IN " + userSub + ")", emails));
        steps.add(new Step("forum_posts", "test forum/story posts", "member_id IN " + userSub, emails));

        // ── Exact-ID admin test records ──
        steps.add(new Step("meetings", "test meeting record (\"Claude QR Test\")", "id IN (:ids)", meetingIds));
        steps.add(new Step("events", "test event record (\"Claude Live Test Event\")", "id IN (:ids)", eventIds));

        // ── CAPTCHA verification test messages -- matched by email pattern, not wholesale,
        //    since this table also receives real public submissions ──
        steps.add(new Step("contact_messages", "CAPTCHA verification test messages", "email LIKE :pattern", contactPattern));

        // ── Direct user-linked tables ──
        steps.add(new Step("bank_reconciliations", "reconciliations recorded by test accounts",
                "recorded_by_id IN " + userSub, emails));
        steps.add(new Step("in_app_notifications", "in-app notifications for test accounts", "user_id IN " + userSub, emails));
        steps.add(new Step("refresh_tokens", "sessions for test accounts", "user_id IN " + userSub, emails));
        steps.add(new Step("audit_logs", "audit log entries by test accounts", "actor_id IN " + userSub, emails));
        steps.add(new Step("user_capabilities", "capabilities for test accounts", "user_id IN " + userSub, emails));
        steps.add(new Step("program_admin_assignments", "program assignments for test accounts", "user_id IN " + userSub, emails));
        steps.add(new Step("program_applications", "program applications by test accounts", "applicant_id IN " + userSub, emails));
        steps.add(new Step("member_contributions", "contributions by test accounts", "user_id IN " + userSub, emails));
        steps.add(new Step("member_credit_balances", "credit balances for test accounts", "user_id IN " + userSub, emails));
        steps.add(new Step("member_status_changes", "status change history for test accounts", "user_id IN " + userSub, emails));
        steps.add(new Step("membership_dues", "dues records for test accounts", "user_id IN " + userSub, emails));
        steps.add(new Step("peer_payments", "peer payments by test accounts", "member_id IN " + userSub, emails));
        steps.add(new Step("member_profiles", "member profiles for test accounts", "user_id IN " + userSub, emails));
        steps.add(new Step("payment_baskets", "payment baskets for test accounts + kept superadmins' leftover test-mode baskets",
                "member_id IN " + paymentUserSub, paymentEmails));
        steps.add(new Step("conversation_threads", "conversation threads for test accounts", "member_id IN " + userSub, emails));
        steps.add(new Step("benevolence_enrollments", "Benevolence enrollments for test accounts", "user_id IN " + userSub, emails));
        steps.add(new Step("benevolence_join_requests", "Benevolence join requests for test accounts", "user_id IN " + userSub, emails));
        steps.add(new Step("membership_applications", "membership applications by test/orphan emails",
                "applicant_email IN (:emails)", appEmails));

        // ── Users themselves, last -- everything above must already be gone ──
        steps.add(new Step("users", "the 16 confirmed-test accounts", "email IN (:emails)", emails));

        return steps;
    }
}
