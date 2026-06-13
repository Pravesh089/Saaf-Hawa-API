package com.saafhawa.ingest.spi;

import com.saafhawa.catalog.SourceStation;

import java.time.Instant;

/**
 * A single parsed measurement in canonical form, before identity resolution and QC. The pollutant
 * code is already normalized to our vocabulary (FR-2.2).
 */
public record CanonicalMeasurement(
        SourceStation station,
        String pollutant,
        Instant intervalStart,
        int intervalSeconds,
        Double value,
        Double valueMin,
        Double valueMax,
        Integer reportedAqi) {
}
