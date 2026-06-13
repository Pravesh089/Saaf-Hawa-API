package com.saafhawa.ingest.source.datagovin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saafhawa.catalog.PollutantNormalizer;
import com.saafhawa.catalog.SourceStation;
import com.saafhawa.common.TimeUtil;
import com.saafhawa.ingest.spi.CanonicalMeasurement;
import com.saafhawa.ingest.spi.ParseResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Parses a data.gov.in CPCB snapshot payload into canonical measurements (§5.1). Defensive about
 * field-name variants and "NA" values (verification-log V2); dedupes within the payload (§4.3).
 */
@Component
public class DataGovInParser {

    static final String SOURCE_ID = "cpcb-datagovin";
    private static final int HOURLY = 3600;

    private final ObjectMapper mapper;
    private final PollutantNormalizer normalizer;

    public DataGovInParser(ObjectMapper mapper, PollutantNormalizer normalizer) {
        this.mapper = mapper;
        this.normalizer = normalizer;
    }

    public ParseResult parse(byte[] bytes) throws Exception {
        ParseResult result = new ParseResult();
        JsonNode root = mapper.readTree(bytes);
        JsonNode records = root.path("records");
        if (!records.isArray()) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode rec : records) {
            result.countFetched();
            try {
                CanonicalMeasurement m = parseRecord(rec);
                if (m == null) {
                    result.reject(rec.toString());
                    continue;
                }
                String key = m.station().sourceKey() + "|" + m.pollutant() + "|" + m.intervalStart();
                if (seen.add(key)) {
                    result.add(m);
                }
            } catch (RuntimeException e) {
                result.reject(rec.toString());
            }
        }
        return result;
    }

    private CanonicalMeasurement parseRecord(JsonNode rec) {
        String pollutantRaw = text(rec, "pollutant_id", "pollutant");
        String pollutant = normalizer.normalize(pollutantRaw);
        if (pollutant == null) {
            return null;
        }
        Instant intervalStart = TimeUtil.parseDataGovIn(text(rec, "last_update"));
        if (intervalStart == null) {
            return null;
        }
        Double avg = num(rec, "pollutant_avg", "avg_value", "pollutant_average");
        Double min = num(rec, "pollutant_min", "min_value");
        Double max = num(rec, "pollutant_max", "max_value");
        if (avg == null && min == null && max == null) {
            return null;
        }

        String station = text(rec, "station");
        String city = text(rec, "city");
        String state = text(rec, "state");
        Double lat = num(rec, "latitude");
        Double lon = num(rec, "longitude");
        if (station == null) {
            return null;
        }
        String sourceKey = station + "|" + (city == null ? "" : city) + "|" + (state == null ? "" : state);

        SourceStation src = new SourceStation(SOURCE_ID, sourceKey, station, city, state,
                lat, lon, null, "UNKNOWN");
        return new CanonicalMeasurement(src, pollutant, intervalStart, HOURLY, avg, min, max, null);
    }

    /** First non-null text field among the given names; "NA"/blank → null. */
    private static String text(JsonNode rec, String... names) {
        for (String n : names) {
            JsonNode v = rec.get(n);
            if (v != null && !v.isNull()) {
                String s = v.asText().trim();
                if (!s.isEmpty() && !s.equalsIgnoreCase("NA") && !s.equalsIgnoreCase("None")) {
                    return s;
                }
            }
        }
        return null;
    }

    private static Double num(JsonNode rec, String... names) {
        String s = text(rec, names);
        if (s == null) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
