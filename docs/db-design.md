# Saaf Hawa — Database Design

PostgreSQL 16 + TimescaleDB + PostGIS. DDL lives as Flyway migrations in
`src/main/resources/db/migration`. This doc explains the *why*; the migrations are the *what*.

## 1. Time-series modelling choice (design obligation §9.2)

The `measurement` table is a **TimescaleDB hypertable**. Justification:

- Volume: 10⁸–10⁹ rows over years (FDD §4.1). A plain table's indexes and autovacuum degrade at
  that scale; hypertables partition transparently into time **chunks**, keeping per-chunk indexes
  small and enabling chunk-level compression and retention.
- Query shape (NFR-2): nearly all reads are *range scans over time for a station+pollutant*.
  Chunking by time + a composite index `(station_id, pollutant, interval_start)` turns these into
  small, contiguous scans.
- Rollups (NFR-1): hour→day→month aggregation is served by **continuous aggregates**
  (materialized, incrementally refreshed) so the API never scans raw rows for daily/monthly
  queries.

**Chunk interval:** 7 days. At ~565 stations × 8 pollutants × 24 hourly rows ≈ 108k rows/day ≈
760k rows/week — a comfortable chunk size (Timescale guidance: chunk ≈ 25% of RAM working set;
well under that on an 8 GB VPS, NFR-1).

**Compression:** native columnar compression on chunks older than 30 days, segmented by
`station_id, pollutant` and ordered by `interval_start DESC`. Old air-quality data is read in
bulk ranges and never updated, so columnar compression (typ. 10–20×) directly addresses disk
growth (FDD §12 risk).

**Retention:** none on `measurement` (history is the product, G2). Raw-payload archive has a
lifecycle policy instead (cold storage > 1 yr).

## 2. Tables

### `pollutant` — controlled vocabulary (§4.1)
`code` PK (`PM2.5,PM10,NO2,SO2,CO,O3,NH3,Pb`), `display_name`, `unit` (CPCB convention:
CO=mg/m³, others µg/m³), `zero_implausible` bool (drives `ZERO_SUSPECT`), `range_min/range_max`
(drives `RANGE`). Seeded by migration. Pollutant is a small dimension — plain table.

### `station` — canonical monitor registry (§4.1, FR-2.1)
`id` text PK (`IN-DL-0042` scheme, §arch 5). `name, city, state, state_code, agency,
station_type` (`CAAQMS|MANUAL|UNKNOWN`), `geom geography(Point,4326)` (PostGIS),
`status` (`ACTIVE|INACTIVE|UNKNOWN`), `needs_review` bool, `first_seen, last_seen` timestamptz.
GiST index on `geom` for `nearest` (FR-4.3). Plain table (≤ ~10k rows).

### `station_alias` — per-source identity map (§4.1, FR-2.1)
`(source, source_key)` unique → `station_id` FK; `source_name_raw`, `match_method`
(`EXACT|FUZZY|OVERRIDE|AUTO`), `match_confidence`. Never hard-deleted; merge re-points aliases.

### `measurement` — the hypertable (§4.1)
Columns: `station_id` FK, `pollutant` FK, `interval_start timestamptz`, `interval_seconds int`,
`value double`, `value_min`, `value_max`, `source` text, `raw_ref` text (archive pointer),
`qc_flags int` (bitmask, see §3), `qc_ruleset` text, `reported_aqi int` null, `ingested_at`.
**Natural key / dedupe (FR-1.4, §4.3):** unique `(station_id, pollutant, interval_start,
interval_seconds, source)`. Hypertable partitioned on `interval_start`. Composite index
`(station_id, pollutant, interval_start DESC)`.

### `city_daily_aqi` — 2015+ history layer (§4.1, FR-4.5)
Kept **separate** from `measurement` (FDD §4.1 explicitly warns against forcing them together):
different grain (city/day vs station/interval), different source (bulletins), different lifecycle.
`(city, date, source)` unique; `aqi int`, `prominent_pollutant`, `qc_flags`, `source`. Not a
hypertable — city×day is ≤ ~1.5M rows over a decade, trivially handled by a btree on `(city,date)`.

### `ingestion_run` — operational ledger (§4.1, FR-1.2)
`id`, `source`, `window_start/end`, `started_at/finished_at`, `outcome`
(`SUCCESS|PARTIAL|FAILED`), `fetched/inserted/updated/duplicate/rejected` counts, `error_detail`,
`raw_ref`, `reject_samples jsonb`. Btree on `(source, started_at DESC)`.

### `api_client` — key holder (§4.1, FR-6)
`id`, `key_hash` (SHA-256, never store raw key), `email`, `tier`
(`ANONYMOUS|KEYED|PARTNER`), `rate_limit_override` int null, `created_at`, `revoked` bool.

### `qc_config` — versioned thresholds (FR-3.2)
`ruleset_version` (e.g. `2026.06`), `key`, `value`, with one row marked `active`. Responses echo
the ruleset version that flagged each point (FR-3.2). Generated QC methodology page reads this
(FR-3.4).

### `aqi_breakpoint` — data-driven AQI bands (§4.2, §9.6)
`pollutant`, `band_low_index`, `band_high_index`, `conc_low`, `conc_high`, `avg_hours`. Seeded
from CPCB table (§4.2), marked **[VERIFY]** in `verification-log.md`. Loaded by `AqiCalculator`.

### `shedlock` — scheduler lock (FR-1.6)
Standard ShedLock table.

## 3. Flag storage representation (design obligation §9.3)

**Bitmask `int` column `qc_flags`** on `measurement`. Rationale vs alternatives:
- vs **join table**: a per-flag row would multiply the largest table 1–7× and force a join on
  every read — fatal at 10⁹ rows.
- vs **text[] array**: arrays cost more storage and GIN indexes; we rarely query "rows with flag
  X" on raw data (that's an availability rollup concern), we mostly *return* flags with rows.
- A bitmask is 4 bytes, trivially compressible, and `qc_flags & MASK_STUCK <> 0` is index-free but
  cheap; availability rollups precompute "unflagged" counts so they never scan raw flags.

Bit positions (stable, append-only): `NEGATIVE=1, ZERO_SUSPECT=2, SENTINEL=4, STUCK=8, SPIKE=16,
RANGE=32, DUPLICATE_SOURCE=64`. Enum + bit defined in `qc.QcFlag`.

## 4. Continuous aggregates & availability (FR-3.3, NFR-1)

Two continuous aggregates over `measurement` (created in a migration; refreshed on a policy):
- `measurement_daily` — per (station, pollutant, day): mean/min/max/count of **unflagged** values
  + expected-interval count → availability %.
- `measurement_monthly` — rolled from daily.

Availability % = unflagged intervals ÷ expected intervals (§4.4). Materialized per
(station, pollutant) month and year. The API's `interval=day|month` reads these, never raw
(NFR-1/2).

## 5. Migration ordering

```
V1__extensions.sql            timescaledb, postgis, pgcrypto
V2__core_tables.sql           pollutant, station, station_alias, ingestion_run, api_client, qc_config, aqi_breakpoint, shedlock
V3__measurement_hypertable.sql measurement table + create_hypertable + indexes + compression/retention policies
V4__city_daily_aqi.sql        city_daily_aqi
V5__continuous_aggregates.sql measurement_daily/monthly + refresh policies
V6__seed_reference.sql        pollutants, aqi breakpoints, default qc ruleset, demo api key
```

Continuous-aggregate and hypertable DDL can't run inside Flyway's wrapping transaction; those
migrations are marked non-transactional via Flyway config / `BEGIN;COMMIT` discipline.
