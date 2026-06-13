package com.saafhawa.qc;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Snapshot of the active QC ruleset, loaded from the qc_config table (FR-3.2). Cached and
 * refreshed lazily; thresholds change without code change.
 */
@Component
public class QcConfig {

    private final QcConfigRepository repo;
    private volatile Snapshot snapshot;

    public QcConfig(QcConfigRepository repo) {
        this.repo = repo;
    }

    public synchronized void reload() {
        List<QcConfigEntity> active = repo.findByActiveTrue();
        Map<String, String> values = active.stream()
                .collect(Collectors.toMap(QcConfigEntity::getKey, QcConfigEntity::getValue, (a, b) -> a));
        String version = active.isEmpty() ? "none" : active.get(0).getRulesetVersion();
        this.snapshot = new Snapshot(version, values);
    }

    private Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s == null) {
            reload();
            s = snapshot;
        }
        return s;
    }

    public String version() {
        return snapshot().version();
    }

    public Set<Double> sentinelValues() {
        String raw = snapshot().values().getOrDefault("sentinel.values", "999,9999");
        return Arrays.stream(raw.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Double::parseDouble)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public double spikeK() {
        return Double.parseDouble(snapshot().values().getOrDefault("spike.k", "4"));
    }

    public int spikeWindowDays() {
        return Integer.parseInt(snapshot().values().getOrDefault("spike.window_days", "30"));
    }

    public int spikePercentile() {
        return Integer.parseInt(snapshot().values().getOrDefault("spike.percentile", "95"));
    }

    public int stuckMinIntervals() {
        return Integer.parseInt(snapshot().values().getOrDefault("stuck.min_intervals", "24"));
    }

    public Map<String, String> allValues() {
        return snapshot().values();
    }

    private record Snapshot(String version, Map<String, String> values) {
    }
}
