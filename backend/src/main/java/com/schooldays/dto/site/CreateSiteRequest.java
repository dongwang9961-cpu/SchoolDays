package com.schooldays.dto.site;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSiteRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 100)
        String timezone,

        @Pattern(regexp = "active|inactive")
        String status,

        @Size(max = 255)
        String streetAddress,

        @Size(max = 100)
        String suite,

        @Size(max = 100)
        String city,

        @Size(max = 80)
        String state,

        @Size(max = 20)
        String zipCode,

        @Size(max = 255)
        String googlePlaceId,

        @Size(max = 500)
        String formattedAddress,

        @Size(max = 50)
        String latitude,

        @Size(max = 50)
        String longitude,

        @Size(max = 255)
        String ownerFullName,

        @Size(max = 50)
        String ownerPhone,

        @Size(max = 320)
        String ownerEmail,

        @Size(max = 255)
        String gradeLevelsServed
) {
}
