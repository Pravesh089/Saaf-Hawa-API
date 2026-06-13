package com.saafhawa.aqi;

import com.saafhawa.aqi.AqiCalculator.Band;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for the AQI module (§9.6) — no Spring, no DB. */
class AqiCalculatorTest {

    // CPCB breakpoints (subset) used across cases.
    private static final List<Band> PM25 = List.of(
            new Band(0, 50, 0, 30, 24), new Band(51, 100, 31, 60, 24),
            new Band(101, 200, 61, 90, 24), new Band(201, 300, 91, 120, 24),
            new Band(301, 400, 121, 250, 24), new Band(401, 500, 251, 100000, 24));
    private static final List<Band> PM10 = List.of(
            new Band(0, 50, 0, 50, 24), new Band(51, 100, 51, 100, 24),
            new Band(101, 200, 101, 250, 24), new Band(201, 300, 251, 350, 24),
            new Band(301, 400, 351, 430, 24), new Band(401, 500, 431, 100000, 24));
    private static final List<Band> O3 = List.of(
            new Band(0, 50, 0, 50, 8), new Band(51, 100, 51, 100, 8),
            new Band(101, 200, 101, 168, 8), new Band(201, 300, 169, 208, 8),
            new Band(301, 400, 209, 748, 8), new Band(401, 500, 749, 100000, 8));

    private final Map<String, List<Band>> bands = Map.of("PM2.5", PM25, "PM10", PM10, "O3", O3);

    @Test
    void subIndexInterpolatesWithinBand() {
        // PM2.5 = 182 falls in the 121–250 band mapping to index 301–400.
        assertThat(AqiCalculator.subIndex(PM25, 182)).contains(348);
        // PM2.5 = 30 sits at the top of the first band.
        assertThat(AqiCalculator.subIndex(PM25, 30)).contains(50);
    }

    @Test
    void subIndexClampsAboveTopBand() {
        assertThat(AqiCalculator.subIndex(PM25, 5000)).contains(500);
    }

    @Test
    void aqiIsMaxOfSubIndicesAndValidWithThreePollutantsIncludingPm() {
        AqiCalculator.Result r = AqiCalculator.compute(
                Map.of("PM2.5", 182.0, "PM10", 305.0, "O3", 25.0), bands);
        assertThat(r.aqi()).isEqualTo(348);
        assertThat(r.prominentPollutant()).isEqualTo("PM2.5");
        assertThat(r.category()).isEqualTo("Very Poor");
        assertThat(r.valid()).isTrue();
    }

    @Test
    void aqiInvalidWithoutThreePollutants() {
        AqiCalculator.Result r = AqiCalculator.compute(Map.of("PM2.5", 50.0, "PM10", 40.0), bands);
        assertThat(r.valid()).isFalse();
        assertThat(r.aqi()).isNotNull(); // still reported, just flagged invalid
    }

    @Test
    void categoriesMatchCpcbBands() {
        assertThat(AqiCalculator.category(25)).isEqualTo("Good");
        assertThat(AqiCalculator.category(75)).isEqualTo("Satisfactory");
        assertThat(AqiCalculator.category(150)).isEqualTo("Moderate");
        assertThat(AqiCalculator.category(250)).isEqualTo("Poor");
        assertThat(AqiCalculator.category(350)).isEqualTo("Very Poor");
        assertThat(AqiCalculator.category(450)).isEqualTo("Severe");
    }
}
