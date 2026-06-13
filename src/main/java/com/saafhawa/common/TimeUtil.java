package com.saafhawa.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Time helpers. Upstream timestamps are IST (UTC+05:30, no DST); we store UTC internally and
 * emit IST with explicit offsets in the API by default (FDD §4.3).
 */
public final class TimeUtil {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final ZoneOffset IST_OFFSET = ZoneOffset.ofHoursMinutes(5, 30);

    /** data.gov.in "dd-mm-yyyy hh:mm:ss" in IST. */
    private static final DateTimeFormatter DATAGOVIN =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private TimeUtil() {
    }

    /** Parse a data.gov.in last_update string (IST) into an instant, or null if unparseable. */
    public static Instant parseDataGovIn(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(raw.trim(), DATAGOVIN);
            return ldt.atOffset(IST_OFFSET).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Render an instant as an ISO-8601 string in IST with the +05:30 offset. */
    public static String toIstIso(Instant instant) {
        if (instant == null) {
            return null;
        }
        OffsetDateTime odt = instant.atZone(IST).toOffsetDateTime();
        return odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
