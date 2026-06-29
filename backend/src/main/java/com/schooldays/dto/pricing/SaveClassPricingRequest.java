package com.schooldays.dto.pricing;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveClassPricingRequest(
        @NotBlank
        @Size(max = 50)
        String pricingType,

        @NotBlank
        @Size(min = 3, max = 3)
        String currency,

        @Valid
        List<ClassFeeItemRequest> feeItems
) {
}
