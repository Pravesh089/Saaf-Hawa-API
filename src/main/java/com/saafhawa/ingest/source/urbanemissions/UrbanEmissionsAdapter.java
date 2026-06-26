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
import java.util.List;

/**
 * Backfills city-level daily AQI from the urbanemissions.info community archive of CPCB daily
 * bulletins (§5.2, verification-log V6). The upstream repo is a sporadically-updated historical
 * dump per calendar year, not a live feed — current AQI is served instead by
 * {@code /v1/aqi/stations}; this adapter only fills in city-bulletin history. Years with no
 * published file are skipped rather than failing the whole run.
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
        int fromYear = Math.max(props.startYear(), window.from().atZone(ZoneOffset.UTC).getYear());
        int toYear = window.to().atZone(ZoneOffset.UTC).getYear();
        StringBuilder combined = new StringBuilder();
        boolean headerWritten = false;
        int yearsFetched = 0;
        for (int year = fromYear; year <= toYear; year++) {
            String csv = fetchYear(year);
            if (csv == null || csv.isBlank()) {
                continue;
            }
            List<String> lines = csv.lines().toList();
            if (lines.isEmpty()) {
                continue;
            }
            if (!headerWritten) {
                combined.append(lines.get(0)).append('\n');
                headerWritten = true;
            }
            for (int i = 1; i < lines.size(); i++) {
                combined.append(lines.get(i)).append('\n');
            }
            yearsFetched++;
        }
        log.info("Fetched {} urbanemissions bulletin file(s) for years {}-{}", yearsFetched, fromYear, toYear);
        return new RawPayload("text/csv", combined.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String fetchYear(int year) {
        String uri = props.baseUrl() + "/AllIndiaBulletins_" + year + ".csv";
        try {
            return webClient.get().uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                    .block(Duration.ofSeconds(60));
        } catch (Exception e) {
            log.warn("No urbanemissions bulletin file for year {} ({})", year, e.getMessage());
            return null;
        }
    }

    @Override
    public ParseResult parse(RawPayload payload) throws Exception {
        return parser.parse(payload.bytes());
    }
}
