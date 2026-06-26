# Verification Log (M0 — AC-M0)

Each `[VERIFY]` item from the FDD, its status, and evidence. Items not yet confirmable against a
live source in this environment are marked **PENDING** with the plan to resolve them; the adapter
is written defensively so either resolution works.

| # | FDD ref | Item | Status | Evidence / note |
|---|---|---|---|---|
| V1 | §5.1 | data.gov.in resource id `3b01bcb8-0b14-4abf-b6f2-c1bfd384ba69` live | PENDING | Requires a valid `data.gov.in` API key (env `DATAGOVIN_API_KEY`). Adapter targets this id and is config-overridable via `saafhawa.ingest.datagovin.resource-id`. Confirm by a sample fetch in M1. |
| V2 | §5.1 | Record field names (`pollutant_id` values, `pollutant_avg` vs `avg_value`) | RESOLVED-DEFENSIVE | Parser accepts both `pollutant_avg/min/max` and `avg_value/min_value/max_value`, and maps `OZONE→O3`. See `DataGovInParser`. |
| V3 | §4.4 | Observed sentinel values {999, 9999, 985…} | PENDING (config) | Encoded in `qc_config` key `sentinel.values` default `999,9999`; tune against real M1 data without code change (FR-3.2). |
| V4 | §4.2 | CPCB AQI breakpoints | SEEDED-FROM-FDD | Seeded into `aqi_breakpoint` exactly as the FDD §4.2 table. Must be re-checked against the official CPCB "National AQI" report before release; flagged here as the authoritative TODO. |
| V5 | §4.1 | Pollutant units (CO mg/m³, others µg/m³) | SEEDED-FROM-FDD | Seeded in `pollutant`. Per-source unit confirmation deferred to each adapter. |
| V6 | §5.2 | urbanemissions `AQI_bulletins` coverage end + CSV schema | RESOLVED | Source is `github.com/urbanemissionsinfo/AQI_bulletins`, file `data/Processed/AllIndiaBulletins_<year>.csv`, columns `date,City,No. Stations,Air Quality,Index Value,Prominent Pollutant`. It's a sporadically-updated community dump (most recent commit Jan 2026; last row observed mid-2025), not a live feed — useful for historical backfill only. See `UrbanEmissionsAdapter`/`UrbanEmissionsParser`. |
| V7 | §5.2 | CPCB daily bulletin URL pattern | STILL DEFERRED | No stable historical-archive URL pattern found for CPCB's own `cpcb.nic.in/aqi_bulletin.php` (daily PDF only, no confirmed per-date query). Live/current city AQI is instead served by station-computed `/v1/aqi/stations`; revisit only if a confirmed CPCB archive endpoint surfaces. |
| V8 | §5.3 | OpenAQ S3 archive bucket name/layout | DEFERRED-M3 | Out of M1 scope. |

## Network check (this environment)

`curl https://api.openaq.org/v3` → HTTP 403 (reachable; needs key). data.gov.in not exercised
without a key. The build and tests do not require live upstream access — adapter tests replay
archived fixture payloads (NFR-5).
