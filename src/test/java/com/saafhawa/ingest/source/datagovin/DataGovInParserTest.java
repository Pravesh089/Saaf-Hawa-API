package com.saafhawa.ingest.source.datagovin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saafhawa.catalog.PollutantNormalizer;
import com.saafhawa.ingest.spi.CanonicalMeasurement;
import com.saafhawa.ingest.spi.ParseResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Replays an archived-style fixture payload through the parser (NFR-5, §5.1 quirks). */
class DataGovInParserTest {

    private final DataGovInParser parser = new DataGovInParser(new ObjectMapper(), new PollutantNormalizer());

    private byte[] fixture() throws Exception {
        return Files.readAllBytes(Path.of("src/test/resources/fixtures/datagovin-sample.json"));
    }

    @Test
    void parsesValidRecordsAndCountsRejects() throws Exception {
        ParseResult result = parser.parse(fixture());

        assertThat(result.fetched()).isEqualTo(9);
        // XYZ (unknown pollutant) + CO (all-NA values) are rejected.
        assertThat(result.rejected()).isEqualTo(2);
        // 7 parseable rows minus 1 in-payload duplicate (Mumbai PM2.5) = 6.
        assertThat(result.measurements()).hasSize(6);
    }

    @Test
    void mapsOzoneToO3AndHandlesAltFieldNames() throws Exception {
        ParseResult result = parser.parse(fixture());

        assertThat(result.measurements()).anyMatch(m -> m.pollutant().equals("O3"));
        // Mumbai row uses avg_value/min_value/max_value instead of pollutant_*.
        CanonicalMeasurement mumbai = result.measurements().stream()
                .filter(m -> m.station().city().equals("Mumbai"))
                .findFirst().orElseThrow();
        assertThat(mumbai.value()).isEqualTo(66.0);
        assertThat(mumbai.pollutant()).isEqualTo("PM2.5");
    }

    @Test
    void preservesRawSuspectValuesForLaterQc() throws Exception {
        ParseResult result = parser.parse(fixture());
        // Negative and sentinel values are kept verbatim; QC flags them downstream, never drops them.
        assertThat(result.measurements()).anyMatch(m -> m.pollutant().equals("NO2") && m.value() == -5.0);
        assertThat(result.measurements()).anyMatch(m -> m.pollutant().equals("SO2") && m.value() == 999.0);
    }
}
