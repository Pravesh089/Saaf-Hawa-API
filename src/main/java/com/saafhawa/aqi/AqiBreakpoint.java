package com.saafhawa.aqi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** One CPCB AQI breakpoint band, loaded from data (§4.2, §9.6). */
@Entity
@Table(name = "aqi_breakpoint")
@IdClass(AqiBreakpoint.Key.class)
public class AqiBreakpoint {

    @Id
    private String pollutant;
    @Id
    @Column(name = "band_low_index")
    private int bandLowIndex;
    @Column(name = "band_high_index")
    private int bandHighIndex;
    @Column(name = "conc_low")
    private double concLow;
    @Column(name = "conc_high")
    private double concHigh;
    @Column(name = "avg_hours")
    private int avgHours;

    protected AqiBreakpoint() {
    }

    public String getPollutant() {
        return pollutant;
    }

    public int getBandLowIndex() {
        return bandLowIndex;
    }

    public int getBandHighIndex() {
        return bandHighIndex;
    }

    public double getConcLow() {
        return concLow;
    }

    public double getConcHigh() {
        return concHigh;
    }

    public int getAvgHours() {
        return avgHours;
    }

    public static class Key implements Serializable {
        private String pollutant;
        private int bandLowIndex;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key k)) {
                return false;
            }
            return bandLowIndex == k.bandLowIndex && Objects.equals(pollutant, k.pollutant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pollutant, bandLowIndex);
        }
    }
}
