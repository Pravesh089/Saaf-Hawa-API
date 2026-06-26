package com.saafhawa.ingest.source.urbanemissions;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * urbanemissions.info CPCB bulletin archive adapter config (§5.2, verification-log V6). The
 * upstream is a sporadically-updated historical CSV dump keyed by year, not a live feed.
 */
@ConfigurationProperties(prefix = "saafhawa.ingest.urbanemissions")
public record UrbanEmissionsProperties(boolean enabled, String baseUrl, int startYear, String cron) {

    public UrbanEmissionsProperties {
        if (baseUrl == null) {
            baseUrl = "https://raw.githubusercontent.com/urbanemissionsinfo/AQI_bulletins/main/data/Processed";
        }
        if (startYear <= 0) {
            startYear = 2015;
        }
    }
}
