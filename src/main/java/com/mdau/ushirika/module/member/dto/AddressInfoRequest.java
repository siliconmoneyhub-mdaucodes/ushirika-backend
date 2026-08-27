package com.mdau.ushirika.module.member.dto;

import com.mdau.ushirika.module.member.enums.Country;
import com.mdau.ushirika.module.member.enums.UsState;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddressInfoRequest(
        @NotBlank @Size(max = 300) String street,
        @NotBlank @Size(max = 150) String city,
        @NotBlank @Size(max = 20)  String zipCode,
        @NotNull(message = "State is required") UsState usState,
        @NotNull Country country,
        @Size(max = 100) String kenyaCounty,
        @Size(max = 100) String kenyaSubCounty,
        @Size(max = 100) String kenyaLocation,
        @Size(max = 100) String kenyaVillage,
        @Size(max = 100) String ugandaProvince,
        @Size(max = 100) String ugandaCounty,
        @Size(max = 100) String ugandaLocation,
        @Size(max = 100) String ugandaVillage
) {
    @AssertTrue(message = "County, sub-county, location/village, and subdivision are required for a Kenyan address")
    public boolean isKenyaRegionComplete() {
        if (country != Country.KENYA) return true;
        return notBlank(kenyaCounty) && notBlank(kenyaSubCounty) && notBlank(kenyaLocation) && notBlank(kenyaVillage);
    }

    @AssertTrue(message = "Province, county, location/village, and subdivision are required for a Ugandan address")
    public boolean isUgandaRegionComplete() {
        if (country != Country.UGANDA) return true;
        return notBlank(ugandaProvince) && notBlank(ugandaCounty) && notBlank(ugandaLocation) && notBlank(ugandaVillage);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
