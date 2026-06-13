package com.saafhawa.api;

import com.saafhawa.qc.QcConfig;
import com.saafhawa.qc.QcFlag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public, human-readable QC methodology page generated from the active config (FR-3.4, G3).
 * The flag definitions and current thresholds are emitted exactly as applied.
 */
@RestController
public class QcController {

    private final QcConfig config;

    public QcController(QcConfig config) {
        this.config = config;
    }

    @GetMapping(value = "/v1/qc/methodology", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public String methodology() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Saaf Hawa — QC Methodology\n\n");
        sb.append("Active ruleset: **").append(config.version()).append("**\n\n");
        sb.append("Flags are additive and never destructive — the raw value is always preserved.\n\n");
        sb.append("## Flags\n\n");
        sb.append("| Flag | Rule |\n|---|---|\n");
        sb.append("| NEGATIVE | value < 0 |\n");
        sb.append("| ZERO_SUSPECT | value == 0 for a pollutant where 0 is physically implausible |\n");
        sb.append("| SENTINEL | value in the configured sentinel set |\n");
        sb.append("| STUCK | identical non-zero value over N consecutive intervals |\n");
        sb.append("| SPIKE | value > K× the 95th percentile of the trailing 30-day distribution |\n");
        sb.append("| RANGE | outside the configured plausible physical range per pollutant |\n");
        sb.append("| DUPLICATE_SOURCE | conflicting values for the same logical measurement across sources |\n\n");
        sb.append("## Active thresholds\n\n");
        sb.append("| Key | Value |\n|---|---|\n");
        for (Map.Entry<String, String> e : config.allValues().entrySet()) {
            sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        }
        sb.append("\nBit positions: ");
        for (QcFlag f : QcFlag.values()) {
            sb.append(f.name()).append("=").append(f.bit()).append("  ");
        }
        sb.append("\n");
        return sb.toString();
    }
}
