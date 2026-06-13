-- The core time-series table as a TimescaleDB hypertable (db-design.md §1).

CREATE TABLE measurement (
    station_id       TEXT NOT NULL REFERENCES station(id),
    pollutant        TEXT NOT NULL REFERENCES pollutant(code),
    interval_start   TIMESTAMPTZ NOT NULL,
    interval_seconds INTEGER NOT NULL,
    value            DOUBLE PRECISION,
    value_min        DOUBLE PRECISION,
    value_max        DOUBLE PRECISION,
    source           TEXT NOT NULL,
    raw_ref          TEXT,
    qc_flags         INTEGER NOT NULL DEFAULT 0,   -- bitmask, see qc.QcFlag
    qc_ruleset       TEXT,
    reported_aqi     INTEGER,
    ingested_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Natural key / dedupe (FR-1.4, §4.3). Includes interval_start so it works on a hypertable.
    CONSTRAINT uq_measurement UNIQUE (station_id, pollutant, interval_start, interval_seconds, source)
);

SELECT create_hypertable('measurement', 'interval_start', chunk_time_interval => INTERVAL '7 days');

-- Primary read pattern: range over time for a station+pollutant (NFR-2).
CREATE INDEX idx_measurement_station_poll_time
    ON measurement (station_id, pollutant, interval_start DESC);

-- Enable columnar compression; the policy that schedules it is added in V5
-- (policy functions must run outside Flyway's transaction).
ALTER TABLE measurement SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'station_id, pollutant',
    timescaledb.compress_orderby = 'interval_start DESC'
);
