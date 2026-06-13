package com.saafhawa.ingest.archive;

import java.time.Instant;

/**
 * Archives raw upstream payloads before parsing (FR-1.3, G5). Reprocessing from archive must be
 * possible without re-fetching. Path convention: {@code source/yyyy/MM/dd/HHmmss-<id>.json.gz}.
 */
public interface RawArchiveService {

    /** Store gzip-compressed bytes; return an opaque reference (filesystem path or object key). */
    String archive(String source, Instant when, String suffix, byte[] data);

    /** Retrieve and decompress previously archived bytes, for reprocessing. */
    byte[] retrieve(String ref);
}
