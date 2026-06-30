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
| V6 | §5.2 | urbanemissions `AQI_bulletins` coverage end + CSV schema | RESOLVED | Source is `github.com/urbanemissionsinfo/AQI_bulletins`, dir `data/Processed`. Data is split across inconsistently-named whole-file dumps with **two schemas**: a clean form `date,City,No. Stations,Air Quality,Index Value,Prominent Pollutant` (e.g. `AllIndiaBulletins_Master2025.csv`, `AllIndiaBulletins_2025.csv`) and an "openrefined" form that prepends a row-index column and appends `year` (`AllIndiaBulletins_Master_2024.csv`, which covers 2015-05 → 2024-12). Coverage: `Master_2024` = 2015–2024, the two 2025 files = 2025. Sporadically updated (last rows observed end-2025), not a live feed — historical backfill only. `UrbanEmissionsParser` maps columns by header name and re-syncs per embedded header, so both schemas parse in one concatenated payload. |
| V7 | §5.2 | CPCB daily bulletin URL pattern | STILL DEFERRED | No stable historical-archive URL pattern found for CPCB's own `cpcb.nic.in/aqi_bulletin.php` (daily PDF only, no confirmed per-date query). Live/current city AQI is instead served by station-computed `/v1/aqi/stations`; revisit only if a confirmed CPCB archive endpoint surfaces. |
| V8 | §5.3 | OpenAQ S3 archive bucket name/layout | DEFERRED-M3 | Out of M1 scope. |
| V9 | §4.2 | CO reported in µg/m³ by data.gov.in vs CPCB CO breakpoints in mg/m³ | RESOLVED-DEFENSIVE | Observed: every pollutant in the feed is µg/m³, but CPCB expresses CO breakpoints in mg/m³ (Severe ≥34 mg/m³). Raw Delhi CO values (14–94) are normal as µg/m³ but pinned CO's sub-index to ~500 when read as mg/m³, making CO spuriously drive AQI at 44/45 stations. `AqiService.normalizeUnits` divides CO by 1000 before applying breakpoints. If a station is ever found reporting CO already in mg/m³, this needs a per-source guard. |

## Network check (this environment)

`curl https://api.openaq.org/v3` → HTTP 403 (reachable; needs key). data.gov.in not exercised
without a key. The build and tests do not require live upstream access — adapter tests replay
archived fixture payloads (NFR-5).
