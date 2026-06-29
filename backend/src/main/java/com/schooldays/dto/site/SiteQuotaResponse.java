package com.schooldays.dto.site;

public record SiteQuotaResponse(
        boolean unlimitedSites,
        Integer maxSites,
        int currentSiteCount,
        Integer remainingSites
) {
}
