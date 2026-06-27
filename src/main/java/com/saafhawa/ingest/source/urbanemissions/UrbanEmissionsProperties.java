package com.saafhawa.ingest.source.urbanemissions;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * urbanemissions.info CPCB bulletin archive adapter config (§5.2, verification-log V6). The
 * upstream is a sporadically-updated historical CSV dump split across several inconsistently-named
 * files (some carry an extra index/year column), so the file set is explicit and configurable
 * rather than derived from a per-year naming convention.
 *
 * <ul>
 *   <li>{@code historyFiles} — large, rarely-changing back-catalogue (default
 *       {@code AllIndiaBulletins_Master_2024.csv}: 2015-05 → 2024-12). Fetched only for a true
 *       historical backfill (window starting before the current year).</li>
 *   <li>{@code currentFiles} — current-year file(s) the weekly refresh keeps topped up.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "saafhawa.ingest.urbanemissions")
public record UrbanEmissionsProperties(boolean enabled, String baseUrl,
                                       List<String> historyFiles, List<String> currentFiles,
                                       String cron) {

    public UrbanEmissionsProperties {
        if (baseUrl == null) {
            baseUrl = "https://raw.githubusercontent.com/urbanemissionsinfo/AQI_bulletins/main/data/Processed";
        }
        if (historyFiles == null || historyFiles.isEmpty()) {
            historyFiles = List.of("AllIndiaBulletins_Master_2024.csv");
        }
        if (currentFiles == null || currentFiles.isEmpty()) {
            currentFiles = List.of("AllIndiaBulletins_Master2025.csv", "AllIndiaBulletins_2025.csv");
        }
    }
}
