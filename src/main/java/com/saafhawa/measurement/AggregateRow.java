package com.saafhawa.measurement;

import java.time.Instant;

/** An aggregated measurement row (interval=day|month) from a continuous aggregate. */
public record AggregateRow(
        String stationId,
        String pollutant,
        Instant bucketStart,
        Double mean,
        Double min,
        Double max,
        long count,
        long unflaggedCount) {
}
