# Saaf Hawa — System Overview

> **One consolidated, current reference**: what the project is, the problem it solves, who uses
> it and how, every component and why it exists, the architecture (high- and low-level), and a
> complete endpoint reference. Current as of the M2 city-bulletin backfill + M3 station-AQI work.
>
> For deeper dives, see the focused docs:
> [`project-guide.md`](project-guide.md) (plain-language intro) ·
> [`architecture.md`](architecture.md) (design rationale) ·
> [`db-design.md`](db-design.md) (data model) ·
> [`operations.md`](operations.md) (run/backup) ·
> [`deployment.md`](deployment.md) / [`deployment-oracle.md`](deployment-oracle.md) (hosting) ·
> [`traceability.md`](traceability.md) (requirement → status) ·
> [`verification-log.md`](verification-log.md) (assumptions to confirm).

---

## 1. What is Saaf Hawa?

**Saaf Hawa ("clean air") is a free REST API that ingests India's official air-quality data,
cleans and quality-flags it, stores it permanently as a normalized time-series, and serves it
through one documented, rate-limited HTTP interface.**

It is a backend data service — "the API *is* the product." A web dashboard is an optional
presentation layer on top, not part of the core.

### The problem it solves

India's official air-quality data (CPCB, via data.gov.in) is public but operationally painful:

| Pain | Saaf Hawa's answer |
|---|---|
| **No history / no bulk API** — the official portal caps you at ~a week per request | Keeps a permanent, growing time-series; backfills history where a source allows it |
| **Messy data** — negatives, implausible zeros, "999" placeholders, sensors stuck for days | Attaches transparent QC flags to every reading; **never deletes or alters** the raw value |
| **No quality metadata** — you can't tell which numbers to trust | Every value carries a flag bitmask + the ruleset version that produced it |
| **Upstream downtime** — when government servers fail, the public loses access | Decoupled ingestion + permanent store keep data available through outages |
| **Everyone re-scrapes & re-cleans differently** — wasted effort, incomparable results | Solves it once, openly, for everyone — one citable, documented dataset |

### Who uses it, and how

| User | Wants | Accesses via |
|---|---|---|
| Data journalist | Pollution numbers as CSV for a story | `GET /v1/measurements?...&format=csv` |
| Researcher / think-tank | Clean datasets with documented QC flags | `/v1/measurements`, `/v1/aqi/cities/{city}` |
| Academic / student | Stable, citable data for a paper | bulk queries + CSV export |
| App developer | Lightweight "current AQI" endpoint | `/v1/latest`, `/v1/aqi/stations` |
| **You (operator)** | Run, monitor, backfill, fix data | `/admin/*` + `/v1/status` |

End users need no login for light use; a free API key (`POST /v1/keys`) raises rate limits.
Access is plain HTTP GET (browser, curl, Python/JS/R), or the interactive Swagger UI at `/docs`.

**Mnemonic:** `/v1/...` = customers (public, read-only); `/admin/...` = you (token-guarded).

---

## 2. High-level architecture

A **modular monolith**: one Spring Boot app (Java 21 / Spring Boot 3.3), internally split into
packages-as-modules, deployed as a single artifact to one VPS. Microservices would add ops cost
with no benefit at this scale (NFR-1). Boundaries are enforced by a strict dependency direction.

```
   Government source                Saaf Hawa (this app)                    Consumers
 ┌──────────────────────┐   ┌───────────────────────────────────────┐   ┌────────────────┐
 │ CPCB via data.gov.in │   │  ingest → archive → parse → resolve     │   │ journalists    │
 │  (hourly snapshot)    │──►│  identity → QC flag → upsert → ledger   │──►│ researchers    │
 ├──────────────────────┤   │                  │                       │   │ students       │
 │ urbanemissions.info  │   │            TimescaleDB (+PostGIS)         │   │ app developers │
 │  (CPCB bulletin CSVs) │──►│      measurement hypertable + aggregates │   │ (a future UI)  │
 └──────────────────────┘   │            + city_daily_aqi              │   └────────────────┘
                            │                  │                       │        ▲
                            │        /v1 REST API  ◄── rate limit ─────┼────────┘
                            │        /admin (token)                    │
                            └───────────────────────────────────────┘
```

### Module map (`com.saafhawa.*`)

| Module | Responsibility | Key requirement |
|---|---|---|
| `common` | RFC-7807 error model, IST/time helpers, config properties | cross-cutting |
| `catalog` | stations, pollutants, aliases, identity resolution | FR-2 |
| `ingest` | adapter SPI, scheduler, ingestion-run ledger, raw archive | FR-1 |
| `ingest.source.datagovin` | the data.gov.in CPCB live-snapshot adapter | §5.1 |
| `ingest.source.urbanemissions` | the urbanemissions.info city-bulletin backfill adapter | §5.2 |
| `qc` | QC flag taxonomy + pipeline (row-local now; context QC = M3) | FR-3 |
| `aqi` | pure CPCB AQI computation + city-bulletin store/writer | §4.2, FR-4.5 |
| `measurement` | hypertable access (upsert, range/aggregate/latest/trailing reads) | FR-1.4, FR-4.4 |
| `api` | public `/v1` REST controllers + DTOs | FR-4 |
| `ops` | `/v1/status`, `/admin`, metrics | FR-7 |
| `export` | bulk CSV/Parquet (boundary only; M3) | FR-5 |

**Dependency rule:** `api`/`ops` → (`catalog`, `qc`, `aqi`, `ingest`, `measurement`) → `common`.
Nothing depends on `api`. Ingest adapters depend only on the SPI + `catalog` + `qc` — never on
each other, so one source failing never affects others (FR-1.1 "fail in isolation").

### Technology choices (one-line rationale)

| Choice | Why |
|---|---|
| Java 21 / Spring Boot 3.3 | LTS, virtual-thread ready, mature ecosystem |
| PostgreSQL 16 + **TimescaleDB** | hypertables + continuous aggregates fit a 10⁸–10⁹-row time-series |
| **PostGIS** | spatial index for `nearest`-station queries |
| Flyway | versioned, reviewable DDL as migrations |
| JPA + JdbcTemplate | JPA for catalog/CRUD; native SQL for hypertable upserts & aggregates |
| springdoc-openapi | auto OpenAPI 3 + Swagger UI at `/docs` |
| bucket4j | in-process token-bucket rate limiting with standard headers |
| ShedLock | distributed scheduler lock so a second node never double-fetches |
| MinIO + AWS SDK v2 | S3-compatible raw-payload archive, local and prod |
| opencsv | parse the urbanemissions bulletin CSVs |
| Testcontainers | real Postgres/Timescale + MinIO in integration tests |
| Micrometer/Prometheus | operational metrics at `/actuator/prometheus` |

---

## 3. The ingestion pipeline (low-level)

Every source runs through one orchestrator, `ingest.AdapterRunner.run(adapter, window)`:

```
Scheduler ──(@Scheduled + ShedLock)──► AdapterRunner
                                          │  adapter.fetch(window) ──► RawPayload (bytes)
                                          │  RawArchiveService.archive(...)   gzip → object store   [FR-1.3]
                                          │  adapter.parse(payload) ──► ParseResult
                                          │       ├─ measurements: List<CanonicalMeasurement>
                                          │       └─ cityBulletins: List<CityBulletinRow>
                                          │  per measurement:
                                          │       CatalogService.resolveStation()  alias/auto-create [FR-2.1]
                                          │       QcPipeline.applyRowLocal()        flag bitmask      [FR-3.1]
                                          │       MeasurementRepository.upsert()    idempotent        [FR-1.4]
                                          │  city bulletins:
                                          │       CityBulletinWriter.upsertAll()    chunked JDBC batch [FR-1.4]
                                          │  IngestionRun saved: counts, outcome, archive ref          [FR-1.2]
```

### The adapter SPI (`ingest.spi.SourceAdapter`)

Three methods — a new source is added by implementing this interface only:

```java
String sourceId();
RawPayload fetch(IngestionWindow window) throws Exception;   // download raw bytes
ParseResult parse(RawPayload payload) throws Exception;      // → measurements and/or city bulletins
```

`ParseResult` carries **two** result lists so one runner serves both station-measurement and
city-bulletin sources: `measurements()` (station/interval grain) and `cityBulletins()` (city/day
grain). Unparseable rows are counted and a bounded sample retained (`reject(...)`) — never
silently dropped (FR-2.3).

### Cross-cutting ingestion guarantees

- **Archive-before-parse (FR-1.3):** raw bytes are archived *before* parsing, so reprocessing
  needs no re-fetch. Path: `source/yyyy/MM/dd/HHmmss-<runId>.<ext>.gz`.
- **Idempotency (FR-1.4):** `INSERT ... ON CONFLICT DO UPDATE` on each table's natural key.
  Re-running any window yields zero net duplicates.
- **Scheduling (FR-1.6):** Spring `@Scheduled` + ShedLock (Postgres lock provider).
- **Backfill (FR-1.5):** `POST /admin/ingest/{source}?from&to` runs any adapter over an arbitrary
  window through the same code path.
- **Polite client (NFR-8):** shared `WebClient` with a contact User-Agent, exponential backoff,
  and (for data.gov.in) a 1-permit semaphore — never concurrent.
- **Error isolation (FR-1.1/1.2):** failures are caught per-run, recorded with `outcome=FAILED`
  and detail; consecutive failures ≥ threshold emit an ops alert via a pluggable `AlertSink`.

### The two sources today

| | `cpcb-datagovin` | `urbanemissions` |
|---|---|---|
| Grain | station × pollutant × hour | city × day |
| Feed type | live snapshot (no history) | sporadically-updated historical CSV dump |
| Schedule | hourly at `:05` | weekly (Mon 03:00), current-year files only |
| Writes to | `measurement` hypertable | `city_daily_aqi` |
| Backfill? | no (snapshot only — history accrues forward) | yes — `Master_2024` (2015–2024) + 2025 files |
| Quirks handled | field-name variants, `OZONE→O3`, "NA", in-payload dedupe | two CSV schemas (clean + "openrefined" index/year), header re-sync |

> The urbanemissions parser is **header-driven**: it maps columns by name and re-syncs whenever a
> header row appears mid-stream, so multiple files with different layouts parse in one payload.
> A full backfill is ~478k rows, written via chunked JDBC `batchUpdate` (`CityBulletinWriter`).

---

## 4. Identity resolution, QC, and AQI (low-level)

### Station identity (`catalog`, FR-2.1)

Canonical IDs are human-legible and stable: **`IN-<STATE>-<seq4>`** (e.g. `IN-DL-0042`).
On first sight of an upstream station: (1) exact-match an existing alias; (2) else fuzzy-match on
normalized name + haversine distance < 300 m; (3) high confidence → attach alias; (4) low →
auto-create a candidate flagged `needs_review`. IDs never change; `POST /admin/stations/merge`
re-points aliases and **preserves history** (never deletes). Each upstream key lives in
`station_alias (source, source_key) → station_id`.

### Quality control (`qc`, FR-3)

Raw values are **never altered** — flags are additive (G3). Flags are a **bitmask `int`** on
`measurement` (4 bytes, compressible; avoids exploding the largest table with join/array storage):

```
NEGATIVE=1  ZERO_SUSPECT=2  SENTINEL=4  STUCK=8  SPIKE=16  RANGE=32  DUPLICATE_SOURCE=64
```

- **Row-local rules** run synchronously on ingest (`QcPipeline.applyRowLocal`): SENTINEL (value in
  configured placeholder set, e.g. 999/9999), NEGATIVE (`<0`), ZERO_SUSPECT (`==0` where the
  pollutant marks zero implausible), RANGE (outside the pollutant's min/max).
- **Context rules** (STUCK = unchanging for N hours, SPIKE = sudden jump) are a scheduled job —
  **deferred to M3** (the next candidate workstream).
- Thresholds live in the versioned `qc_config` table; responses echo the ruleset version that
  flagged each point (FR-3.2). `GET /v1/qc/methodology` renders the live config as Markdown.

### AQI computation (`aqi`, §4.2)

`AqiCalculator` is a **pure function** (no Spring deps, unit-tested in isolation). Breakpoints are
loaded from the seeded `aqi_breakpoint` table — never hard-coded. Algorithm:

1. Per pollutant, compute a sub-index by **linear interpolation** within its CPCB breakpoint band
   (open-ended top band clamps to index 500).
2. **Overall AQI = max** of the valid sub-indices; the pollutant at the max is the
   "prominent pollutant".
3. **Validity:** requires ≥3 sub-indices *and* at least one of PM2.5/PM10 (CPCB rule). The number
   is still returned when invalid, just marked `valid:false`.

`/v1/aqi/stations` adds the CPCB **averaging windows** on top of the calculator (see §5):
24-hour trailing mean for most pollutants, 8-hour for CO/O₃, with a minimum-completeness rule, and
a graceful fallback to the latest reading while history accumulates.

---

## 5. Complete endpoint reference

Base path `/v1` for public, `/admin` for operator. All reads send `Cache-Control`; list endpoints
use stable sort + cursor pagination; errors are RFC-7807 `application/problem+json`. Rate limiting
adds `X-RateLimit-*` headers and `429 + Retry-After`.

### Public — stations (`catalog`)

| Endpoint | Purpose | Key params |
|---|---|---|
| `GET /v1/stations` | List/search the monitoring-station registry | `state`, `city`, `bbox`, `geojson=true`, `cursor`, `limit` |
| `GET /v1/stations/{id}` | One station's metadata | path `id` (`IN-DL-0042`) |
| `GET /v1/stations/nearest` | Nearest stations to a point (PostGIS) | `lat`, `lon`, `limit` |

### Public — measurements & latest (`measurement`)

| Endpoint | Purpose | Key params |
|---|---|---|
| `GET /v1/measurements` | **The core endpoint** — pollution readings over a time range | `station` or `city`, `pollutant`, `from`, `to` (required), `interval=raw\|day\|month`, `include_flagged`, `format=json\|csv`, `cursor`, `limit` |
| `GET /v1/latest` | Most recent reading per pollutant | `station` or `city` (returns `ageMinutes`) |

`interval=day\|month` reads continuous aggregates (never raw); `format=csv` streams a download.

### Public — AQI (`aqi`)

| Endpoint | Purpose | Key params |
|---|---|---|
| `GET /v1/aqi/cities` | List all cities that have bulletin AQI history (299 today) | — |
| `GET /v1/aqi/cities/{city}` | A city's **daily AQI history** (bulletin-sourced, 2015→) | `from`, `to` |
| `GET /v1/aqi/stations` | **Station-computed CPCB AQI** (live) | `station` or `city`; `basis=avg\|latest` |

`/v1/aqi/stations` basis modes:
- `basis=avg` (default) — CPCB trailing 24h mean (8h for CO/O₃), min-completeness ≥16/24h
  (≥6/8h). Stations short on history return `basis=latest-fallback` so a real number is always
  served. QC-flagged values excluded.
- `basis=latest` — quick snapshot from each pollutant's most recent reading.
- Each result carries `aqi`, `category`, `prominentPollutant`, `subIndices`, `valid`,
  `measuredAt`, `ageMinutes`, and `basis`; sorted most-polluted-first.

### Public — meta & signup

| Endpoint | Purpose |
|---|---|
| `GET /v1/status` | Health + upstream freshness: per-source last attempt/outcome/inserted, stations reporting in 3h/24h |
| `GET /v1/qc/methodology` | Plain-Markdown explanation of QC flags, generated from the live `qc_config` |
| `POST /v1/keys` | Self-service free API-key signup (body: `{email}`) |
| `GET /docs` | Interactive OpenAPI 3 / Swagger UI |
| `GET /actuator/prometheus` | Prometheus metrics |

### Admin — operator only (`Authorization: Bearer <admin-token>`)

| Endpoint | Purpose |
|---|---|
| `POST /admin/ingest/{source}` | Run/backfill an adapter over a window (`?from&to`). Sources: `cpcb-datagovin`, `urbanemissions` |
| `POST /admin/stations/merge` | Merge duplicate stations (`?from&into`), preserving history |
| `POST /admin/qc/reload` | Hot-reload the active QC config without a restart |
| `POST /admin/keys/{id}/revoke` | Disable an abusive API key |

---

## 6. Data model (summary)

Full rationale in [`db-design.md`](db-design.md); migrations in `src/main/resources/db/migration`.

| Table | Grain | Notes |
|---|---|---|
| `measurement` | station × pollutant × interval | **TimescaleDB hypertable**, 7-day chunks, columnar compression >30d. Natural key `(station_id, pollutant, interval_start, interval_seconds, source)`. `qc_flags` bitmask. |
| `measurement_daily` / `_monthly` | rollups | continuous aggregates; power `interval=day\|month` and availability % |
| `city_daily_aqi` | city × day | bulletin history (separate from `measurement` by design). Natural key `(city, aqi_date, source)`. ~478k rows, 2015→. |
| `station` | per monitor | `IN-<STATE>-<seq4>` PK, PostGIS `geom`, `needs_review` |
| `station_alias` | per source-key | `(source, source_key) → station_id`, never hard-deleted |
| `pollutant` | controlled vocab | units, `zero_implausible`, range bounds (drive QC) |
| `aqi_breakpoint` | per band | data-driven CPCB bands + `avg_hours`; loaded by `AqiCalculator` |
| `qc_config` | versioned | active ruleset; echoed in responses |
| `ingestion_run` | per run | ops ledger: counts, outcome, archive ref, reject samples |
| `api_client` | per key | SHA-256 key hash, tier, revoked flag |
| `shedlock` | scheduler lock | standard ShedLock |

---

## 7. Status & what's next

**Done:** M1 (data.gov.in pipeline, QC, core read API, keys/rate-limit, docker compose, OpenAPI) ·
M2 city-bulletin backfill (`urbanemissions`, ~478k rows, 2015→) · M3 station-computed AQI
(`/v1/aqi/stations`, 24h/8h averaging + latest fallback).

**Candidate next workstreams** (see [`traceability.md`](traceability.md)):
- **Context QC (STUCK/SPIKE)** — the scheduled cross-reading QC job (FR-3.1); pure backend.
- **Bulk export (CSV/Parquet)** — FR-5.1/5.2; currently a package boundary.
- **OpenAQ second source** — FR-1.1; a third adapter for cross-validation.
- **Dashboard UI** — a separate front-end on the public `/v1` endpoints (map, city charts, CSV
  download). High-visibility; not core plumbing.

> **Note on doc currency:** `project-guide.md` and `architecture.md` predate the M2/M3 work and
> describe the project as "M1 only." This file (`system-overview.md`) is the current consolidated
> source of truth; the others remain accurate for design *rationale*.
