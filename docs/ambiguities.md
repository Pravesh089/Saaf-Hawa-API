# Ambiguities found in the FDD (with recommended defaults)

Per FDD §0.1.3. Each is resolved with a default so implementation can proceed; flagged for user
confirmation.

1. **Module packaging — single Maven module vs multi-module.** *Default chosen:* single Maven
   module, packages-as-modules with an enforced dependency direction (see architecture.md §1).
   Rationale: solo maintainer, faster builds; boundaries still clean. Revisit if extraction needed.

2. **Object store in dev — MinIO vs filesystem.** *Default:* MinIO via docker compose (S3 API),
   with a `filesystem` archive profile for tests/CI so no container is needed for unit tests.

3. **AQI band for missing/edge concentrations.** *Default:* a concentration above the top
   breakpoint clamps to index 500 and sets a `RANGE`-style note; a pollutant with no breakpoint
   row contributes no sub-index. Documented in AqiCalculator.

4. **Anonymous rate limit unit.** FDD says "30 req/h" anonymous and "60 req/min" keyed.
   *Default:* implement exactly that — anonymous bucket = 30 tokens/hour, keyed = 60 tokens/minute,
   per-key override column. Headers per FR-6.2.

5. **Station ID state code source.** *Default:* derive 2-letter code from a built-in
   Indian-state→code map (`StateCodes`); unknown/none → `XX`. Reviewable in catalog.

6. **`interval=raw` cap & pagination.** *Default:* 50k rows/response (FR-4.4), opaque cursor =
   base64 of `(interval_start, station, pollutant)` of the last row; `next` null when exhausted.

7. **Computed vs reported AQI in `/v1/aqi/cities`.** *Default:* return both fields
   (`aqi_reported`, `aqi_computed`) where available; never collapse them (FDD §4.2).

8. **Self-computed city AQI from stations.** *Default (M1):* expose bulletin-sourced city AQI;
   station-derived city AQI requires 24h rollups (M3) and is returned as `null` with a
   `note` until then. Tracked in traceability.

9. **Admin auth.** *Default:* static bearer token from env `SAAFHAWA_ADMIN_TOKEN` (FR-7.1 v1).
