package com.saafhawa.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.saafhawa.aqi.AqiCalculator;
import com.saafhawa.aqi.AqiService;
import com.saafhawa.aqi.CityDailyAqi;
import com.saafhawa.aqi.CityDailyAqiRepository;
import com.saafhawa.api.dto.ApiResponse;
import com.saafhawa.api.dto.Meta;
import com.saafhawa.catalog.CatalogService;
import com.saafhawa.common.ApiException;
import com.saafhawa.common.TimeUtil;
import com.saafhawa.measurement.LatestRow;
import com.saafhawa.measurement.MeasurementRepository;
import com.saafhawa.qc.QcFlag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AQI endpoints (FR-4.5).
 *
 * <ul>
 *   <li>{@code /v1/aqi/cities} — bulletin-sourced city daily AQI history (M2 data layer).</li>
 *   <li>{@code /v1/aqi/stations} — real CPCB AQI computed server-side from each station's latest
 *       readings, using the seeded CPCB sub-index breakpoints across all pollutants (§4.2). This
 *       replaces any client-side PM-only approximation: it is the official max-sub-index method.</li>
 * </ul>
 */
@RestController
public class AqiController {

    /**
     * Readings carrying any of these QC flags are untrustworthy and excluded from the AQI
     * computation (impossible/placeholder/out-of-range/stuck/spike values). DUPLICATE_SOURCE is a
     * provenance marker, not a value problem, so it does not exclude a reading.
     */
    private static final int REJECT_MASK = QcFlag.toMask(EnumSet.of(
            QcFlag.NEGATIVE, QcFlag.ZERO_SUSPECT, QcFlag.SENTINEL,
            QcFlag.STUCK, QcFlag.SPIKE, QcFlag.RANGE));

    private final CityDailyAqiRepository cityAqi;
    private final AqiService aqiService;
    private final MeasurementRepository measurements;
    private final CatalogService catalog;

    public AqiController(CityDailyAqiRepository cityAqi, AqiService aqiService,
                         MeasurementRepository measurements, CatalogService catalog) {
        this.cityAqi = cityAqi;
        this.aqiService = aqiService;
        this.measurements = measurements;
        this.catalog = catalog;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CityAqiDto(String city, String date, Integer aqiReported, Integer aqiComputed,
                             String prominentPollutant, String source) {
    }

    /** Per-station computed AQI ({@code aqi} present when ≥1 sub-index; {@code valid} per §4.2). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StationAqiDto(String stationId, Integer aqi, String category,
                                String prominentPollutant, boolean valid,
                                Map<String, Integer> subIndices, String measuredAt, Long ageMinutes) {
    }

    @GetMapping("/v1/aqi/cities")
    public ResponseEntity<ApiResponse<String>> cities() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(ApiResponse.of(Meta.of(List.of("urbanemissions", "cpcb-bulletin"), null, null),
                        cityAqi.distinctCities()));
    }

    @GetMapping("/v1/aqi/cities/{city}")
    public ResponseEntity<ApiResponse<CityAqiDto>> citySeries(
            @PathVariable String city,
            @RequestParam String from,
            @RequestParam String to) {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        List<CityAqiDto> series = cityAqi
                .findByCityIgnoreCaseAndAqiDateBetweenOrderByAqiDateAsc(city, fromDate, toDate)
                .stream()
                .map(this::toDto)
                .toList();
        Meta meta = new Meta(List.of("urbanemissions", "cpcb-bulletin"), null, java.time.Instant.now(),
                "AQI bulletins via UrbanEmissions.info; Saaf Hawa", null,
                "aqiComputed (station-derived) is populated from M3; bulletin AQI shown as aqiReported");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(ApiResponse.of(meta, series));
    }

    /**
     * Real CPCB AQI per station, computed from the latest reading of each pollutant. Resolve
     * stations by {@code station} (comma-separated ids) or {@code city}. Results are sorted most
     * polluted first. Note the AQI uses latest readings, not 24-hour averages (see meta note).
     */
    @GetMapping("/v1/aqi/stations")
    public ResponseEntity<ApiResponse<StationAqiDto>> stationAqi(
            @RequestParam(required = false) String station,
            @RequestParam(required = false) String city) {
        List<String> stationIds = resolveStations(station, city);
        Instant now = Instant.now();

        Map<String, List<LatestRow>> byStation = new LinkedHashMap<>();
        for (LatestRow r : measurements.latestForStations(stationIds)) {
            byStation.computeIfAbsent(r.stationId(), k -> new ArrayList<>()).add(r);
        }

        List<StationAqiDto> out = new ArrayList<>(byStation.size());
        for (var e : byStation.entrySet()) {
            out.add(toStationAqi(e.getKey(), e.getValue(), now));
        }
        // Most polluted first; stations with no computable AQI sink to the bottom.
        out.sort(Comparator.comparing(StationAqiDto::aqi,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Meta meta = new Meta(List.of("cpcb-datagovin"), null, now,
                "CPCB via data.gov.in; Saaf Hawa", null,
                "AQI computed from each station's latest readings using CPCB sub-index breakpoints "
                        + "(all pollutants, max sub-index). Not 24-hour averaged. 'valid' is true only "
                        + "with ≥3 sub-indices including PM2.5/PM10; QC-flagged values are excluded.");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(ApiResponse.of(meta, out));
    }

    private StationAqiDto toStationAqi(String stationId, List<LatestRow> rows, Instant now) {
        Map<String, Double> concentrations = new HashMap<>();
        Instant latest = null;
        for (LatestRow r : rows) {
            if (r.value() == null || (r.qcFlags() & REJECT_MASK) != 0) {
                continue;
            }
            concentrations.put(r.pollutant(), r.value());
            if (latest == null || r.intervalStart().isAfter(latest)) {
                latest = r.intervalStart();
            }
        }
        AqiCalculator.Result res = aqiService.compute(concentrations);
        Long age = latest == null ? null : Duration.between(latest, now).toMinutes();
        String measuredAt = latest == null ? null : TimeUtil.toIstIso(latest);
        Map<String, Integer> subIndices = res.subIndices().isEmpty() ? null : res.subIndices();
        return new StationAqiDto(stationId, res.aqi(), res.category(), res.prominentPollutant(),
                res.valid(), subIndices, measuredAt, age);
    }

    private List<String> resolveStations(String station, String city) {
        if (station != null && !station.isBlank()) {
            return Arrays.stream(station.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        if (city != null && !city.isBlank()) {
            return catalog.search(null, city).stream().map(s -> s.getId()).toList();
        }
        throw ApiException.badRequest("Provide 'station' or 'city'");
    }

    private CityAqiDto toDto(CityDailyAqi c) {
        return new CityAqiDto(c.getCity(), c.getAqiDate().toString(), c.getAqi(), null,
                c.getProminentPollutant(), c.getSource());
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Invalid date (expected yyyy-MM-dd): " + value);
        }
    }
}
