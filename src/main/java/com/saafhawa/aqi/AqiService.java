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

    public AqiCalculator.Result compute(Map<String, Double> concentrations) {
        return AqiCalculator.compute(concentrations, bands());
    }

    public Map<String, List<AqiCalculator.Band>> bandsByPollutant() {
        return bands();
    }
}
