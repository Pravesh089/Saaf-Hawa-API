package com.saafhawa.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.saafhawa.aqi.CityDailyAqi;
import com.saafhawa.aqi.CityDailyAqiRepository;
import com.saafhawa.api.dto.ApiResponse;
import com.saafhawa.api.dto.Meta;
import com.saafhawa.common.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * City daily AQI endpoints (FR-4.5). Serves bulletin-sourced history; station-derived computed
 * AQI requires 24h rollups and is returned as null with a note until M3 (ambiguities.md #8).
 */
@RestController
public class AqiController {

    private final CityDailyAqiRepository cityAqi;

    public AqiController(CityDailyAqiRepository cityAqi) {
        this.cityAqi = cityAqi;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CityAqiDto(String city, String date, Integer aqiReported, Integer aqiComputed,
                             String prominentPollutant, String source) {
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
