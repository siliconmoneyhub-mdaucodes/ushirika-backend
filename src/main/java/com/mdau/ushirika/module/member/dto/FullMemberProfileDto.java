package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.entity.MembershipApplication;
import com.mdau.ushirika.module.member.enums.Country;
import com.mdau.ushirika.module.member.enums.Gender;
import com.mdau.ushirika.module.member.enums.MaritalStatus;
import com.mdau.ushirika.module.member.enums.UsState;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Full editable profile returned by GET /users/me/full-profile. */
public record FullMemberProfileDto(
        UUID   id,
        String memberId,
        String email,
        String firstName,
        String middleName,
        String lastName,
        String phone,
        String role,
        String photoUrl,

        // identity
        String      idNumber,
        Gender      gender,
        LocalDate   dateOfBirth,

        // address
        String  street,
        String  city,
        String  zipCode,
        UsState usState,
        Country country,
        String  kenyaCounty,
        String  kenyaSubCounty,
        String  kenyaLocation,
        String  kenyaVillage,
        String  ugandaProvince,
        String  ugandaCounty,
        String  ugandaLocation,
        String  ugandaVillage,

        // family
        MaritalStatus maritalStatus,
        String        spouseName,

        // next of kin (exactly 2) / emergency contacts (exactly 2)
        List<NextOfKinDto> nextOfKin,
        List<EmergencyContactDto> emergencyContacts,

        // occupation
        String occupation,
        String employer,

        // references
        String reference1Name,
        String reference1MemberId,
        String reference2Name,
        String reference2MemberId,

        // membership (read-only)
        LocalDate memberSince,
        String    membershipTier,

        // governance acceptance record (read-only — proof of consent)
        LocalDateTime constitutionAcceptedAt,
        String        constitutionSignatureName,
        String        constitutionSignatureInitials,
        LocalDate     constitutionSignatureDate,
        LocalDateTime bylawsAcceptedAt,
        String        bylawsSignatureName,
        String        bylawsSignatureInitials,
        LocalDate     bylawsSignatureDate
) {
    public static FullMemberProfileDto from(User user, MemberProfile p) {
        return from(user, p, null);
    }

    public static FullMemberProfileDto from(User user, MemberProfile p, MembershipApplication a) {
        String role = switch (user.getRole()) {
            case SUPERADMIN          -> "superadmin";
            case ADMIN               -> "admin";
            case LEADERSHIP          -> "leadership";
            case SECRETARY           -> "secretary";
            case CHIEF_WHIP          -> "chief_whip";
            case COMPLIANCE          -> "compliance";
            case FINANCIAL_ADMIN     -> "financial_admin";
            case FINANCIAL_OFFICIAL  -> "financial_official";
            case APPLICANT           -> "applicant";
            case MEMBER              -> "member";
        };
        return new FullMemberProfileDto(
                user.getId(),
                p != null ? p.getMemberId()       : null,
                user.getEmail(),
                user.getFirstName(),
                user.getMiddleName(),
                user.getLastName(),
                user.getPhone(),
                role,
                p != null ? p.getPhotoUrl()       : null,
                p != null ? p.getIdNumber()        : null,
                p != null ? p.getGender()          : null,
                p != null ? p.getDateOfBirth()     : null,
                p != null ? p.getStreet()          : null,
                p != null ? p.getCity()            : null,
                p != null ? p.getZipCode()         : null,
                p != null ? p.getUsState()         : null,
                p != null ? p.getCountry()         : null,
                p != null ? p.getKenyaCounty()     : null,
                p != null ? p.getKenyaSubCounty()  : null,
                p != null ? p.getKenyaLocation()   : null,
                p != null ? p.getKenyaVillage()    : null,
                p != null ? p.getUgandaProvince()  : null,
                p != null ? p.getUgandaCounty()    : null,
                p != null ? p.getUgandaLocation()  : null,
                p != null ? p.getUgandaVillage()   : null,
                p != null ? p.getMaritalStatus()   : null,
                p != null ? p.getSpouseName()      : null,
                p != null ? p.getNextOfKin().stream()
                        .map(k -> new NextOfKinDto(k.getFullName(), k.getPhone(), k.getRelationship()))
                        .toList() : List.of(),
                p != null ? p.getEmergencyContacts().stream()
                        .map(c -> new EmergencyContactDto(c.getFullName(), c.getPhone(), c.getRelationship()))
                        .toList() : List.of(),
                p != null ? p.getOccupation()      : null,
                p != null ? p.getEmployer()        : null,
                p != null ? p.getReference1Name()      : null,
                p != null ? p.getReference1MemberId()  : null,
                p != null ? p.getReference2Name()      : null,
                p != null ? p.getReference2MemberId()  : null,
                p != null ? p.getMemberSince()     : null,
                p != null ? p.getMembershipTier()  : null,

                a != null ? a.getConstitutionAcceptedAt()         : null,
                a != null ? a.getConstitutionSignatureName()      : null,
                a != null ? a.getConstitutionSignatureInitials()  : null,
                a != null ? a.getConstitutionSignatureDate()      : null,
                a != null ? a.getBylawsAcceptedAt()               : null,
                a != null ? a.getBylawsSignatureName()            : null,
                a != null ? a.getBylawsSignatureInitials()        : null,
                a != null ? a.getBylawsSignatureDate()            : null
        );
    }
}
