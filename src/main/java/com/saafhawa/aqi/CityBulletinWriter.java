package com.saafhawa.aqi;

import com.saafhawa.ingest.spi.CityBulletinRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Types;
import java.util.List;

/**
 * Bulk idempotent upsert of city-day bulletin rows into {@code city_daily_aqi} (FR-1.4). A
 * historical backfill is hundreds of thousands of rows, so writes go through chunked JDBC
 * {@code batchUpdate} rather than per-row JPA calls. Conflicts on the natural key
 * {@code (city, aqi_date, source)} refresh the AQI value and provenance.
 */
@Repository
public class CityBulletinWriter {

    private static final int BATCH_SIZE = 1000;

    private static final String UPSERT_SQL = """
            INSERT INTO city_daily_aqi (city, state, aqi_date, aqi, prominent_pollutant, source, raw_ref, qc_flags)
            VALUES (:city, :state, :aqiDate, :aqi, :prominentPollutant, :source, :rawRef, 0)
            ON CONFLICT (city, aqi_date, source) DO UPDATE SET
                aqi = EXCLUDED.aqi,
                prominent_pollutant = EXCLUDED.prominent_pollutant,
                state = COALESCE(EXCLUDED.state, city_daily_aqi.state),
                raw_ref = EXCLUDED.raw_ref,
                ingested_at = now()
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CityBulletinWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Upserts all rows in chunked batches; returns the number of rows processed. */
    public int upsertAll(List<CityBulletinRow> rows, String source, String rawRef) {
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<CityBulletinRow> chunk = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            SqlParameterSource[] params = new SqlParameterSource[chunk.size()];
            for (int i = 0; i < chunk.size(); i++) {
                CityBulletinRow r = chunk.get(i);
                params[i] = new MapSqlParameterSource()
                        .addValue("city", r.city())
                        .addValue("state", r.state(), Types.VARCHAR)
                        .addValue("aqiDate", Date.valueOf(r.aqiDate()))
                        .addValue("aqi", r.aqi(), Types.INTEGER)
                        .addValue("prominentPollutant", r.prominentPollutant(), Types.VARCHAR)
                        .addValue("source", source)
                        .addValue("rawRef", rawRef, Types.VARCHAR);
            }
            jdbc.batchUpdate(UPSERT_SQL, params);
        }
        return rows.size();
    }
}
