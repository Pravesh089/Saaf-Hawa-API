package com.saafhawa.aqi;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-conversion guard for AQI input (verification-log V9): CO arrives from the feed in µg/m³ but
 * CPCB breakpoints express CO in mg/m³, so it must be divided by 1000 before the breakpoints apply.
 */
class AqiServiceTest {

    @Test
    void convertsCoFromMicrogramsToMilligrams() {
        Map<String, Double> normalized = AqiService.normalizeUnits(Map.of("CO", 94.0));
        // 94 µg/m³ → 0.094 mg/m³, well within CPCB's "Good" CO band, not the Severe 94 mg/m³.
        assertThat(normalized.get("CO")).isEqualTo(0.094);
    }

    @Test
    void leavesMicrogramPollutantsUnchanged() {
        Map<String, Double> in = Map.of("PM2.5", 76.0, "PM10", 132.0, "NO2", 42.0, "O3", 13.0);
        Map<String, Double> out = AqiService.normalizeUnits(in);
        assertThat(out).containsExactlyInAnyOrderEntriesOf(in);
    }

    @Test
    void preservesNullValues() {
        Map<String, Double> in = new HashMap<>();
        in.put("CO", null);
        assertThat(AqiService.normalizeUnits(in).get("CO")).isNull();
    }
}
