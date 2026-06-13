package com.saafhawa.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Controlled-vocabulary pollutant (§4.1). */
@Entity
@Table(name = "pollutant")
public class Pollutant {

    @Id
    private String code;
    @Column(name = "display_name")
    private String displayName;
    private String unit;
    @Column(name = "zero_implausible")
    private boolean zeroImplausible;
    @Column(name = "range_min")
    private Double rangeMin;
    @Column(name = "range_max")
    private Double rangeMax;

    protected Pollutant() {
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUnit() {
        return unit;
    }

    public boolean isZeroImplausible() {
        return zeroImplausible;
    }

    public Double getRangeMin() {
        return rangeMin;
    }

    public Double getRangeMax() {
        return rangeMax;
    }
}
