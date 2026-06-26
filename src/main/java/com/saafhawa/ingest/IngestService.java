package com.saafhawa.ingest;

import com.saafhawa.common.ApiException;
import com.saafhawa.ingest.spi.IngestionWindow;
import com.saafhawa.ingest.spi.SourceAdapter;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of adapters + scheduling entry points (FR-1.5/1.6). Adapters are discovered by type;
 * scheduled jobs hold a ShedLock so a second node never double-fetches.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final Map<String, SourceAdapter> adaptersById;
    private final AdapterRunner runner;

    public IngestService(List<SourceAdapter> adapters, AdapterRunner runner) {
        this.adaptersById = adapters.stream()
                .collect(Collectors.toMap(SourceAdapter::sourceId, Function.identity()));
        this.runner = runner;
    }

    /** Run any registered adapter over an arbitrary window (FR-1.5; admin backfill). */
    public IngestionRun runSource(String sourceId, IngestionWindow window) {
        SourceAdapter adapter = adaptersById.get(sourceId);
        if (adapter == null) {
            throw ApiException.notFound("Unknown ingestion source: " + sourceId);
        }
        return runner.run(adapter, window);
    }

    public List<String> sources() {
        return List.copyOf(adaptersById.keySet());
    }

    /** Hourly poll of the data.gov.in snapshot (§5.1 cadence). */
    @Scheduled(cron = "${saafhawa.ingest.datagovin.cron:0 5 * * * *}")
    @SchedulerLock(name = "ingest-datagovin", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void pollDataGovIn() {
        SourceAdapter adapter = adaptersById.get("cpcb-datagovin");
        if (adapter == null) {
            return;
        }
        log.info("Scheduled data.gov.in poll starting");
        runner.run(adapter, IngestionWindow.lastHours(1));
    }

    /**
     * Weekly refresh of the current calendar year's urbanemissions bulletin file (§5.2). The
     * upstream dump updates sporadically, so a low-frequency poll is enough; full historical
     * backfill across all years is triggered separately via the admin endpoint (FR-1.5).
     */
    @Scheduled(cron = "${saafhawa.ingest.urbanemissions.cron:0 0 3 * * MON}")
    @SchedulerLock(name = "ingest-urbanemissions", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void pollUrbanEmissions() {
        SourceAdapter adapter = adaptersById.get("urbanemissions");
        if (adapter == null) {
            return;
        }
        log.info("Scheduled urbanemissions bulletin refresh starting");
        Instant now = Instant.now();
        Instant startOfYear = now.atZone(ZoneOffset.UTC).withDayOfYear(1)
                .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        runner.run(adapter, new IngestionWindow(startOfYear, now));
    }
}
