package com.saafhawa.ingest.source.datagovin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** data.gov.in CPCB snapshot adapter config (§5.1). resourceId is [VERIFY] (verification-log V1). */
@ConfigurationProperties(prefix = "saafhawa.ingest.datagovin")
public record DataGovInProperties(
        boolean enabled,
        String baseUrl,
        String resourceId,
        String apiKey,
        int pageSize,
        int maxPages) {

    public DataGovInProperties {
        if (baseUrl == null) {
            baseUrl = "https://api.data.gov.in/resource";
        }
        if (resourceId == null) {
            resourceId = "3b01bcb8-0b14-4abf-b6f2-c1bfd384ba69";
        }
        if (pageSize <= 0) {
            pageSize = 1000;
        }
        if (maxPages <= 0) {
            maxPages = 20;
        }
    }
}
