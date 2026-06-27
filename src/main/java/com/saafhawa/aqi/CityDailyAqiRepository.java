package com.saafhawa.aqi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface CityDailyAqiRepository extends JpaRepository<CityDailyAqi, Long> {

    List<CityDailyAqi> findByCityIgnoreCaseAndAqiDateBetweenOrderByAqiDateAsc(
            String city, LocalDate from, LocalDate to);

    @Query("SELECT DISTINCT c.city FROM CityDailyAqi c ORDER BY c.city ASC")
    List<String> distinctCities();

    @Query("SELECT max(c.aqiDate) FROM CityDailyAqi c")
    LocalDate maxDate();
}
