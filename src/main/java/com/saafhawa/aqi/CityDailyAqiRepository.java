package com.saafhawa.aqi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface CityDailyAqiRepository extends JpaRepository<CityDailyAqi, Long> {

    List<CityDailyAqi> findByCityIgnoreCaseAndAqiDateBetweenOrderByAqiDateAsc(
            String city, LocalDate from, LocalDate to);

    @Query("SELECT DISTINCT c.city FROM CityDailyAqi c ORDER BY c.city ASC")
    List<String> distinctCities();

    @Query("SELECT max(c.aqiDate) FROM CityDailyAqi c")
    LocalDate maxDate();

    /** Idempotent upsert on the natural key {@code (city, aqi_date, source)} (FR-1.4). */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO city_daily_aqi (city, state, aqi_date, aqi, prominent_pollutant, source, raw_ref, qc_flags)
            VALUES (:city, :state, :aqiDate, :aqi, :prominentPollutant, :source, :rawRef, 0)
            ON CONFLICT (city, aqi_date, source) DO UPDATE SET
                aqi = EXCLUDED.aqi,
                prominent_pollutant = EXCLUDED.prominent_pollutant,
                state = COALESCE(EXCLUDED.state, city_daily_aqi.state),
                raw_ref = EXCLUDED.raw_ref,
                ingested_at = now()
            """, nativeQuery = true)
    void upsert(@Param("city") String city, @Param("state") String state, @Param("aqiDate") LocalDate aqiDate,
               @Param("aqi") Integer aqi, @Param("prominentPollutant") String prominentPollutant,
               @Param("source") String source, @Param("rawRef") String rawRef);
}
