-- Reference / seed data (db-design.md §5). AQI breakpoints are [VERIFY] (verification-log.md V4).

INSERT INTO pollutant (code, display_name, unit, zero_implausible, range_min, range_max) VALUES
    ('PM2.5', 'Particulate Matter < 2.5um', 'ug/m3', TRUE,  0, 2000),
    ('PM10',  'Particulate Matter < 10um',  'ug/m3', TRUE,  0, 3000),
    ('NO2',   'Nitrogen Dioxide',           'ug/m3', FALSE, 0, 1000),
    ('SO2',   'Sulphur Dioxide',            'ug/m3', FALSE, 0, 2000),
    ('CO',    'Carbon Monoxide',            'mg/m3', FALSE, 0,  100),
    ('O3',    'Ozone',                      'ug/m3', FALSE, 0, 1000),
    ('NH3',   'Ammonia',                    'ug/m3', FALSE, 0, 3000),
    ('Pb',    'Lead',                       'ug/m3', FALSE, 0,   20);

-- CPCB AQI breakpoints (FDD §4.2). conc_high of the top band is an open-ended sentinel.
INSERT INTO aqi_breakpoint (pollutant, band_low_index, band_high_index, conc_low, conc_high, avg_hours) VALUES
    ('PM2.5',  0,  50,    0,    30, 24), ('PM2.5', 51, 100,   31,    60, 24),
    ('PM2.5',101, 200,   61,    90, 24), ('PM2.5',201, 300,   91,   120, 24),
    ('PM2.5',301, 400,  121,   250, 24), ('PM2.5',401, 500,  251,100000, 24),

    ('PM10',   0,  50,    0,    50, 24), ('PM10',  51, 100,   51,   100, 24),
    ('PM10', 101, 200,  101,   250, 24), ('PM10', 201, 300,  251,   350, 24),
    ('PM10', 301, 400,  351,   430, 24), ('PM10', 401, 500,  431,100000, 24),

    ('NO2',    0,  50,    0,    40, 24), ('NO2',   51, 100,   41,    80, 24),
    ('NO2',  101, 200,   81,   180, 24), ('NO2',  201, 300,  181,   280, 24),
    ('NO2',  301, 400,  281,   400, 24), ('NO2',  401, 500,  401,100000, 24),

    ('SO2',    0,  50,    0,    40, 24), ('SO2',   51, 100,   41,    80, 24),
    ('SO2',  101, 200,   81,   380, 24), ('SO2',  201, 300,  381,   800, 24),
    ('SO2',  301, 400,  801,  1600, 24), ('SO2',  401, 500, 1601,100000, 24),

    ('CO',     0,  50,    0,   1.0,  8), ('CO',    51, 100,  1.1,   2.0,  8),
    ('CO',   101, 200,  2.1,    10,  8), ('CO',   201, 300, 10.1,    17,  8),
    ('CO',   301, 400, 17.1,    34,  8), ('CO',   401, 500, 34.1,100000,  8),

    ('O3',     0,  50,    0,    50,  8), ('O3',    51, 100,   51,   100,  8),
    ('O3',   101, 200,  101,   168,  8), ('O3',   201, 300,  169,   208,  8),
    ('O3',   301, 400,  209,   748,  8), ('O3',   401, 500,  749,100000,  8),

    ('NH3',    0,  50,    0,   200, 24), ('NH3',   51, 100,  201,   400, 24),
    ('NH3',  101, 200,  401,   800, 24), ('NH3',  201, 300,  801,  1200, 24),
    ('NH3',  301, 400, 1201,  1800, 24), ('NH3',  401, 500, 1801,100000, 24),

    ('Pb',     0,  50,    0,   0.5, 24), ('Pb',    51, 100,  0.6,   1.0, 24),
    ('Pb',   101, 200,  1.1,   2.0, 24), ('Pb',   201, 300,  2.1,   3.0, 24),
    ('Pb',   301, 400,  3.1,   3.5, 24), ('Pb',   401, 500,  3.6,100000, 24);

-- Active QC ruleset (FR-3.2). Thresholds tunable without code change (FDD §4.4).
INSERT INTO qc_config (ruleset_version, key, value, active) VALUES
    ('2026.06', 'sentinel.values',     '999,9999',  TRUE),
    ('2026.06', 'spike.k',             '4',         TRUE),
    ('2026.06', 'spike.percentile',    '95',        TRUE),
    ('2026.06', 'spike.window_days',   '30',        TRUE),
    ('2026.06', 'stuck.min_intervals', '24',        TRUE);

-- Demo API key for `docker compose up` + README quickstart (NFR-3, AC-M4).
-- Raw key: "saafhawa-demo-key" (documented in README; never use in production).
INSERT INTO api_client (key_hash, email, tier, rate_limit_override) VALUES
    (encode(digest('saafhawa-demo-key', 'sha256'), 'hex'), 'demo@saafhawa.local', 'PARTNER', 6000);
