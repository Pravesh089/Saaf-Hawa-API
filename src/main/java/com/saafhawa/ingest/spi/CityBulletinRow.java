package com.saafhawa.ingest.spi;

import java.time.LocalDate;

/** One city-day AQI bulletin row, parsed in canonical form (§5.2). Distinct from station measurements. */
public record CityBulletinRow(
        String city,
        String state,
        LocalDate aqiDate,
        Integer aqi,
        String prominentPollutant) {
}
