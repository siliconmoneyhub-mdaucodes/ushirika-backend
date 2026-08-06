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
