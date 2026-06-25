package com.saafhawa.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saafhawa.catalog.CatalogService;
import com.saafhawa.catalog.Pollutant;
import com.saafhawa.catalog.PollutantRepository;
import com.saafhawa.catalog.Station;
import com.saafhawa.common.AlertSink;
import com.saafhawa.common.AppProperties;
import com.saafhawa.ingest.archive.RawArchiveService;
import com.saafhawa.ingest.spi.CanonicalMeasurement;
import com.saafhawa.ingest.spi.IngestionWindow;
import com.saafhawa.ingest.spi.ParseResult;
import com.saafhawa.ingest.spi.RawPayload;
import com.saafhawa.ingest.spi.SourceAdapter;
import com.saafhawa.measurement.MeasurementRepository;
import com.saafhawa.qc.QcPipeline;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates one adapter run: fetch → archive → parse → resolve identity → QC → upsert → ledger
 * (architecture.md §2). Failures are isolated to the run and recorded (FR-1.1/1.2/1.3).
 */
@Service
public class AdapterRunner {

    private static final Logger log = LoggerFactory.getLogger(AdapterRunner.class);

    private final RawArchiveService archive;
    private final CatalogService catalog;
    private final PollutantRepository pollutants;
    private final QcPipeline qc;
    private final MeasurementRepository measurements;
    private final IngestionRunRepository runs;
    private final AlertSink alerts;
    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final MeterRegistry metrics;

    public AdapterRunner(RawArchiveService archive, CatalogService catalog,
                         PollutantRepository pollutants, QcPipeline qc,
                         MeasurementRepository measurements, IngestionRunRepository runs,
                         AlertSink alerts, AppProperties props, ObjectMapper objectMapper,
                         MeterRegistry metrics) {
        this.archive = archive;
        this.catalog = catalog;
        this.pollutants = pollutants;
        this.qc = qc;
        this.measurements = measurements;
        this.runs = runs;
        this.alerts = alerts;
        this.props = props;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public IngestionRun run(SourceAdapter adapter, IngestionWindow window) {
        String source = adapter.sourceId();
        IngestionRun run = runs.save(new IngestionRun(source, window.from(), window.to()));
        log.info("Ingestion run {} starting for source {}", run.getId(), source);

        Map<String, Optional<Pollutant>> pollutantCache = new HashMap<>();
        try {
            RawPayload raw = adapter.fetch(window);
            String ref = archive.archive(source, Instant.now(), String.valueOf(run.getId()), raw.bytes());
            run.setRawRef(ref);

            ParseResult parsed = adapter.parse(raw);
            run.setFetched(parsed.fetched());
            run.setRejected(parsed.rejected());

            int inserted = 0;
            int duplicate = 0;
            for (CanonicalMeasurement m : parsed.measurements()) {
                Pollutant pollutant = pollutantCache
                        .computeIfAbsent(m.pollutant(), pollutants::findById)
                        .orElse(null);
                Station station = catalog.resolve(m.station());
                int flags = qc.applyRowLocal(pollutant, m.value());
                boolean isNew = measurements.upsert(new MeasurementRepository.Upsert(
                        station.getId(), m.pollutant(), m.intervalStart(), m.intervalSeconds(),
                        m.value(), m.valueMin(), m.valueMax(), source, ref, flags,
                        qc.rulesetVersion(), m.reportedAqi()));
                if (isNew) {
                    inserted++;
                } else {
                    duplicate++;
                }
            }
            run.setInserted(inserted);
            run.setDuplicate(duplicate);
            run.setRejectSamples(parsed.rejectSamples());
            run.setOutcome(parsed.rejected() > 0 ? "PARTIAL" : "SUCCESS");
            log.info("Run {} done: fetched={} inserted={} duplicate={} rejected={}",
                    run.getId(), parsed.fetched(), inserted, duplicate, parsed.rejected());
        } catch (Exception e) {
            run.setOutcome("FAILED");
            run.setErrorDetail(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("Run {} for source {} failed", run.getId(), source, e);
        } finally {
            run.setFinishedAt(Instant.now());
            runs.save(run);
            recordMetrics(run);
            checkConsecutiveFailures(source);
        }
        return run;
    }

    private void recordMetrics(IngestionRun run) {
        metrics.counter("saafhawa.ingest.inserted", "source", run.getSource()).increment(run.getInserted());
        metrics.counter("saafhawa.ingest.rejected", "source", run.getSource()).increment(run.getRejected());
        if ("FAILED".equals(run.getOutcome())) {
            metrics.counter("saafhawa.ingest.failed", "source", run.getSource()).increment();
        }
    }

    private void checkConsecutiveFailures(String source) {
        int threshold = props.alerting().consecutiveFailureThreshold();
        List<IngestionRun> recent = runs.findTop10BySourceOrderByStartedAtDesc(source);
        int consecutive = 0;
        for (IngestionRun r : recent) {
            if ("FAILED".equals(r.getOutcome())) {
                consecutive++;
            } else {
                break;
            }
        }
        if (consecutive >= threshold) {
            alerts.alert("Ingestion failing: " + source,
                    consecutive + " consecutive failed runs (threshold " + threshold + ")");
        }
    }
}
