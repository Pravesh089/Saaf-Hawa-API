package com.saafhawa.aqi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Pure CPCB AQI computation (§4.2). No Spring dependencies — unit-tested in isolation (§9.6).
 * AQI = max of valid pollutant sub-indices; valid only if ≥3 sub-indices exist and at least one
 * is PM2.5 or PM10. Sub-index is linear interpolation within a breakpoint band.
 */
public final class AqiCalculator {

    /** A breakpoint band: index range, concentration range, averaging window. */
    public record Band(int indexLow, int indexHigh, double concLow, double concHigh, int avgHours) {
    }

    /** Computed AQI result, including per-pollutant sub-indices and validity (§4.2). */
    public record Result(Integer aqi, String category, String prominentPollutant,
                         Map<String, Integer> subIndices, boolean valid) {
    }

    /**
     * Concentration ceiling marking the open-ended top band. The CPCB top band ("&gt; X") has no
     * upper concentration bound, so it is seeded with this sentinel and treated as a clamp to the
     * top index (Severe 500) rather than interpolated (verification-log.md V4, ambiguities.md #3).
     */
    static final double OPEN_ENDED = 100_000;

    private AqiCalculator() {
    }

    /**
     * Sub-index for one pollutant concentration given its bands. Within a finite band the value is
     * linearly interpolated; within (or above) the open-ended top band it clamps to the top index.
     * Empty bands → empty.
     */
    public static Optional<Integer> subIndex(List<Band> bands, double conc) {
        if (bands == null || bands.isEmpty()) {
            return Optional.empty();
        }
        List<Band> sorted = bands.stream()
                .sorted((a, b) -> Double.compare(a.concLow(), b.concLow()))
                .toList();
        for (Band band : sorted) {
            if (conc <= band.concHigh()) {
                if (band.concHigh() >= OPEN_ENDED) {
                    return Optional.of(band.indexHigh());
                }
                double span = band.concHigh() - band.concLow();
                double idxSpan = band.indexHigh() - band.indexLow();
                double i = span <= 0 ? band.indexLow()
                        : (idxSpan / span) * (conc - band.concLow()) + band.indexLow();
                return Optional.of((int) Math.round(Math.max(band.indexLow(), i)));
            }
        }
        // Above every band (incl. the open-ended one): clamp to the highest index.
        return Optional.of(sorted.get(sorted.size() - 1).indexHigh());
    }

    /**
     * Overall AQI from a set of pollutant concentrations (already at the correct averaging window).
     *
     * @param concentrations pollutant code → concentration
     * @param bandsByPollutant pollutant code → its breakpoint bands
     */
    public static Result compute(Map<String, Double> concentrations,
                                 Map<String, List<Band>> bandsByPollutant) {
        Map<String, Integer> subIndices = new TreeMap<>();
        for (var e : concentrations.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            subIndex(bandsByPollutant.get(e.getKey()), e.getValue())
                    .ifPresent(idx -> subIndices.put(e.getKey(), idx));
        }

        boolean hasPm = subIndices.containsKey("PM2.5") || subIndices.containsKey("PM10");
        boolean valid = subIndices.size() >= 3 && hasPm;
        if (subIndices.isEmpty()) {
            return new Result(null, null, null, subIndices, false);
        }

        String prominent = null;
        int max = Integer.MIN_VALUE;
        for (var e : subIndices.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                prominent = e.getKey();
            }
        }
        return new Result(max, category(max), prominent, subIndices, valid);
    }

    /** CPCB category bands (§4.2). */
    public static String category(int aqi) {
        if (aqi <= 50) {
            return "Good";
        }
        if (aqi <= 100) {
            return "Satisfactory";
        }
        if (aqi <= 200) {
            return "Moderate";
        }
        if (aqi <= 300) {
            return "Poor";
        }
        if (aqi <= 400) {
            return "Very Poor";
        }
        return "Severe";
    }
}
