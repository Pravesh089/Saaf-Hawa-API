package com.saafhawa.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

/** Canonical physical monitor. ID scheme IN-&lt;STATE&gt;-&lt;seq4&gt; (architecture.md §5). */
@Entity
@Table(name = "station")
public class Station {

    @Id
    private String id;
    private String name;
    @Column(name = "name_norm")
    private String nameNorm;
    private String city;
    private String state;
    @Column(name = "state_code")
    private String stateCode;
    private String agency;
    @Column(name = "station_type")
    private String stationType = "UNKNOWN";
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;
    private String status = "UNKNOWN";
    @Column(name = "needs_review")
    private boolean needsReview;
    @Column(name = "first_seen")
    private Instant firstSeen;
    @Column(name = "last_seen")
    private Instant lastSeen;

    protected Station() {
    }

    public Station(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameNorm() {
        return nameNorm;
    }

    public void setNameNorm(String nameNorm) {
        this.nameNorm = nameNorm;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getAgency() {
        return agency;
    }

    public void setAgency(String agency) {
        this.agency = agency;
    }

    public String getStationType() {
        return stationType;
    }

    public void setStationType(String stationType) {
        this.stationType = stationType;
    }

    public Point getGeom() {
        return geom;
    }

    public void setGeom(Point geom) {
        this.geom = geom;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(Instant firstSeen) {
        this.firstSeen = firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Double latitude() {
        return geom == null ? null : geom.getY();
    }

    public Double longitude() {
        return geom == null ? null : geom.getX();
    }
}
