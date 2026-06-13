package com.saafhawa;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base for integration tests: a real TimescaleDB + PostGIS database via Testcontainers (NFR-5),
 * so Flyway migrations (hypertables, continuous aggregates, PostGIS) run exactly as in production.
 */
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb-ha:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("saafhawa")
            .withUsername("saafhawa")
            .withPassword("saafhawa")
            .withStartupTimeout(Duration.ofMinutes(4));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        // No upstream calls in tests; disable the scheduled poll and use a temp filesystem archive.
        registry.add("saafhawa.ingest.datagovin.cron", () -> "-");
        registry.add("saafhawa.ingest.datagovin.api-key", () -> "");
        registry.add("saafhawa.archive.type", () -> "filesystem");
        registry.add("saafhawa.archive.base-path", () -> System.getProperty("java.io.tmpdir") + "/sh-archive");
        registry.add("saafhawa.admin-token", () -> "test-admin-token");
    }
}
