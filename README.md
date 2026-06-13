# Saaf Hawa API

**A historical + real-time Indian air-quality data service with first-class data-quality flags.**

India's official air-quality data (CPCB / data.gov.in) is operationally hard to use: no bulk or
historical API, no quality-control metadata, and frequent downtime. Saaf Hawa ("clean air")
continuously ingests official sources into a permanent, normalized time-series, computes
transparent QC flags, and serves it all through one documented, rate-limited, free REST API.

This repository implements **Milestone M1**: the data.gov.in live-feed pipeline (ingest → canonical
store → QC → public API), runnable with one command. See [`docs/`](docs) for the full design and
[`docs/traceability.md`](docs/traceability.md) for what's done vs deferred to M2–M4.

## Quickstart (≈2 minutes)

Requires Docker.

```bash
docker compose up --build
```

This starts the API, TimescaleDB (+ PostGIS), and MinIO, runs the database migrations, and seeds
reference data (pollutants, AQI breakpoints, QC ruleset, a demo API key). Then:

```bash
# Service health + upstream freshness
curl localhost:8080/v1/status

# Interactive API docs (OpenAPI 3 / Swagger UI)
open http://localhost:8080/docs

# QC methodology, generated from the live config
curl localhost:8080/v1/qc/methodology
```

To pull live data, set a free [data.gov.in](https://data.gov.in) API key and trigger a run:

```bash
export DATAGOVIN_API_KEY=your-key-here   # then `docker compose up` picks it up
curl -X POST localhost:8080/admin/ingest/cpcb-datagovin \
  -H "Authorization: Bearer dev-admin-token"
```

Without a key the service still runs and serves seeded reference data; the scheduled poll logs a
clear "not configured" message and the ledger records the failed run.

## Using the API

All endpoints are versioned under `/v1`. Send your API key as the `X-API-Key` header (anonymous
access is allowed at a lower rate limit). Get a key:

```bash
curl -X POST localhost:8080/v1/keys -H 'Content-Type: application/json' -d '{"email":"you@example.com"}'
```

Key endpoints:

| Endpoint | Description |
|---|---|
| `GET /v1/stations?state=&city=&bbox=&format=json\|geojson` | List/filter stations |
| `GET /v1/stations/{id}` | Station metadata |
| `GET /v1/stations/nearest?lat=&lon=&limit=` | Nearest stations (PostGIS) |
| `GET /v1/measurements?station=\|city=&pollutant=&from=&to=&interval=raw\|day\|month&include_flagged=&format=json\|csv` | Time-series, server-side aggregated, with QC flags |
| `GET /v1/latest?station=\|city=` | Most recent values per pollutant + age in minutes |
| `GET /v1/aqi/cities` , `GET /v1/aqi/cities/{city}?from=&to=` | City daily AQI (bulletin history) |
| `GET /v1/status` | Freshness / health (watchdog on CPCB uptime) |
| `GET /v1/qc/methodology` | Human-readable QC rules from the active config |
| `GET /docs` | OpenAPI 3 / Swagger UI |

Example:

```bash
curl "localhost:8080/v1/measurements?station=IN-DL-0001&pollutant=PM2.5&from=2026-01-01&to=2026-01-31&interval=day"
```

Every measurement carries machine-readable QC flags (`NEGATIVE`, `ZERO_SUSPECT`, `SENTINEL`,
`STUCK`, `SPIKE`, `RANGE`, `DUPLICATE_SOURCE`). Raw values are **never** silently dropped or
"fixed" — flags are additive (project goal G3). Errors are RFC-7807 `application/problem+json`.

## Architecture at a glance

A modular monolith (Java 21 / Spring Boot 3.3): packages `catalog`, `ingest` (adapter SPI +
data.gov.in source), `qc`, `aqi`, `measurement`, `api`, `ops`. Time-series lives in a TimescaleDB
hypertable with continuous aggregates for day/month rollups; raw upstream payloads are archived to
object storage *before* parsing so the dataset is fully reproducible (G5). Full rationale in
[`docs/architecture.md`](docs/architecture.md) and [`docs/db-design.md`](docs/db-design.md).

## Development

```bash
mvn test                 # unit tests (AQI, QC, parser) + Testcontainers integration tests
mvn spring-boot:run      # needs a local Postgres/Timescale (see docker-compose.yml for one)
```

Integration tests use Testcontainers and require Docker. On very recent Docker Engines, the
docker-java client API version is pinned in `pom.xml` (`docker.api.version`).

## Data attribution & license

Code under the MIT License (see `LICENSE`). Data outputs derive from CPCB via data.gov.in
(National Data Sharing and Accessibility Policy / OGD) and, for historical AQI, from
UrbanEmissions.info — attributed in API responses (`meta.attribution`) and intended for release as
CC-BY-4.0 with upstream attribution.
