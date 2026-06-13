package com.saafhawa.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Maps a source's identifier to a canonical station (§4.1, FR-2.1). Never hard-deleted. */
@Entity
@Table(name = "station_alias")
public class StationAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String source;
    @Column(name = "source_key")
    private String sourceKey;
    @Column(name = "station_id")
    private String stationId;
    @Column(name = "source_name_raw")
    private String sourceNameRaw;
    @Column(name = "match_method")
    private String matchMethod = "AUTO";
    @Column(name = "match_confidence")
    private Double matchConfidence;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected StationAlias() {
    }

    public StationAlias(String source, String sourceKey, String stationId,
                        String sourceNameRaw, String matchMethod, Double matchConfidence) {
        this.source = source;
        this.sourceKey = sourceKey;
        this.stationId = stationId;
        this.sourceNameRaw = sourceNameRaw;
        this.matchMethod = matchMethod;
        this.matchConfidence = matchConfidence;
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public String getSourceNameRaw() {
        return sourceNameRaw;
    }

    public String getMatchMethod() {
        return matchMethod;
    }
}
