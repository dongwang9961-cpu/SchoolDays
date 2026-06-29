package com.schooldays.dto.site;

import java.util.List;

public record SiteListResponse(
        List<SiteResponse> sites,
        SiteQuotaResponse quota
) {
}
