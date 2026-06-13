package com.saafhawa.ingest.spi;

/**
 * Service Provider Interface for an ingestion source (FR-1.1). Each source implements this;
 * adapters are independently schedulable and fail in isolation. New sources are added by
 * implementing this interface only — nothing else in the pipeline changes.
 */
public interface SourceAdapter {

    /** Stable source id used in the {@code source} column and the ledger, e.g. "cpcb-datagovin". */
    String sourceId();

    /** Fetch raw bytes for the window. Archived before parsing (FR-1.3). */
    RawPayload fetch(IngestionWindow window) throws Exception;

    /** Parse raw bytes into canonical measurements, counting rejects (FR-2.3). */
    ParseResult parse(RawPayload payload) throws Exception;
}
