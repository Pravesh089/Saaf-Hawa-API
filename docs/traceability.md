# Requirements Traceability (FDD §0.2)

Status legend: ✅ implemented (M1) · 🟡 partial (boundary in place) · ⏳ deferred to named milestone.

| Req | Summary | Status | Where / note |
|---|---|---|---|
| FR-1.1 | Adapter SPI, independent scheduling, fail-in-isolation | ✅ | `ingest.spi.SourceAdapter`, `ingest.AdapterRunner` |
| FR-1.2 | IngestionRun ledger + consecutive-failure alert | ✅ | `ingest.IngestionRun`, `ingest.AdapterRunner`, `ops.AlertSink` |
| FR-1.3 | Raw payload archived pre-parse | ✅ | `ingest.archive.RawArchiveService` |
| FR-1.4 | Idempotent natural-key upsert | ✅ | `measurement` unique key, `MeasurementRepository.upsert` |
| FR-1.5 | Backfill via admin endpoint | ✅ | `ops.AdminController POST /admin/ingest/{source}` |
| FR-1.6 | Scheduling + distributed lock | ✅ | `@Scheduled` + ShedLock, `IngestScheduler` |
| FR-2.1 | Canonical registry + aliases + review flag + merge | ✅ resolve/auto-create; 🟡 merge admin | `catalog.CatalogService`, `AdminController` merge |
| FR-2.2 | Pollutant vocab + unit normalization | ✅ | `catalog.PollutantNormalizer` (`OZONE→O3`) |
| FR-2.3 | Parse rejects counted + sampled | ✅ | `ParseResult.rejects`, stored on run |
| FR-3.1 | Row-local QC on ingest; context QC scheduled | ✅ row-local; ⏳ M3 context | `qc.QcPipeline` |
| FR-3.2 | Versioned QC config; ruleset echoed | ✅ | `qc.QcConfig`, `qc_config` table, response meta |
| FR-3.3 | Availability % materialized | ⏳ M3 | continuous aggregates defined; rollup job M3 |
| FR-3.4 | QC methodology page from config | ✅ | `GET /v1/qc/methodology` |
| FR-4.1 | `GET /v1/stations` filters + GeoJSON | ✅ | `api.StationController` |
| FR-4.2 | `GET /v1/stations/{id}` | ✅ | `api.StationController` |
| FR-4.3 | `GET /v1/stations/nearest` (PostGIS) | ✅ | `api.StationController` |
| FR-4.4 | `GET /v1/measurements` + interval/format/caps/cursor | ✅ raw+day/month; csv+json | `api.MeasurementController` |
| FR-4.5 | AQI: station-computed + city bulletin | ✅ station-computed (`GET /v1/aqi/stations`, all-pollutant CPCB sub-index); 🟡 city bulletin backfill pending | `api.AqiController`, `aqi.AqiService` |
| FR-4.6 | `GET /v1/latest` + age-in-minutes | ✅ | `api.LatestController` |
| FR-4.7 | Stable sort, cursor pagination, Cache-Control | ✅ | controllers + `CacheControl` |
| FR-5.1/5.2 | Bulk export + Parquet snapshots | ⏳ M3 | `export` package boundary only |
| FR-6.1 | Anonymous + keyed tiers, self-service signup | ✅ | `api.ApiKeyController`, `api_client` |
| FR-6.2 | bucket4j rate limiting + headers + 429 | ✅ | `api.ratelimit.RateLimitFilter` |
| FR-7.1 | Admin API (static token) | ✅ | `ops.AdminController` |
| FR-7.2 | `GET /v1/status` freshness | ✅ | `ops.StatusController` |
| FR-7.3 | Micrometer/Prometheus metrics | ✅ | actuator + micrometer; `/actuator/prometheus` |
| NFR-1 | 8GB VPS, continuous aggregates | 🟡 | aggregates defined; load test M4 |
| NFR-2 | p95 < 500ms ≤1 station-year daily | 🟡 | aggregate-backed; measured M4 |
| NFR-3 | `docker compose up` full stack + seed | ✅ | `docker-compose.yml`, seed migration |
| NFR-4 | OpenAPI 3 at /docs, CI-snapshot check | ✅ spec; 🟡 snapshot-diff | springdoc, `/docs` |
| NFR-5 | Testcontainers adapter + QC tests | ✅ | `src/test` (fixture replay + QC) |
| NFR-6 | 12-factor env config, JSON logs | ✅ env; 🟡 JSON logs | `application.yml` env binding; JSON log appender is a documented follow-up (operations.md) |
| NFR-7 | Backup plan documented | ✅ | `docs/operations.md` |
| NFR-8 | Polite client (UA, backoff, 1 concurrent) | ✅ | `ingest.http.UpstreamWebClient` |

Milestones: M1 scope is largely ✅. M2 (bulletins/history) and M3 (OpenAQ, context QC,
availability, export) are scaffolded behind clean boundaries and tracked above.
