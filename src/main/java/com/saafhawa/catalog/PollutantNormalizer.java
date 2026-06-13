package com.saafhawa.catalog;

import org.springframework.stereotype.Component;

import java.util.Map;

/** Normalizes upstream pollutant codes to our controlled vocabulary (FR-2.2). */
@Component
public class PollutantNormalizer {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("OZONE", "O3"),
            Map.entry("O3", "O3"),
            Map.entry("PM2.5", "PM2.5"),
            Map.entry("PM25", "PM2.5"),
            Map.entry("PM10", "PM10"),
            Map.entry("NO2", "NO2"),
            Map.entry("SO2", "SO2"),
            Map.entry("CO", "CO"),
            Map.entry("NH3", "NH3"),
            Map.entry("PB", "Pb"),
            Map.entry("LEAD", "Pb"));

    /** @return canonical pollutant code, or null if not in our vocabulary. */
    public String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return ALIASES.get(raw.trim().toUpperCase());
    }
}
