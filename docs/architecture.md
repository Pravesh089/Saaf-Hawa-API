# Saaf Hawa — Architecture

> Status: living document. Covers M0 design + the M1 vertical slice implemented in this repo.
> Every technology decision carries a one-line rationale (FDD §0.1).

## 1. Shape: modular monolith

A single Spring Boot application, internally decomposed into packages-as-modules. We deploy one
artifact to one VPS (NFR-1), so microservices would add ops cost with no benefit (FDD §12,
"no microservice ops tax"). Module boundaries are enforced by package structure and a strict
dependency direction; nothing precludes extracting a module later.

```
com.saafhawa
├── common      cross-cutting: error model (RFC-7807), time/IST helpers, config props
├── catalog     stations, pollutants, aliases, identity resolution            (FR-2)
├── ingest      adapter SPI, scheduler, ingestion-run ledger, raw archive     (FR-1)
│   └── source.datagovin   the data.gov.in CPCB snapshot adapter              (§5.1)
├── qc          QC flag taxonomy + pipeline                                   (FR-3)
├── aqi         pure AQI computation (data-driven breakpoints)                (§4.2)
├── api         public /v1 REST controllers + DTOs                            (FR-4)
├── export      bulk CSV/Parquet export (M3; stubbed boundary in M1)          (FR-5)
└── ops         /v1/status, admin API, metrics                               (FR-7)
```

**Dependency rule:** `api`/`ops` → (`catalog`,`qc`,`aqi`,`ingest`) → `common`. No module depends
on `api`. `ingest` adapters depend only on the SPI + `catalog` + `qc`, never on each other
(FR-1.1 fail-in-isolation).

## 2. Ingestion pipeline (FR-1)

```
Scheduler ──(ShedLock)──> AdapterRunner ──> Source adapter.fetch(window)
                                              │
                              raw bytes ──> RawArchiveService (gzip → object store)   [FR-1.3, G5]
                                              │
                              adapter.parse() ──> List<CanonicalMeasurement>
                                              │
                              CatalogService.resolveStation() (alias/auto-create)     [FR-2.1]
                                              │
                              QcPipeline.applyRowLocal() (NEGATIVE/ZERO/SENTINEL/RANGE)[FR-3.1]
                                              │
                              MeasurementRepository.upsert() (natural-key, idempotent) [FR-1.4]
                                              │
                              IngestionRun recorded (counts, outcome, archive ref)     [FR-1.2]
```

- **Adapter SPI** (`ingest.spi.SourceAdapter`): `sourceId()`, `fetch(IngestionWindow)` →
  `RawPayload`, `parse(RawPayload)` → `ParseResult`. Each adapter is a Spring bean discovered by
  type; new sources are added by implementing the interface only (FR-1.1).
- **Archive-before-parse** (FR-1.3, G5): the runner archives raw bytes *before* calling
  `parse`, so reprocessing from archive needs no re-fetch. Archive path =
  `source/yyyy/MM/dd/HHmmss-<runId>.json.gz`.
- **Idempotency** (FR-1.4): measurements have natural key
  `(station_id, pollutant, interval_start, interval_seconds, source)`; ingest uses
  `INSERT ... ON CONFLICT DO UPDATE`. Re-running any window produces zero net duplicates.
- **Scheduling** (FR-1.6): Spring `@Scheduled` + ShedLock (Postgres `LockProvider`) so a second
  node never double-fetches. Single node in v1.
- **Backfill** (FR-1.5): admin endpoint `POST /admin/ingest/{source}` with a window runs any
  adapter over an arbitrary historical range using the same code path.
- **Polite client** (NFR-8): all HTTP via a shared `WebClient` with a contact User-Agent,
  exponential backoff, and a 1-permit semaphore for data.gov.in (never concurrent).

## 3. Threading & scheduling model

- Ingestion runs on a dedicated bounded `ThreadPoolTaskScheduler` (separate from Tomcat request
  threads) so a slow upstream never starves the API (NFR-1).
- Context QC (STUCK/SPIKE) and availability rollups run as separate scheduled jobs (M3) over
  trailing windows; they are read-heavy and hit continuous aggregates, not the raw hypertable.
- ShedLock guards every scheduled method so horizontal scaling stays safe.

## 4. Error-handling strategy

- **Ingest:** failures are caught per-adapter; the run is recorded with `outcome=FAILED` and the
  error detail; consecutive failures ≥ threshold emit an ops alert (FR-1.2, pluggable
  `AlertSink`). One adapter failing never aborts others (FR-1.1).
- **Parse rejects** (FR-2.3): unparseable rows are counted and a bounded sample retained on the
  run, never silently dropped.
- **API:** a single `@RestControllerAdvice` renders every error as RFC-7807
  `application/problem+json` (FDD §7). Validation, not-found, rate-limit, and upstream errors all
  map to typed problems.

## 5. Station identity scheme (FDD §7)

Canonical station IDs are human-legible and stable: `IN-<STATE>-<seq4>`, e.g. `IN-DL-0042`.
`<STATE>` is the 2-letter Indian state/UT code; `<seq4>` is a zero-padded per-state sequence
assigned at first sighting. The ID never changes once issued (merge preserves history by
re-pointing aliases, never deleting — FR-2.1, risk §12). Each upstream identifier is stored in
`station_alias` mapping source+source_key → canonical station.

**Alias matching (FR-2.1, §5.3):** on first sight of a source station we (1) exact-match on an
existing alias; (2) else fuzzy-match on normalized name (lowercase, strip punctuation/extra
spaces) + haversine distance < 300 m; (3) if confidence high, attach alias; (4) if low,
auto-create a candidate station flagged `needs_review`. A manual-override file
(`config/station-overrides.yml`) forces specific source-key → canonical mappings for exceptions.

## 6. AQI computation (§4.2, FR-6)

`aqi.AqiCalculator` is a pure function with no Spring dependencies, unit-tested in isolation.
Breakpoints are loaded from the `aqi_breakpoint` table (seeded by Flyway), never hard-coded
(design obligation §9.6). AQI = max of valid pollutant sub-indices; valid only if ≥3 sub-indices
exist and at least one is PM2.5/PM10. Both upstream-reported and self-computed AQI are exposed
and labelled (FDD §4.2).

## 7. API surface (FR-4)

Versioned under `/v1`. springdoc generates OpenAPI 3 at `/docs` (NFR-4). All reads carry
`Cache-Control`; list endpoints use stable sort + cursor pagination (FR-4.7). Rate limiting via
bucket4j with `X-RateLimit-*` headers and 429 + `Retry-After` (FR-6.2).

## 8. Technology decisions (one-line rationale each)

| Choice | Rationale |
|---|---|
| Java 21 / Spring Boot 3.3 | FDD target stack; LTS, virtual-thread ready, mature ecosystem |
| Maven | Reproducible, ubiquitous; simpler CI than Gradle for a solo maintainer |
| PostgreSQL 16 + TimescaleDB | Hypertables + continuous aggregates fit 10⁸–10⁹ row time-series (FDD §4.1) |
| PostGIS | `nearest` station geo query (FR-4.3) needs spatial index |
| Flyway | Versioned, reviewable DDL as migrations (FDD §0.1 db-design obligation) |
| Spring Data JPA + JdbcTemplate | JPA for catalog/CRUD; native SQL for hypertable upserts & aggregates |
| springdoc-openapi | Auto OpenAPI 3 spec + Swagger UI (NFR-4) |
| bucket4j | In-process token-bucket rate limiting, standard headers (FR-6.2) |
| ShedLock | Distributed scheduler lock without a new dependency tier (FR-1.6) |
| MinIO + AWS SDK v2 | S3-compatible raw archive locally and in prod (G5, §5 archive) |
| Testcontainers | Real Postgres/Timescale + MinIO in integration tests (NFR-5) |
| Micrometer/Prometheus | Operational metrics (FR-7.3) |
| Resilience: WebClient + Reactor retry | Backoff to polite-client upstreams (NFR-8) |

## 9. What is implemented now (M1 slice) vs deferred

Implemented: ingestion SPI, data.gov.in adapter, raw archive, ingestion-run ledger, catalog +
alias resolution, row-local QC, AQI module, core read API (`/v1/stations*`, `/v1/measurements`,
`/v1/latest`, `/v1/aqi/cities`), `/v1/status`, API keys + rate limiting, docker compose,
OpenAPI, seed + integration tests. Deferred to later milestones and tracked in
`traceability.md`: bulletin/OpenAQ adapters (M2/M3), context QC + availability rollups (M3),
bulk export (M3), Parquet snapshots (M3).
