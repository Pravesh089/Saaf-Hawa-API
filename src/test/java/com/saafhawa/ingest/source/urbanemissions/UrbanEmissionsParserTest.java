package com.saafhawa.ingest.source.urbanemissions;

import com.saafhawa.ingest.spi.CityBulletinRow;
import com.saafhawa.ingest.spi.ParseResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Replays a sample urbanemissions bulletin CSV through the parser (§5.2, verification-log V6). */
class UrbanEmissionsParserTest {

    private final UrbanEmissionsParser parser = new UrbanEmissionsParser();

    private byte[] fixture() throws Exception {
        return Files.readAllBytes(Path.of("src/test/resources/fixtures/urbanemissions-sample.csv"));
    }

    @Test
    void parsesRowsAndHandlesMissingValues() throws Exception {
        ParseResult result = parser.parse(fixture());

        assertThat(result.fetched()).isEqualTo(5);
        assertThat(result.rejected()).isZero();
        assertThat(result.cityBulletins()).hasSize(5);
    }

    @Test
    void handlesQuotedMultiPollutantField() throws Exception {
        ParseResult result = parser.parse(fixture());

        CityBulletinRow mumbai = result.cityBulletins().stream()
                .filter(c -> c.city().equals("Mumbai") && c.aqiDate().equals(LocalDate.of(2025, 7, 1)))
                .findFirst().orElseThrow();
        assertThat(mumbai.aqi()).isEqualTo(53);
        assertThat(mumbai.prominentPollutant()).isEqualTo("PM10, PM2.5");
    }

    @Test
    void treatsNaAsNullAqiAndPollutant() throws Exception {
        ParseResult result = parser.parse(fixture());

        CityBulletinRow unknown = result.cityBulletins().stream()
                .filter(c -> c.city().equals("Unknown City"))
                .findFirst().orElseThrow();
        assertThat(unknown.aqi()).isNull();
        assertThat(unknown.prominentPollutant()).isNull();
    }
}
