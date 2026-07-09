package com.schooldays.dto.externalcheckin;

import java.util.List;

public record ExternalCheckInListResponse(
        List<ExternalCheckInRowResponse> checkIns
) {
}
