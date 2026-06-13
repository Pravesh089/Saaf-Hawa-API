package com.saafhawa.qc;

import java.util.EnumSet;
import java.util.Set;

/**
 * QC flag taxonomy (§4.4). Stored as a bitmask int on each measurement (db-design.md §3).
 * Bit positions are stable and append-only.
 */
public enum QcFlag {
    NEGATIVE(1),
    ZERO_SUSPECT(2),
    SENTINEL(4),
    STUCK(8),
    SPIKE(16),
    RANGE(32),
    DUPLICATE_SOURCE(64);

    private final int bit;

    QcFlag(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public static int toMask(Set<QcFlag> flags) {
        int m = 0;
        for (QcFlag f : flags) {
            m |= f.bit;
        }
        return m;
    }

    public static EnumSet<QcFlag> fromMask(int mask) {
        EnumSet<QcFlag> out = EnumSet.noneOf(QcFlag.class);
        for (QcFlag f : values()) {
            if ((mask & f.bit) != 0) {
                out.add(f);
            }
        }
        return out;
    }
}
