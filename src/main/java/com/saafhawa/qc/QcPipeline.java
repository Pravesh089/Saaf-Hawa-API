package com.saafhawa.qc;

import com.saafhawa.catalog.Pollutant;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Applies the QC flag taxonomy (§4.4). Row-local rules (NEGATIVE/ZERO_SUSPECT/SENTINEL/RANGE)
 * run synchronously on ingest (FR-3.1). Context rules (STUCK/SPIKE) are a scheduled job (M3).
 * Raw values are never altered — flags are additive (G3).
 */
@Component
public class QcPipeline {

    private final QcConfig config;

    public QcPipeline(QcConfig config) {
        this.config = config;
    }

    /** @return bitmask of row-local flags for a measurement value. */
    public int applyRowLocal(Pollutant pollutant, Double value) {
        EnumSet<QcFlag> flags = EnumSet.noneOf(QcFlag.class);
        if (value == null) {
            return 0;
        }
        if (config.sentinelValues().contains(value)) {
            flags.add(QcFlag.SENTINEL);
        }
        if (value < 0) {
            flags.add(QcFlag.NEGATIVE);
        }
        if (value == 0 && pollutant != null && pollutant.isZeroImplausible()) {
            flags.add(QcFlag.ZERO_SUSPECT);
        }
        if (pollutant != null && !flags.contains(QcFlag.SENTINEL)) {
            if (pollutant.getRangeMin() != null && value < pollutant.getRangeMin()) {
                flags.add(QcFlag.RANGE);
            }
            if (pollutant.getRangeMax() != null && value > pollutant.getRangeMax()) {
                flags.add(QcFlag.RANGE);
            }
        }
        return QcFlag.toMask(flags);
    }

    public String rulesetVersion() {
        return config.version();
    }
}
