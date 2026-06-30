package com.schooldays.dto.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateProfileRequest(
        @Null(message = "Email cannot be changed")
        String email,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @NotBlank
        @Size(max = 50)
        String phone,

        ParentAddress address
) {
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ParentAddress(
            @NotBlank
            @Size(max = 200)
            String streetAddress,

            @Size(max = 100)
            String suite,

            @NotBlank
            @Size(max = 100)
            String city,

            @NotBlank
            @Size(max = 50)
            String state,

            @NotBlank
            @Size(max = 20)
            String zipCode
    ) {
    }
}
