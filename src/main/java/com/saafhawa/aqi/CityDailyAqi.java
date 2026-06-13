package com.saafhawa.aqi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** City-level daily AQI (bulletin-sourced history layer, §4.1). Distinct from station data. */
@Entity
@Table(name = "city_daily_aqi")
public class CityDailyAqi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String city;
    private String state;
    @Column(name = "aqi_date")
    private LocalDate aqiDate;
    private Integer aqi;
    @Column(name = "prominent_pollutant")
    private String prominentPollutant;
    private String source;
    @Column(name = "raw_ref")
    private String rawRef;
    @Column(name = "qc_flags")
    private int qcFlags;
    @Column(name = "ingested_at")
    private Instant ingestedAt = Instant.now();

    protected CityDailyAqi() {
    }

    public Long getId() {
        return id;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public LocalDate getAqiDate() {
        return aqiDate;
    }

    public Integer getAqi() {
        return aqi;
    }

    public String getProminentPollutant() {
        return prominentPollutant;
    }

    public String getSource() {
        return source;
    }
}
