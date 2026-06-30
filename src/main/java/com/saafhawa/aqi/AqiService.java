package com.saafhawa.aqi;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads AQI breakpoints from the DB and exposes the pure {@link AqiCalculator} (§9.6). */
@Service
public class AqiService {

    private final AqiBreakpointRepository repo;
    private volatile Map<String, List<AqiCalculator.Band>> bands;

    public AqiService(AqiBreakpointRepository repo) {
        this.repo = repo;
    }

    private Map<String, List<AqiCalculator.Band>> bands() {
        Map<String, List<AqiCalculator.Band>> b = bands;
        if (b == null) {
            b = load();
            bands = b;
        }
        return b;
    }

    private synchronized Map<String, List<AqiCalculator.Band>> load() {
        Map<String, List<AqiCalculator.Band>> map = new HashMap<>();
        for (AqiBreakpoint bp : repo.findAll()) {
            map.computeIfAbsent(bp.getPollutant(), k -> new ArrayList<>())
                    .add(new AqiCalculator.Band(bp.getBandLowIndex(), bp.getBandHighIndex(),
                            bp.getConcLow(), bp.getConcHigh(), bp.getAvgHours()));
        }
        return map;
    }

    /**
     * Per-pollutant divisor applied to feed concentrations before the breakpoints are used. The
     * upstream (CPCB via data.gov.in) reports every pollutant in µg/m³, but CPCB's AQI breakpoints
     * express CO — and only CO — in mg/m³. Without this conversion a CO reading of e.g. 94 µg/m³ is
     * treated as 94 mg/m³, pinning its sub-index to ~500 and spuriously dominating the
     * max-sub-index AQI at nearly every station (verification-log V9).
     */
    private static final Map<String, Double> CONC_UNIT_DIVISOR = Map.of("CO", 1000.0);

    public AqiCalculator.Result compute(Map<String, Double> concentrations) {
        return AqiCalculator.compute(normalizeUnits(concentrations), bands());
    }

    /** Scales feed concentrations (µg/m³) to the unit each pollutant's breakpoints expect. */
    static Map<String, Double> normalizeUnits(Map<String, Double> concentrations) {
        Map<String, Double> out = new HashMap<>(concentrations.size() * 2);
        for (var e : concentrations.entrySet()) {
            Double value = e.getValue();
            Double divisor = CONC_UNIT_DIVISOR.get(e.getKey());
            if (value != null && divisor != null) {
                out.put(e.getKey(), value / divisor);
            } else {
                out.put(e.getKey(), value);
            }
        }
        return out;
    }

    public Map<String, List<AqiCalculator.Band>> bandsByPollutant() {
        return bands();
    }
}
