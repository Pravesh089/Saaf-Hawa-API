# Operations

## Configuration (12-factor, NFR-6)

All config is environment-driven (see `application.yml` for the full list). Key variables:

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | PostgreSQL connection | local dev values |
| `DATAGOVIN_API_KEY` | data.gov.in API key (§5.1) — **required** for live ingestion | empty (poll fails gracefully) |
| `DATAGOVIN_CRON` | poll schedule; `-` disables | `0 5 * * * *` (hourly) |
| `ARCHIVE_TYPE` | `filesystem` or `s3` | `filesystem` |
| `S3_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` | object store for raw archive (G5) | MinIO dev values |
| `SAAFHAWA_ADMIN_TOKEN` | static admin bearer token (FR-7.1) | empty (admin disabled) |
| `RATE_ANON_PER_HOUR` / `RATE_KEYED_PER_MIN` | rate limits (FR-6.1) | 30 / 60 |
| `ALERT_WEBHOOK_URL` | ops alert webhook (FR-1.2) | empty (logs only) |

## Secrets

Never commit secrets. The data.gov.in key and admin token come from the environment only. The
demo API key (`saafhawa-demo-key`) seeded by Flyway is for local `docker compose` only.

## Backups (NFR-7)

- **Database:** nightly `pg_dump -Fc saafhawa` to off-host storage; for PITR enable WAL archiving
  (`archive_mode=on`, `archive_command` to the object store). Continuous aggregates and hypertable
  chunks are included in a logical dump.
- **Ultimate recovery path (G5):** the raw-payload archive. Every upstream payload is stored gzip
  in the object store before parsing, so the entire dataset can be rebuilt by replaying archived
  payloads through the adapters (`POST /admin/ingest/{source}` or the reprocessing path). This is
  the reproducibility guarantee — the DB is a derived artifact.

## Logging

Logs are human-readable by default. For JSON/structured logs in production, add a
`logback-spring.xml` with `logstash-logback-encoder` (or upgrade to Spring Boot 3.4+ and set
`logging.structured.format.console=ecs`). Tracked as the NFR-6 JSON-logging follow-up.

## Metrics (FR-7.3)

Prometheus scrape endpoint: `/actuator/prometheus`. Custom counters: `saafhawa.ingest.inserted`,
`saafhawa.ingest.rejected`, `saafhawa.ingest.failed`, `saafhawa.api.ratelimit.hits`.

## Scheduled jobs

- data.gov.in poll: hourly (`DATAGOVIN_CRON`), guarded by ShedLock so a second node never
  double-fetches (FR-1.6).
- Context QC (STUCK/SPIKE) and availability rollups: M3.

## Running locally

```bash
docker compose up --build          # app + TimescaleDB/PostGIS + MinIO, seeded
# then:
curl localhost:8080/v1/status
open  localhost:8080/docs          # Swagger UI
```

To enable live ingestion, set `DATAGOVIN_API_KEY` (free from data.gov.in) and either wait for the
hourly poll or trigger a run:

```bash
curl -X POST localhost:8080/admin/ingest/cpcb-datagovin \
  -H "Authorization: Bearer $SAAFHAWA_ADMIN_TOKEN"
```
