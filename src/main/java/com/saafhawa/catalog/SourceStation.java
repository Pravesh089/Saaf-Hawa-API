package com.saafhawa.catalog;

/** A station as seen by an upstream source, before identity resolution. */
public record SourceStation(
        String source,
        String sourceKey,
        String name,
        String city,
        String state,
        Double lat,
        Double lon,
        String agency,
        String stationType) {
}
