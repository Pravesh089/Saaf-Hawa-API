package com.saafhawa.measurement;

import java.time.Instant;

/** A raw measurement row as returned to the API (interval=raw). */
public record MeasurementRow(
        String stationId,
        String pollutant,
        Instant intervalStart,
        int intervalSeconds,
        Double value,
        Double valueMin,
        Double valueMax,
        String source,
        int qcFlags,
        String qcRuleset,
        Integer reportedAqi) {
}
