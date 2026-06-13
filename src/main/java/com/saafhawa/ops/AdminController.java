package com.saafhawa.ops;

import com.saafhawa.api.key.ApiClient;
import com.saafhawa.api.key.ApiClientRepository;
import com.saafhawa.catalog.CatalogService;
import com.saafhawa.common.ApiException;
import com.saafhawa.common.AppProperties;
import com.saafhawa.ingest.IngestService;
import com.saafhawa.ingest.IngestionRun;
import com.saafhawa.ingest.spi.IngestionWindow;
import com.saafhawa.qc.QcConfig;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Admin API (FR-7.1), guarded by a static bearer token (ambiguities.md #9). Triggers backfills,
 * merges stations, reloads QC config, and revokes keys.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AppProperties props;
    private final IngestService ingestService;
    private final CatalogService catalog;
    private final QcConfig qcConfig;
    private final ApiClientRepository apiClients;

    public AdminController(AppProperties props, IngestService ingestService, CatalogService catalog,
                           QcConfig qcConfig, ApiClientRepository apiClients) {
        this.props = props;
        this.ingestService = ingestService;
        this.catalog = catalog;
        this.qcConfig = qcConfig;
        this.apiClients = apiClients;
    }

    /** FR-1.5: run any adapter over an arbitrary historical window. */
    @PostMapping("/ingest/{source}")
    public Map<String, Object> backfill(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @PathVariable String source,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to) {
        authorize(auth);
        IngestionWindow window = (from != null && to != null)
                ? new IngestionWindow(Instant.parse(from), Instant.parse(to))
                : IngestionWindow.lastHours(1);
        IngestionRun run = ingestService.runSource(source, window);
        return Map.of("runId", run.getId(), "outcome", String.valueOf(run.getOutcome()),
                "inserted", run.getInserted(), "duplicate", run.getDuplicate(),
                "rejected", run.getRejected());
    }

    /** FR-2.1: merge one station into another, preserving history. */
    @PostMapping("/stations/merge")
    public Map<String, String> merge(@RequestHeader(value = "Authorization", required = false) String auth,
                                     @RequestParam String from, @RequestParam String into) {
        authorize(auth);
        catalog.merge(from, into);
        return Map.of("status", "merged", "from", from, "into", into);
    }

    /** FR-7.1: hot-reload active QC config after a threshold change. */
    @PostMapping("/qc/reload")
    public Map<String, String> reloadQc(@RequestHeader(value = "Authorization", required = false) String auth) {
        authorize(auth);
        qcConfig.reload();
        return Map.of("status", "reloaded", "rulesetVersion", qcConfig.version());
    }

    /** FR-7.1: revoke an API key. */
    @PostMapping("/keys/{id}/revoke")
    public Map<String, Object> revokeKey(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @PathVariable Long id) {
        authorize(auth);
        ApiClient client = apiClients.findById(id)
                .orElseThrow(() -> ApiException.notFound("API client not found: " + id));
        client.setRevoked(true);
        apiClients.save(client);
        return Map.of("status", "revoked", "id", id);
    }

    private void authorize(String auth) {
        String expected = props.adminToken();
        if (expected == null || expected.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Admin API disabled (no token configured)");
        }
        if (auth == null || !auth.equals("Bearer " + expected)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or missing admin token");
        }
    }
}
