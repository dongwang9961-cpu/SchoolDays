package com.schooldays.dto.pricing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClassFeeItemRequest(
        @NotBlank
        @Size(max = 50)
        String category,

        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(min = 3, max = 3)
        String currency,

        @NotNull
        @Min(0)
        Integer fee,

        @Size(max = 1000)
        String note
) {
}
