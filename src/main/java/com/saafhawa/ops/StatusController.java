package com.saafhawa.ops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.saafhawa.aqi.CityDailyAqiRepository;
import com.saafhawa.ingest.IngestService;
import com.saafhawa.ingest.IngestionRun;
import com.saafhawa.ingest.IngestionRunRepository;
import com.saafhawa.measurement.MeasurementRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Public freshness/health page (FR-7.2). Doubles as a watchdog on CPCB uptime: last successful run
 * per source, stations reporting in the last 3h vs 24h, and the current bulletin date.
 */
@RestController
public class StatusController {

    private final IngestService ingestService;
    private final IngestionRunRepository runs;
    private final MeasurementRepository measurements;
    private final CityDailyAqiRepository cityAqi;

    public StatusController(IngestService ingestService, IngestionRunRepository runs,
                            MeasurementRepository measurements, CityDailyAqiRepository cityAqi) {
        this.ingestService = ingestService;
        this.runs = runs;
        this.measurements = measurements;
        this.cityAqi = cityAqi;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceStatus(String source, Instant lastSuccess, Instant lastAttempt,
                               String lastOutcome, Integer lastInserted) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Status(Instant generatedAt, List<SourceStatus> sources,
                         long stationsReporting3h, long stationsReporting24h,
                         LocalDate currentBulletinDate) {
    }

    @GetMapping("/v1/status")
    public ResponseEntity<Status> status() {
        Instant now = Instant.now();
        List<SourceStatus> sources = new ArrayList<>();
        for (String source : ingestService.sources()) {
            IngestionRun success = runs
                    .findFirstBySourceAndOutcomeOrderByStartedAtDesc(source, "SUCCESS").orElse(null);
            IngestionRun last = runs.findFirstBySourceOrderByStartedAtDesc(source).orElse(null);
            sources.add(new SourceStatus(source,
                    success == null ? null : success.getStartedAt(),
                    last == null ? null : last.getStartedAt(),
                    last == null ? null : last.getOutcome(),
                    last == null ? null : last.getInserted()));
        }
        Status status = new Status(now, sources,
                measurements.countStationsReportingSince(now.minus(Duration.ofHours(3))),
                measurements.countStationsReportingSince(now.minus(Duration.ofHours(24))),
                cityAqi.maxDate());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(status);
    }
}
