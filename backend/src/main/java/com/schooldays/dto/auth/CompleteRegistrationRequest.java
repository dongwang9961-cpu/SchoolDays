package com.schooldays.dto.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteRegistrationRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 50) String phone,
        @Valid ParentAddress address
) {
    public record ParentAddress(
            @NotBlank @Size(max = 200) String streetAddress,
            @Size(max = 100) String suite,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 50) String state,
            @NotBlank @Size(max = 20) String zipCode
    ) {
    }
}
