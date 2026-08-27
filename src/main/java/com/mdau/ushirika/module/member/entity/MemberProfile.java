package com.mdau.ushirika.module.member.entity;

import com.mdau.ushirika.common.entity.BaseEntity;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.member.enums.Country;
import com.mdau.ushirika.module.member.enums.Gender;
import com.mdau.ushirika.module.member.enums.MaritalStatus;
import com.mdau.ushirika.module.member.enums.UsState;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "member_profiles",
    indexes = {
        @Index(name = "idx_mp_country",      columnList = "country"),
        @Index(name = "idx_mp_gender",       columnList = "gender"),
        @Index(name = "idx_mp_member_since", columnList = "member_since"),
        @Index(name = "idx_mp_tier",         columnList = "membership_tier")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true,
                foreignKey = @ForeignKey(name = "fk_mp_user"))
    private User user;

    // ── Identity ──────────────────────────────────────────────────────────────
    // Nullable at the DB level — a bare profile row is created before onboarding
    // fills these in. Completeness is enforced as an application-layer gate in
    // OnboardingService.submitRegistration(), not a DB constraint.

    @Column(name = "id_number", unique = true, length = 20)
    private String idNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    // ── Address ───────────────────────────────────────────────────────────────

    @Column(name = "street", length = 300)
    private String street;

    @Column(name = "city", length = 150)
    private String city;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    /** The US state of the member's residential address above -- independent of country,
     * which tracks country of origin (Kenya/Uganda), not where they actually live. */
    @Enumerated(EnumType.STRING)
    @Column(name = "us_state", length = 30)
    private UsState usState;

    @Enumerated(EnumType.STRING)
    @Column(name = "country", length = 10)
    private Country country;

    /** Required together when country = KENYA. */
    @Column(name = "kenya_county", length = 100)
    private String kenyaCounty;

    @Column(name = "kenya_sub_county", length = 100)
    private String kenyaSubCounty;

    /** Location/Village -- the administrative unit below sub-county, distinct from
     * kenyaVillage below (which is actually "Subdivision/Apartment Complex", a more
     * urban/estate-level concept than Kenya's Location/Village administrative terms). */
    @Column(name = "kenya_location", length = 100)
    private String kenyaLocation;

    @Column(name = "kenya_village", length = 100)
    private String kenyaVillage;

    /** Required together when country = UGANDA. */
    @Column(name = "uganda_province", length = 100)
    private String ugandaProvince;

    @Column(name = "uganda_county", length = 100)
    private String ugandaCounty;

    @Column(name = "uganda_location", length = 100)
    private String ugandaLocation;

    @Column(name = "uganda_village", length = 100)
    private String ugandaVillage;

    @Column(name = "photo_url", length = 1000)
    private String photoUrl;

    // ── Family ────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 20)
    private MaritalStatus maritalStatus;

    @Column(name = "spouse_name", length = 150)
    private String spouseName;

    /** JSON array: [{"name":"...","dateOfBirth":"YYYY-MM-DD"},...] */
    @Column(name = "children_json", columnDefinition = "TEXT")
    private String childrenJson;

    // ── Next of Kin / Emergency Contact ──────────────────────────────────────
    // Exactly two of each, distinguished by position — see NextOfKin/EmergencyContact.

    // EAGER is deliberate here — capped at 2 rows each, and read outside a
    // transactional boundary in several places (open-in-view is disabled).
    @OneToMany(mappedBy = "memberProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    @Builder.Default
    private List<NextOfKin> nextOfKin = new ArrayList<>();

    @OneToMany(mappedBy = "memberProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    @Builder.Default
    private List<EmergencyContact> emergencyContacts = new ArrayList<>();

    // ── Occupation ────────────────────────────────────────────────────────────

    @Column(name = "occupation", length = 150)
    private String occupation;

    @Column(name = "employer", length = 200)
    private String employer;

    // ── Member References ─────────────────────────────────────────────────────

    @Column(name = "reference1_name", length = 150)
    private String reference1Name;

    @Column(name = "reference1_member_id", length = 20)
    private String reference1MemberId;

    @Column(name = "reference2_name", length = 150)
    private String reference2Name;

    @Column(name = "reference2_member_id", length = 20)
    private String reference2MemberId;

    // ── Discovery ─────────────────────────────────────────────────────────────

    @Column(name = "heard_about_us", length = 200)
    private String heardAboutUs;

    // ── Membership ────────────────────────────────────────────────────────────

    /** Assigned on membership approval — format: UW-YYYY-XXXX */
    @Column(name = "member_id", unique = true, length = 20)
    private String memberId;

    @Column(name = "member_since")
    private LocalDate memberSince;

    /**
     * Contribution plan tier name — matches ContributionPlan.name (Standard / Family / Patron).
     * Set to "Standard" on membership approval; updated by admin when member upgrades.
     */
    @Column(name = "membership_tier", length = 50)
    @Builder.Default
    private String membershipTier = "Standard";
}
