package com.saafhawa.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/** Operational ledger row: one execution of one adapter (§4.1, FR-1.2). */
@Entity
@Table(name = "ingestion_run")
public class IngestionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String source;
    @Column(name = "window_start")
    private Instant windowStart;
    @Column(name = "window_end")
    private Instant windowEnd;
    @Column(name = "started_at")
    private Instant startedAt = Instant.now();
    @Column(name = "finished_at")
    private Instant finishedAt;
    private String outcome;
    private int fetched;
    private int inserted;
    private int updated;
    private int duplicate;
    private int rejected;
    @Column(name = "error_detail")
    private String errorDetail;
    @Column(name = "raw_ref")
    private String rawRef;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reject_samples")
    private List<String> rejectSamples;

    protected IngestionRun() {
    }

    public IngestionRun(String source, Instant windowStart, Instant windowEnd) {
        this.source = source;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public int getFetched() {
        return fetched;
    }

    public void setFetched(int fetched) {
        this.fetched = fetched;
    }

    public int getInserted() {
        return inserted;
    }

    public void setInserted(int inserted) {
        this.inserted = inserted;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getDuplicate() {
        return duplicate;
    }

    public void setDuplicate(int duplicate) {
        this.duplicate = duplicate;
    }

    public int getRejected() {
        return rejected;
    }

    public void setRejected(int rejected) {
        this.rejected = rejected;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
    }

    public String getRawRef() {
        return rawRef;
    }

    public void setRawRef(String rawRef) {
        this.rawRef = rawRef;
    }

    public void setRejectSamples(List<String> rejectSamples) {
        this.rejectSamples = rejectSamples;
    }
}
