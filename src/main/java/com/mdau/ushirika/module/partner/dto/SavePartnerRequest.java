package com.mdau.ushirika.module.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SavePartnerRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @Size(max = 500) String websiteUrl,
        Integer sortOrder
) {}
