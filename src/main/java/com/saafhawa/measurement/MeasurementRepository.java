package com.saafhawa.measurement;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Hypertable access via JdbcTemplate (db-design.md §1). Writes use a natural-key upsert for
 * idempotency (FR-1.4); reads hit the raw table or continuous aggregates.
 */
@Repository
public class MeasurementRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MeasurementRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A measurement ready to persist. */
    public record Upsert(String stationId, String pollutant, Instant intervalStart, int intervalSeconds,
                         Double value, Double valueMin, Double valueMax, String source, String rawRef,
                         int qcFlags, String qcRuleset, Integer reportedAqi) {
    }

    /** Trailing 8h and 24h means (with hour-counts) for one (station, pollutant). */
    public record WindowedAvgRow(String stationId, String pollutant,
                                 Double avg8, int cnt8, Double avg24, int cnt24, Instant lastSeen) {
    }

    /**
     * Idempotent upsert on the natural key (FR-1.4). Returns true if the row was newly inserted,
     * false if an existing row was updated (detected via {@code xmax = 0}).
     */
    public boolean upsert(Upsert m) {
        String sql = """
                INSERT INTO measurement (station_id, pollutant, interval_start, interval_seconds,
                    value, value_min, value_max, source, raw_ref, qc_flags, qc_ruleset, reported_aqi)
                VALUES (:stationId, :pollutant, :intervalStart, :intervalSeconds,
                    :value, :valueMin, :valueMax, :source, :rawRef, :qcFlags, :qcRuleset, :reportedAqi)
                ON CONFLICT (station_id, pollutant, interval_start, interval_seconds, source)
                DO UPDATE SET value = EXCLUDED.value, value_min = EXCLUDED.value_min,
                    value_max = EXCLUDED.value_max, raw_ref = EXCLUDED.raw_ref,
                    qc_flags = EXCLUDED.qc_flags, qc_ruleset = EXCLUDED.qc_ruleset,
                    reported_aqi = EXCLUDED.reported_aqi, ingested_at = now()
                RETURNING (xmax = 0) AS inserted
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("stationId", m.stationId())
                .addValue("pollutant", m.pollutant())
                .addValue("intervalStart", Timestamp.from(m.intervalStart()))
                .addValue("intervalSeconds", m.intervalSeconds())
                .addValue("value", m.value())
                .addValue("valueMin", m.valueMin())
                .addValue("valueMax", m.valueMax())
                .addValue("source", m.source())
                .addValue("rawRef", m.rawRef())
                .addValue("qcFlags", m.qcFlags())
                .addValue("qcRuleset", m.qcRuleset())
                .addValue("reportedAqi", m.reportedAqi());
        Boolean inserted = jdbc.queryForObject(sql, p, Boolean.class);
        return Boolean.TRUE.equals(inserted);
    }

    /** Raw measurements with keyset pagination (FR-4.4). flaggedMode: ALL|UNFLAGGED|FLAGGED_ONLY. */
    public List<MeasurementRow> queryRaw(List<String> stations, List<String> pollutants,
                                         Instant from, Instant to, String flaggedMode,
                                         MeasurementCursor cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT station_id, pollutant, interval_start, interval_seconds, value, value_min,
                       value_max, source, qc_flags, qc_ruleset, reported_aqi
                FROM measurement
                WHERE station_id IN (:stations) AND pollutant IN (:pollutants)
                  AND interval_start >= :from AND interval_start < :to
                """);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("stations", stations)
                .addValue("pollutants", pollutants)
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to))
                .addValue("limit", limit);
        appendFlagFilter(sql, flaggedMode);
        if (cursor != null) {
            sql.append(" AND (interval_start, station_id, pollutant) > (:cTs, :cStation, :cPoll)");
            p.addValue("cTs", Timestamp.from(cursor.intervalStart()))
                    .addValue("cStation", cursor.stationId())
                    .addValue("cPoll", cursor.pollutant());
        }
        sql.append(" ORDER BY interval_start ASC, station_id ASC, pollutant ASC LIMIT :limit");
        return jdbc.query(sql.toString(), p, (rs, i) -> new MeasurementRow(
                rs.getString("station_id"), rs.getString("pollutant"),
                rs.getTimestamp("interval_start").toInstant(), rs.getInt("interval_seconds"),
                (Double) rs.getObject("value"), (Double) rs.getObject("value_min"),
                (Double) rs.getObject("value_max"), rs.getString("source"),
                rs.getInt("qc_flags"), rs.getString("qc_ruleset"),
                (Integer) rs.getObject("reported_aqi")));
    }

    /** Aggregated rows from a continuous aggregate (interval=day|month). */
    public List<AggregateRow> queryAggregate(String view, List<String> stations,
                                             List<String> pollutants, Instant from, Instant to, int limit) {
        String sql = """
                SELECT station_id, pollutant, bucket, mean_value, min_value, max_value, cnt, unflagged_cnt
                FROM %s
                WHERE station_id IN (:stations) AND pollutant IN (:pollutants)
                  AND bucket >= :from AND bucket < :to
                ORDER BY bucket ASC, station_id ASC, pollutant ASC
                LIMIT :limit
                """.formatted(view);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("stations", stations)
                .addValue("pollutants", pollutants)
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to))
                .addValue("limit", limit);
        return jdbc.query(sql, p, (rs, i) -> new AggregateRow(
                rs.getString("station_id"), rs.getString("pollutant"),
                rs.getTimestamp("bucket").toInstant(),
                (Double) rs.getObject("mean_value"), (Double) rs.getObject("min_value"),
                (Double) rs.getObject("max_value"), rs.getLong("cnt"), rs.getLong("unflagged_cnt")));
    }

    /** Most recent value per (station, pollutant) for the given stations (FR-4.6). */
    public List<LatestRow> latestForStations(List<String> stations) {
        if (stations.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT DISTINCT ON (station_id, pollutant)
                       station_id, pollutant, interval_start, value, source, qc_flags
                FROM measurement
                WHERE station_id IN (:stations)
                ORDER BY station_id, pollutant, interval_start DESC
                """;
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("stations", stations);
        return jdbc.query(sql, p, (rs, i) -> new LatestRow(
                rs.getString("station_id"), rs.getString("pollutant"),
                rs.getTimestamp("interval_start").toInstant(), (Double) rs.getObject("value"),
                rs.getString("source"), rs.getInt("qc_flags")));
    }

    /**
     * Trailing-window mean concentration per (station, pollutant) for CPCB-method AQI (§4.2).
     * For each pair we return both the 8-hour and 24-hour means with their hour-counts, so the
     * caller can pick the window CPCB prescribes for that pollutant (8h for CO/O3, 24h otherwise)
     * and enforce a minimum-completeness rule. Values carrying any QC flag in {@code rejectMask}
     * (and nulls) are excluded from the averages.
     */
    public List<WindowedAvgRow> trailingAverages(List<String> stations, Instant now, int rejectMask) {
        if (stations.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT station_id, pollutant,
                       avg(value)   FILTER (WHERE interval_start >= :since8)  AS avg8,
                       count(value) FILTER (WHERE interval_start >= :since8)  AS cnt8,
                       avg(value)   AS avg24,
                       count(value) AS cnt24,
                       max(interval_start) AS last_seen
                FROM measurement
                WHERE station_id IN (:stations)
                  AND interval_start >= :since24
                  AND value IS NOT NULL
                  AND (qc_flags & :rejectMask) = 0
                GROUP BY station_id, pollutant
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("stations", stations)
                .addValue("since24", Timestamp.from(now.minus(Duration.ofHours(24))))
                .addValue("since8", Timestamp.from(now.minus(Duration.ofHours(8))))
                .addValue("rejectMask", rejectMask);
        return jdbc.query(sql, p, (rs, i) -> new WindowedAvgRow(
                rs.getString("station_id"), rs.getString("pollutant"),
                (Double) rs.getObject("avg8"), rs.getInt("cnt8"),
                (Double) rs.getObject("avg24"), rs.getInt("cnt24"),
                rs.getTimestamp("last_seen").toInstant()));
    }

    /** Distinct stations reporting any measurement since the given instant (FR-7.2). */
    public long countStationsReportingSince(Instant since) {
        Long n = jdbc.queryForObject(
                "SELECT count(DISTINCT station_id) FROM measurement WHERE interval_start >= :since",
                new MapSqlParameterSource("since", Timestamp.from(since)), Long.class);
        return n == null ? 0 : n;
    }

    private void appendFlagFilter(StringBuilder sql, String flaggedMode) {
        switch (flaggedMode) {
            case "UNFLAGGED" -> sql.append(" AND qc_flags = 0");
            case "FLAGGED_ONLY" -> sql.append(" AND qc_flags <> 0");
            default -> {
                // ALL: no filter
            }
        }
    }
}
