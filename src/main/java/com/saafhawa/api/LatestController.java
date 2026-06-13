package com.saafhawa.api;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** GET /v1/latest (FR-4.6): most recent values per pollutant with age-in-minutes. */
@RestController
public class LatestController {

    private final MeasurementRepository measurements;
    private final CatalogService catalog;

    public LatestController(MeasurementRepository measurements, CatalogService catalog) {
        this.measurements = measurements;
        this.catalog = catalog;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LatestDto(String stationId, String pollutant, Double value, String measuredAt,
                            long ageMinutes, List<String> flags, String source) {
    }

    @GetMapping("/v1/latest")
    public ResponseEntity<ApiResponse<LatestDto>> latest(
            @RequestParam(required = false) String station,
            @RequestParam(required = false) String city) {
        List<String> stationIds;
        if (station != null && !station.isBlank()) {
            stationIds = Arrays.stream(station.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        } else if (city != null && !city.isBlank()) {
            stationIds = catalog.search(null, city).stream().map(s -> s.getId()).toList();
        } else {
            throw ApiException.badRequest("Provide 'station' or 'city'");
        }

        Instant now = Instant.now();
        List<LatestDto> dtos = measurements.latestForStations(stationIds).stream()
                .map(r -> toDto(r, now))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(ApiResponse.of(Meta.of(List.of("cpcb-datagovin"), null, null), dtos));
    }

    private LatestDto toDto(LatestRow r, Instant now) {
        long age = Duration.between(r.intervalStart(), now).toMinutes();
        List<String> flags = QcFlag.fromMask(r.qcFlags()).stream().map(Enum::name).toList();
        return new LatestDto(r.stationId(), r.pollutant(), r.value(),
                TimeUtil.toIstIso(r.intervalStart()), age, flags, r.source());
    }
}
