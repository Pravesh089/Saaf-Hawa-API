package com.saafhawa.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cross-cutting app config (12-factor, NFR-6). */
@ConfigurationProperties(prefix = "saafhawa")
public record AppProperties(
        String contactUrl,        // identifies us politely to upstreams (NFR-8)
        String adminToken,        // static admin bearer token (FR-7.1)
        Archive archive,
        RateLimit rateLimit,
        Alerting alerting) {

    public AppProperties {
        if (archive == null) {
            archive = new Archive("filesystem", "./data/archive", null, null, null, "saafhawa-raw");
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(30, 60);
        }
        if (alerting == null) {
            alerting = new Alerting(3, null);
        }
    }

    /** Raw-payload archive (G5). type=filesystem|s3. */
    public record Archive(String type, String basePath, String endpoint,
                          String accessKey, String secretKey, String bucket) {
    }

    /** Default rate limits (FR-6.1). */
    public record RateLimit(int anonymousPerHour, int keyedPerMinute) {
    }

    /** Ops alerting (FR-1.2). consecutiveFailureThreshold triggers an alert; webhookUrl optional. */
    public record Alerting(int consecutiveFailureThreshold, String webhookUrl) {
    }
}
