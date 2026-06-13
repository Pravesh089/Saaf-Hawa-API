package com.saafhawa.ingest.spi;

import java.time.Instant;

/** The time window an adapter is asked to ingest. For snapshot sources, {@code to} is "now". */
public record IngestionWindow(Instant from, Instant to) {

    public static IngestionWindow lastHours(int hours) {
        Instant now = Instant.now();
        return new IngestionWindow(now.minusSeconds(hours * 3600L), now);
    }
}
