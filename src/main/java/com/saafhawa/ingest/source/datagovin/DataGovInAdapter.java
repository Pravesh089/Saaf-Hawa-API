package com.saafhawa.ingest.source.datagovin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.saafhawa.ingest.spi.IngestionWindow;
import com.saafhawa.ingest.spi.ParseResult;
import com.saafhawa.ingest.spi.RawPayload;
import com.saafhawa.ingest.spi.SourceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Semaphore;

/**
 * The data.gov.in CPCB real-time snapshot adapter (§5.1). Pages through all records, combines them
 * into one payload for archival, and parses defensively. Never issues concurrent requests to
 * data.gov.in (NFR-8) and backs off on failure.
 */
@Component
public class DataGovInAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(DataGovInAdapter.class);

    /** Enforces "never exceed 1 concurrent request to data.gov.in" (NFR-8). */
    private final Semaphore permit = new Semaphore(1);

    private final DataGovInProperties props;
    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final DataGovInParser parser;

    public DataGovInAdapter(DataGovInProperties props, WebClient upstreamWebClient,
                            ObjectMapper mapper, DataGovInParser parser) {
        this.props = props;
        this.webClient = upstreamWebClient;
        this.mapper = mapper;
        this.parser = parser;
    }

    @Override
    public String sourceId() {
        return DataGovInParser.SOURCE_ID;
    }

    @Override
    public RawPayload fetch(IngestionWindow window) throws Exception {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "DATAGOVIN_API_KEY not configured; cannot fetch data.gov.in snapshot");
        }
        ArrayNode combined = mapper.createArrayNode();
        for (int page = 0; page < props.maxPages(); page++) {
            int offset = page * props.pageSize();
            JsonNode body = fetchPage(offset);
            JsonNode records = body.path("records");
            if (!records.isArray() || records.isEmpty()) {
                break;
            }
            records.forEach(combined::add);
            if (records.size() < props.pageSize()) {
                break;
            }
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.set("records", combined);
        log.info("Fetched {} records from data.gov.in", combined.size());
        return new RawPayload("application/json",
                mapper.writeValueAsBytes(payload));
    }

    private JsonNode fetchPage(int offset) {
        String uri = UriComponentsBuilder.fromHttpUrl(props.baseUrl())
                .pathSegment(props.resourceId())
                .queryParam("api-key", props.apiKey())
                .queryParam("format", "json")
                .queryParam("limit", props.pageSize())
                .queryParam("offset", offset)
                .build(true).toUriString();
        permit.acquireUninterruptibly();
        try {
            return webClient.get().uri(uri)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                    .block(Duration.ofSeconds(60));
        } finally {
            permit.release();
        }
    }

    @Override
    public ParseResult parse(RawPayload payload) throws Exception {
        return parser.parse(payload.bytes());
    }

    /** Reparse already-archived bytes without fetching (G5); used by tests and backfill tooling. */
    public ParseResult parseBytes(byte[] bytes) throws Exception {
        return parser.parse(new String(bytes, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
    }
}
