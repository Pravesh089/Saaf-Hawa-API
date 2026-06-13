package com.saafhawa;

import com.saafhawa.catalog.Station;
import com.saafhawa.catalog.StationRepository;
import com.saafhawa.ingest.AdapterRunner;
import com.saafhawa.ingest.IngestionRun;
import com.saafhawa.ingest.source.datagovin.DataGovInParser;
import com.saafhawa.ingest.spi.IngestionWindow;
import com.saafhawa.ingest.spi.ParseResult;
import com.saafhawa.ingest.spi.RawPayload;
import com.saafhawa.ingest.spi.SourceAdapter;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end M1 slice: replay a fixture through the full ingestion pipeline (archive → parse →
 * identity → QC → upsert), verify idempotency (FR-1.4), then exercise the public API (FR-4).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineAndApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    AdapterRunner runner;
    @Autowired
    DataGovInParser parser;
    @Autowired
    StationRepository stations;
    @Autowired
    MockMvc mvc;

    /** A test adapter that serves the bundled fixture instead of calling data.gov.in (NFR-5). */
    private SourceAdapter fixtureAdapter() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/fixtures/datagovin-sample.json"));
        return new SourceAdapter() {
            @Override
            public String sourceId() {
                return "cpcb-datagovin";
            }

            @Override
            public RawPayload fetch(IngestionWindow window) {
                return new RawPayload("application/json", bytes);
            }

            @Override
            public ParseResult parse(RawPayload payload) throws Exception {
                return parser.parse(payload.bytes());
            }
        };
    }

    @Test
    @Order(1)
    void ingestsFixtureAndIsIdempotent() throws Exception {
        SourceAdapter adapter = fixtureAdapter();

        IngestionRun first = runner.run(adapter, IngestionWindow.lastHours(1));
        assertThat(first.getInserted()).isEqualTo(6);
        assertThat(first.getRejected()).isEqualTo(2);
        assertThat(first.getOutcome()).isEqualTo("PARTIAL"); // rejects present
        assertThat(first.getRawRef()).isNotNull();

        // Re-running the same window must produce no new rows (FR-1.4).
        IngestionRun second = runner.run(adapter, IngestionWindow.lastHours(1));
        assertThat(second.getInserted()).isZero();
        assertThat(second.getDuplicate()).isEqualTo(6);

        List<Station> delhi = stations.findByCityIgnoreCase("Delhi");
        assertThat(delhi).hasSize(1);
        assertThat(delhi.get(0).getId()).isEqualTo("IN-DL-0001");
        assertThat(delhi.get(0).getStateCode()).isEqualTo("DL");
    }

    @Test
    @Order(2)
    void stationsEndpointReturnsCreatedStations() throws Exception {
        mvc.perform(get("/v1/stations").param("state", "DL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value("IN-DL-0001"))
                .andExpect(jsonPath("$.results[0].city").value("Delhi"));
    }

    @Test
    @Order(3)
    void measurementsEndpointServesValueAndQcFlags() throws Exception {
        // Clean PM2.5 value, no flags.
        mvc.perform(get("/v1/measurements")
                        .param("station", "IN-DL-0001").param("pollutant", "PM2.5")
                        .param("from", "2026-06-01").param("to", "2026-06-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].value").value(182.0))
                .andExpect(jsonPath("$.results[0].flags").isEmpty())
                .andExpect(jsonPath("$.meta.qcRuleset").value("2026.06"));

        // Negative NO2 value is preserved and flagged NEGATIVE (G3: never dropped).
        mvc.perform(get("/v1/measurements")
                        .param("station", "IN-DL-0001").param("pollutant", "NO2")
                        .param("from", "2026-06-01").param("to", "2026-06-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].value").value(-5.0))
                .andExpect(jsonPath("$.results[0].flags[0]").value("NEGATIVE"));
    }

    @Test
    @Order(4)
    void latestEndpointReturnsRecentValues() throws Exception {
        mvc.perform(get("/v1/latest").param("station", "IN-DL-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isNotEmpty());
    }

    @Test
    @Order(5)
    void statusAndDocsAndQcAreLive() throws Exception {
        mvc.perform(get("/v1/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sources").isArray());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/v1/qc/methodology")).andExpect(status().isOk());
    }
}
