package com.saafhawa.ingest.archive;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Shared archive path/key convention (§5). */
final class ArchivePaths {

    private static final DateTimeFormatter DIR =
            DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FILE =
            DateTimeFormatter.ofPattern("HHmmssSSS").withZone(ZoneOffset.UTC);

    private ArchivePaths() {
    }

    static String key(String source, Instant when, String suffix) {
        return source + "/" + DIR.format(when) + "/" + FILE.format(when) + "-" + suffix + ".json.gz";
    }
}
