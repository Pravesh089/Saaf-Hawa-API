package com.saafhawa.measurement;

import java.time.Instant;

/** Most-recent value per (station, pollutant) for /v1/latest (FR-4.6). */
public record LatestRow(
        String stationId,
        String pollutant,
        Instant intervalStart,
        Double value,
        String source,
        int qcFlags) {
}
