package com.mdau.ushirika.module.auth.controller;

import com.mdau.ushirika.common.exception.BadRequestException;
import com.mdau.ushirika.common.exception.ConflictException;
import com.mdau.ushirika.common.exception.ResourceNotFoundException;
import com.mdau.ushirika.common.response.ApiResponse;
import com.mdau.ushirika.module.auth.dto.UpdateCredentialsRequest;
import com.mdau.ushirika.module.auth.dto.UserProfileDto;
import com.mdau.ushirika.module.auth.entity.User;
import com.mdau.ushirika.module.auth.enums.UserRole;
import com.mdau.ushirika.module.auth.repository.UserRepository;
import com.mdau.ushirika.module.auth.service.AuthService;
import com.mdau.ushirika.module.dues.service.MembershipDuesService;
import com.mdau.ushirika.module.member.dto.FullMemberProfileDto;
import com.mdau.ushirika.module.member.dto.UpdateProfileRequest;
import com.mdau.ushirika.module.member.entity.EmergencyContact;
import com.mdau.ushirika.module.member.entity.MemberProfile;
import com.mdau.ushirika.module.member.entity.NextOfKin;
import com.mdau.ushirika.module.member.repository.MemberProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Authenticated user profile")
public class UserController {

    private final UserRepository userRepository;
    private final MemberProfileRepository profileRepository;
    private final MembershipDuesService duesService;
    private final AuthService authService;

    @GetMapping("/me")
    @Operation(
        summary = "Get the authenticated user's full profile",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<UserProfileDto>> me() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
        MemberProfile profile = profileRepository.findByUser(user).orElse(null);

        // Resolve dues status only for approved members
        String duesStatus = null;
        if (user.getRole() == UserRole.MEMBER && profile != null && profile.getMemberId() != null) {
            duesStatus = duesService.getCurrentYearStatus(user)
                    .map(Enum::name)
                    .orElse(null);
        }

        return ResponseEntity.ok(ApiResponse.ok("Profile fetched", UserProfileDto.from(user, profile, duesStatus)));
    }

    @PutMapping("/me/credentials")
    @Operation(
        summary = "Update login email and/or password — current password required",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> updateCredentials(@Valid @RequestBody UpdateCredentialsRequest req) {
        authService.updateCredentials(req);
        return ResponseEntity.ok(ApiResponse.ok("Credentials updated. Please sign in with your new details."));
    }

    // ── Full profile (all editable fields) ────────────────────────────────────

    @GetMapping("/me/full-profile")
    @Operation(summary = "Get full editable profile", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<FullMemberProfileDto>> fullProfile() {
        User user = currentUser();
        MemberProfile profile = profileRepository.findByUser(user).orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(FullMemberProfileDto.from(user, profile)));
    }

    @PutMapping("/me/profile")
    @Transactional
    @Operation(summary = "Update personal profile information", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<FullMemberProfileDto>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest req) {

        User user = currentUser();

        // Phone uniqueness check (skip if same as current)
        if (!req.phone().equals(user.getPhone()) && userRepository.existsByPhone(req.phone())) {
            throw new ConflictException("That phone number is already registered to another account.");
        }

        user.setFirstName(req.firstName().trim());
        user.setMiddleName(trimOrNull(req.middleName()));
        user.setLastName(req.lastName().trim());
        user.setPhone(req.phone().trim());
        userRepository.save(user);

        MemberProfile profile = profileRepository.findByUser(user).orElseThrow(
                () -> new BadRequestException("Profile not found. Complete membership approval first."));

        if (req.idNumber() != null && !req.idNumber().isBlank() && !req.idNumber().startsWith("P-")) {
            profile.setIdNumber(req.idNumber().trim());
        }
        profile.setGender(req.gender());
        profile.setDateOfBirth(req.dateOfBirth());
        profile.setStreet(req.street().trim());
        profile.setCity(req.city().trim());
        profile.setZipCode(req.zipCode().trim());
        profile.setCountry(req.country());
        profile.setKenyaCounty(trimOrNull(req.kenyaCounty()));
        profile.setKenyaSubCounty(trimOrNull(req.kenyaSubCounty()));
        profile.setKenyaLocation(trimOrNull(req.kenyaLocation()));
        profile.setKenyaVillage(trimOrNull(req.kenyaVillage()));
        profile.setUgandaProvince(trimOrNull(req.ugandaProvince()));
        profile.setUgandaCounty(trimOrNull(req.ugandaCounty()));
        profile.setUgandaLocation(trimOrNull(req.ugandaLocation()));
        profile.setUgandaVillage(trimOrNull(req.ugandaVillage()));
        profile.setMaritalStatus(req.maritalStatus());
        profile.setSpouseName(req.spouseName() != null ? req.spouseName().trim() : null);

        profile.getNextOfKin().clear();
        for (int i = 0; i < req.nextOfKin().size(); i++) {
            var k = req.nextOfKin().get(i);
            profile.getNextOfKin().add(NextOfKin.builder()
                    .memberProfile(profile)
                    .position((short) (i + 1))
                    .fullName(k.fullName().trim())
                    .phone(k.phone().trim())
                    .relationship(k.relationship().trim())
                    .build());
        }

        profile.getEmergencyContacts().clear();
        for (int i = 0; i < req.emergencyContacts().size(); i++) {
            var c = req.emergencyContacts().get(i);
            profile.getEmergencyContacts().add(EmergencyContact.builder()
                    .memberProfile(profile)
                    .position((short) (i + 1))
                    .fullName(c.fullName().trim())
                    .phone(c.phone().trim())
                    .relationship(c.relationship().trim())
                    .build());
        }

        profile.setOccupation(req.occupation() != null ? req.occupation().trim() : null);
        profile.setEmployer(req.employer() != null ? req.employer().trim() : null);
        profileRepository.save(profile);

        return ResponseEntity.ok(ApiResponse.ok("Profile updated.", FullMemberProfileDto.from(user, profile)));
    }

    @PatchMapping("/me/photo")
    @Operation(summary = "Update profile photo URL after Cloudinary upload",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<String>> updatePhoto(@RequestBody PhotoUpdateRequest req) {
        if (req.photoUrl() == null || req.photoUrl().isBlank()) {
            throw new BadRequestException("photoUrl is required.");
        }
        User user = currentUser();
        MemberProfile profile = profileRepository.findByUser(user).orElseThrow(
                () -> new BadRequestException("Profile not found."));
        profile.setPhotoUrl(req.photoUrl().trim());
        profileRepository.save(profile);
        return ResponseEntity.ok(ApiResponse.ok("Photo updated.", profile.getPhotoUrl()));
    }

    public record PhotoUpdateRequest(String photoUrl) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String trimOrNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));
    }
}
