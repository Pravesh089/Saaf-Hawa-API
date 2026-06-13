package com.saafhawa.ingest.spi;

/** Raw bytes fetched from an upstream, archived before parsing (FR-1.3, G5). */
public record RawPayload(String contentType, byte[] bytes) {

    public int size() {
        return bytes == null ? 0 : bytes.length;
    }
}
