package com.saafhawa.measurement;

import com.saafhawa.common.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** Opaque keyset cursor for raw measurement pagination (ambiguities.md #6). */
public record MeasurementCursor(Instant intervalStart, String stationId, String pollutant) {

    public String encode() {
        String raw = intervalStart.toString() + "|" + stationId + "|" + pollutant;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static MeasurementCursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 3);
            return new MeasurementCursor(Instant.parse(parts[0]), parts[1], parts[2]);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Invalid cursor token");
        }
    }
}
