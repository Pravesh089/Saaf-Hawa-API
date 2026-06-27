package com.saafhawa.ingest.source.urbanemissions;

import com.saafhawa.ingest.spi.IngestionWindow;
import com.saafhawa.ingest.spi.ParseResult;
import com.saafhawa.ingest.spi.RawPayload;
import com.saafhawa.ingest.spi.SourceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Backfills city-level daily AQI from the urbanemissions.info community archive of CPCB daily
 * bulletins (§5.2, verification-log V6). The upstream is a sporadically-updated set of whole-file
 * CSV dumps (not a live, date-queryable feed), so a run fetches whole files and relies on the
 * idempotent upsert for dedup; current AQI is served instead by {@code /v1/aqi/stations}.
 *
 * <p>To keep the weekly refresh cheap, the large historical back-catalogue is fetched only when the
 * requested window starts before the current year (i.e. an explicit backfill); the scheduled job,
 * which asks for the current year, pulls just the current-year file(s). Missing files are skipped
 * with a warning rather than failing the whole run.
 */
@Component
public class UrbanEmissionsAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(UrbanEmissionsAdapter.class);

    private final UrbanEmissionsProperties props;
    private final WebClient webClient;
    private final UrbanEmissionsParser parser;

    public UrbanEmissionsAdapter(UrbanEmissionsProperties props, WebClient upstreamWebClient,
                                 UrbanEmissionsParser parser) {
        this.props = props;
        this.webClient = upstreamWebClient;
        this.parser = parser;
    }

    @Override
    public String sourceId() {
        return UrbanEmissionsParser.SOURCE_ID;
    }

    @Override
    public RawPayload fetch(IngestionWindow window) throws Exception {
        int currentYear = window.to().atZone(ZoneOffset.UTC).getYear();
        int fromYear = window.from().atZone(ZoneOffset.UTC).getYear();
        boolean includeHistory = fromYear < currentYear;

        List<String> files = new ArrayList<>();
        if (includeHistory) {
            files.addAll(props.historyFiles());
        }
        files.addAll(props.currentFiles());

        // Concatenate fetched files; each retains its own header so the parser re-syncs schemas.
        StringBuilder combined = new StringBuilder();
        int filesFetched = 0;
        for (String file : files) {
            String csv = fetchFile(file);
            if (csv == null || csv.isBlank()) {
                continue;
            }
            combined.append(csv);
            if (csv.charAt(csv.length() - 1) != '\n') {
                combined.append('\n');
            }
            filesFetched++;
        }
        log.info("Fetched {} of {} urbanemissions bulletin file(s) (history={})",
                filesFetched, files.size(), includeHistory);
        return new RawPayload("text/csv", combined.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String fetchFile(String file) {
        String uri = props.baseUrl() + "/" + file;
        try {
            return webClient.get().uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                    .block(Duration.ofSeconds(120));
        } catch (Exception e) {
            log.warn("Skipping urbanemissions file {} ({})", file, e.getMessage());
            return null;
        }
    }

    @Override
    public ParseResult parse(RawPayload payload) throws Exception {
        return parser.parse(payload.bytes());
    }
}
