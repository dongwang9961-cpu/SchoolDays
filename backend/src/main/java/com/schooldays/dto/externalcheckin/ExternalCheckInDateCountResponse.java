package com.schooldays.dto.externalcheckin;

import java.time.LocalDate;

public record ExternalCheckInDateCountResponse(
        LocalDate checkDate,
        Long count
) {
}
