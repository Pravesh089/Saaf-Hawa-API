package com.saafhawa.ingest.source.urbanemissions;

import com.saafhawa.ingest.spi.CityBulletinRow;
import com.saafhawa.ingest.spi.ParseResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays a sample urbanemissions bulletin payload through the parser (§5.2, verification-log V6).
 * The fixture concatenates the two real upstream schemas — the "openrefined" form (leading index
 * column + trailing {@code year}) followed by the clean form — to exercise the header re-sync.
 */
class UrbanEmissionsParserTest {

    private final UrbanEmissionsParser parser = new UrbanEmissionsParser();

    private byte[] fixture() throws Exception {
        return Files.readAllBytes(Path.of("src/test/resources/fixtures/urbanemissions-sample.csv"));
    }

    @Test
    void parsesBothSchemasInOneStream() throws Exception {
        ParseResult result = parser.parse(fixture());

        // 2 rows under the openrefined header + 3 under the clean header; both headers skipped.
        assertThat(result.fetched()).isEqualTo(5);
        assertThat(result.rejected()).isZero();
        assertThat(result.cityBulletins()).hasSize(5);
    }

    @Test
    void mapsColumnsByNameAcrossDifferingLayouts() throws Exception {
        ParseResult result = parser.parse(fixture());

        // Openrefined row: offset by the leading index column, trailing year column ignored.
        CityBulletinRow delhi = find(result, "Delhi", LocalDate.of(2024, 12, 30));
        assertThat(delhi.aqi()).isEqualTo(256);
        assertThat(delhi.prominentPollutant()).isEqualTo("PM2.5");

        // Clean row with a quoted multi-pollutant field.
        CityBulletinRow mumbai = find(result, "Mumbai", LocalDate.of(2025, 7, 1));
        assertThat(mumbai.aqi()).isEqualTo(53);
        assertThat(mumbai.prominentPollutant()).isEqualTo("PM10, PM2.5");
    }

    @Test
    void treatsNaAsNullAqiAndPollutant() throws Exception {
        ParseResult result = parser.parse(fixture());

        CityBulletinRow unknown = find(result, "Unknown City", LocalDate.of(2025, 7, 1));
        assertThat(unknown.aqi()).isNull();
        assertThat(unknown.prominentPollutant()).isNull();
    }

    private CityBulletinRow find(ParseResult result, String city, LocalDate date) {
        return result.cityBulletins().stream()
                .filter(c -> c.city().equals(city) && c.aqiDate().equals(date))
                .findFirst().orElseThrow();
    }
}
