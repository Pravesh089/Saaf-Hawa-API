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
import com.saafhawa.measurement.MeasurementRepository.WindowedAvgRow;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AQI endpoints (FR-4.5).
 *
 * <ul>
 *   <li>{@code /v1/aqi/cities} — bulletin-sourced city daily AQI history (M2 data layer).</li>
 *   <li>{@code /v1/aqi/stations} — real CPCB AQI computed server-side using the seeded sub-index
 *       breakpoints across all pollutants (§4.2). Default {@code basis=avg} uses the CPCB trailing
 *       24-hour mean (8-hour for CO/O3) with a completeness rule; {@code basis=latest} is a quick
 *       single-reading snapshot. The official max-sub-index method, replacing any PM-only approx.</li>
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

    /** CPCB minimum data completeness: ≥16 of 24 hours (≥6 of 8 for the 8-hour pollutants). */
    private static final int MIN_HOURS_24 = 16;
    private static final int MIN_HOURS_8 = 6;

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
                                Map<String, Integer> subIndices, String measuredAt, Long ageMinutes,
                                String basis) {
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
     * Real CPCB AQI per station. The default {@code basis=avg} uses the CPCB method: a trailing
     * 24-hour mean per pollutant (8-hour for CO and O3) with a minimum-completeness rule.
     * {@code basis=latest} instead uses each pollutant's most recent single reading — a quick
     * snapshot that is always available but not time-averaged. Resolve stations by {@code station}
     * (comma-separated ids) or {@code city}; results are sorted most polluted first.
     */
    @GetMapping("/v1/aqi/stations")
    public ResponseEntity<ApiResponse<StationAqiDto>> stationAqi(
            @RequestParam(required = false) String station,
            @RequestParam(required = false) String city,
            @RequestParam(required = false, defaultValue = "avg") String basis) {
        List<String> stationIds = resolveStations(station, city);
        Instant now = Instant.now();
        boolean latest = "latest".equalsIgnoreCase(basis);

        List<StationAqiDto> out = latest ? latestStations(stationIds, now)
                : averagedStations(stationIds, now);
        // Most polluted first; stations with no computable AQI sink to the bottom.
        out.sort(Comparator.comparing(StationAqiDto::aqi,
                Comparator.nullsLast(Comparator.reverseOrder())));

        String note = latest
                ? "AQI from each station's latest single reading using CPCB sub-index breakpoints "
                        + "(all pollutants, max sub-index). A quick snapshot, not time-averaged; the "
                        + "default basis=avg gives CPCB-correct values."
                : "AQI by the CPCB method: trailing 24-hour mean per pollutant (8-hour for CO/O3), "
                        + "max sub-index across pollutants. Needs ≥" + MIN_HOURS_24 + "h (≥" + MIN_HOURS_8
                        + "h for 8-hour pollutants); 'valid' needs ≥3 sub-indices including PM2.5/PM10. "
                        + "Stations short on history fall back to latest readings (basis=latest-fallback). "
                        + "QC-flagged values excluded.";
        Meta meta = new Meta(List.of("cpcb-datagovin"), null, now,
                "CPCB via data.gov.in; Saaf Hawa", null, note);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(latest ? 1 : 10)).cachePublic())
                .body(ApiResponse.of(meta, out));
    }

    /** Latest-reading basis: one most-recent value per pollutant. */
    private List<StationAqiDto> latestStations(List<String> stationIds, Instant now) {
        Map<String, Instant> seen = new HashMap<>();
        Map<String, Map<String, Double>> conc = latestConcByStation(stationIds, seen);
        List<StationAqiDto> out = new ArrayList<>(conc.size());
        for (var e : conc.entrySet()) {
            out.add(toResult(e.getKey(), aqiService.compute(e.getValue()),
                    seen.get(e.getKey()), now, "latest"));
        }
        return out;
    }

    /**
     * CPCB-method basis: trailing 24h mean per pollutant (8h for CO/O3) with completeness gating.
     * A station that doesn't yet have enough hours for a valid averaged AQI falls back to its
     * latest single readings (basis {@code latest-fallback}), so a real number is still served
     * while history accumulates.
     */
    private List<StationAqiDto> averagedStations(List<String> stationIds, Instant now) {
        Map<String, Integer> avgHours = avgHoursByPollutant();
        Map<String, List<WindowedAvgRow>> avgByStation = new LinkedHashMap<>();
        for (WindowedAvgRow r : measurements.trailingAverages(stationIds, now, REJECT_MASK)) {
            avgByStation.computeIfAbsent(r.stationId(), k -> new ArrayList<>()).add(r);
        }
        Map<String, Instant> latestSeen = new HashMap<>();
        Map<String, Map<String, Double>> latestConc = latestConcByStation(stationIds, latestSeen);

        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(avgByStation.keySet());
        ids.addAll(latestConc.keySet());

        List<StationAqiDto> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            Map<String, Double> conc = new HashMap<>();
            Instant seen = null;
            for (WindowedAvgRow r : avgByStation.getOrDefault(id, List.of())) {
                boolean shortWindow = avgHours.getOrDefault(r.pollutant(), 24) <= 8;
                Double mean = shortWindow
                        ? (r.cnt8() >= MIN_HOURS_8 ? r.avg8() : null)
                        : (r.cnt24() >= MIN_HOURS_24 ? r.avg24() : null);
                if (mean != null) {
                    conc.put(r.pollutant(), mean);
                    if (seen == null || r.lastSeen().isAfter(seen)) {
                        seen = r.lastSeen();
                    }
                }
            }
            AqiCalculator.Result res = aqiService.compute(conc);
            if (res.valid()) {
                out.add(toResult(id, res, seen, now, "avg"));
            } else {
                Map<String, Double> lc = latestConc.getOrDefault(id, Map.of());
                out.add(toResult(id, aqiService.compute(lc), latestSeen.get(id), now, "latest-fallback"));
            }
        }
        return out;
    }

    /** Latest valid reading per pollutant for each station; records the most-recent timestamp. */
    private Map<String, Map<String, Double>> latestConcByStation(List<String> stationIds,
                                                                 Map<String, Instant> seenOut) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        for (LatestRow r : measurements.latestForStations(stationIds)) {
            if (r.value() == null || (r.qcFlags() & REJECT_MASK) != 0) {
                continue;
            }
            out.computeIfAbsent(r.stationId(), k -> new HashMap<>()).put(r.pollutant(), r.value());
            seenOut.merge(r.stationId(), r.intervalStart(), (a, b) -> b.isAfter(a) ? b : a);
        }
        return out;
    }

    /** Builds the DTO from an already-computed AQI result. */
    private StationAqiDto toResult(String stationId, AqiCalculator.Result res, Instant seen,
                                   Instant now, String basis) {
        Long age = seen == null ? null : Duration.between(seen, now).toMinutes();
        String measuredAt = seen == null ? null : TimeUtil.toIstIso(seen);
        Map<String, Integer> sub = res.subIndices().isEmpty() ? null : res.subIndices();
        return new StationAqiDto(stationId, res.aqi(), res.category(), res.prominentPollutant(),
                res.valid(), sub, measuredAt, age, basis);
    }

    /** Per-pollutant CPCB averaging window (hours), read from the seeded breakpoint bands. */
    private Map<String, Integer> avgHoursByPollutant() {
        Map<String, Integer> out = new HashMap<>();
        for (var e : aqiService.bandsByPollutant().entrySet()) {
            if (!e.getValue().isEmpty()) {
                out.put(e.getKey(), e.getValue().get(0).avgHours());
            }
        }
        return out;
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
