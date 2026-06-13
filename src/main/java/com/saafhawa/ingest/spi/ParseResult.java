package com.saafhawa.ingest.spi;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of parsing a raw payload. Rejects (unparseable rows) are counted and sampled, never
 * silently swallowed (FR-2.3).
 */
public class ParseResult {

    /** Cap on retained reject samples to bound memory/storage. */
    private static final int MAX_REJECT_SAMPLES = 50;

    private final List<CanonicalMeasurement> measurements = new ArrayList<>();
    private final List<String> rejectSamples = new ArrayList<>();
    private int fetched;
    private int rejected;

    public void add(CanonicalMeasurement m) {
        measurements.add(m);
    }

    public void countFetched() {
        fetched++;
    }

    public void reject(String sample) {
        rejected++;
        if (rejectSamples.size() < MAX_REJECT_SAMPLES) {
            rejectSamples.add(sample);
        }
    }

    public List<CanonicalMeasurement> measurements() {
        return measurements;
    }

    public List<String> rejectSamples() {
        return rejectSamples;
    }

    public int fetched() {
        return fetched;
    }

    public int rejected() {
        return rejected;
    }
}
