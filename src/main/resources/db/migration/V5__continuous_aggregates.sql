-- Continuous aggregates for hour->day->month rollups + retention/compression policies
-- (db-design.md §4). Runs with executeInTransaction=false (see .conf): Timescale forbids
-- creating continuous aggregates or adding background-job policies inside a transaction.

CREATE MATERIALIZED VIEW measurement_daily
WITH (timescaledb.continuous) AS
SELECT station_id,
       pollutant,
       time_bucket(INTERVAL '1 day', interval_start) AS bucket,
       avg(value)  AS mean_value,
       min(value)  AS min_value,
       max(value)  AS max_value,
       count(value) AS cnt,
       sum(CASE WHEN qc_flags = 0 THEN 1 ELSE 0 END) AS unflagged_cnt
FROM measurement
GROUP BY station_id, pollutant, bucket
WITH NO DATA;

CREATE MATERIALIZED VIEW measurement_monthly
WITH (timescaledb.continuous) AS
SELECT station_id,
       pollutant,
       time_bucket(INTERVAL '1 month', interval_start) AS bucket,
       avg(value)  AS mean_value,
       min(value)  AS min_value,
       max(value)  AS max_value,
       count(value) AS cnt,
       sum(CASE WHEN qc_flags = 0 THEN 1 ELSE 0 END) AS unflagged_cnt
FROM measurement
GROUP BY station_id, pollutant, bucket
WITH NO DATA;

-- Keep aggregates fresh (FR-3.3). Offsets chosen so recently-ingested data refreshes promptly.
SELECT add_continuous_aggregate_policy('measurement_daily',
    start_offset => INTERVAL '3 days', end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');

SELECT add_continuous_aggregate_policy('measurement_monthly',
    start_offset => INTERVAL '3 months', end_offset => INTERVAL '1 day',
    schedule_interval => INTERVAL '6 hours');

-- Compress raw chunks older than 30 days (disk-growth mitigation, FDD §12).
SELECT add_compression_policy('measurement', INTERVAL '30 days');
