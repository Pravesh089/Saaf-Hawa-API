package com.saafhawa.catalog;

import com.saafhawa.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Canonical station registry + cross-source identity resolution (FR-2.1, architecture.md §5).
 * Never hard-deletes; low-confidence matches auto-create a station flagged needs_review.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    /** Matching thresholds (architecture.md §5; §5.3 uses 300 m). */
    private static final double MAX_DISTANCE_M = 300;
    private static final double MIN_SIMILARITY = 0.6;

    private final StationRepository stations;
    private final StationAliasRepository aliases;

    public CatalogService(StationRepository stations, StationAliasRepository aliases) {
        this.stations = stations;
        this.aliases = aliases;
    }

    /**
     * Resolve an upstream station to a canonical one, creating an alias (and station) on first
     * sight. Idempotent: a known (source, sourceKey) returns the existing station and refreshes
     * last_seen.
     */
    @Transactional
    public Station resolve(SourceStation src) {
        Optional<StationAlias> existing = aliases.findBySourceAndSourceKey(src.source(), src.sourceKey());
        if (existing.isPresent()) {
            Station s = stations.findById(existing.get().getStationId())
                    .orElseThrow(() -> ApiException.notFound("Alias points to missing station"));
            touch(s, src);
            return s;
        }

        Match match = bestFuzzyMatch(src);
        if (match != null) {
            attachAlias(src, match.station().getId(), match.similarity() >= 0.99 ? "EXACT" : "FUZZY",
                    match.similarity());
            touch(match.station(), src);
            return match.station();
        }

        // No confident match: create a new candidate station.
        Station created = createStation(src, /*needsReview*/ true);
        attachAlias(src, created.getId(), "AUTO", null);
        log.info("Auto-created candidate station {} for {}:{} (needs review)",
                created.getId(), src.source(), src.sourceKey());
        return created;
    }

    private Match bestFuzzyMatch(SourceStation src) {
        if (src.city() == null) {
            return null;
        }
        String norm = GeoUtil.normalizeName(src.name());
        Match best = null;
        for (Station s : stations.findByCityIgnoreCase(src.city())) {
            double sim = GeoUtil.nameSimilarity(norm, s.getNameNorm() == null ? "" : s.getNameNorm());
            boolean closeEnough = true;
            if (src.lat() != null && src.lon() != null && s.getGeom() != null) {
                double d = GeoUtil.haversineMetres(src.lat(), src.lon(), s.latitude(), s.longitude());
                closeEnough = d <= MAX_DISTANCE_M;
            }
            boolean accept = (sim >= 0.99) || (sim >= MIN_SIMILARITY && closeEnough);
            if (accept && (best == null || sim > best.similarity())) {
                best = new Match(s, sim);
            }
        }
        return best;
    }

    private Station createStation(SourceStation src, boolean needsReview) {
        String code = StateCodes.codeFor(src.state());
        String id = nextStationId(code);
        Station s = new Station(id);
        s.setName(src.name());
        s.setNameNorm(GeoUtil.normalizeName(src.name()));
        s.setCity(src.city());
        s.setState(src.state());
        s.setStateCode(code);
        s.setAgency(src.agency());
        s.setStationType(src.stationType() == null ? "UNKNOWN" : src.stationType());
        if (src.lat() != null && src.lon() != null) {
            s.setGeom(GeoUtil.point(src.lon(), src.lat()));
        }
        s.setStatus("ACTIVE");
        s.setNeedsReview(needsReview);
        s.setFirstSeen(Instant.now());
        s.setLastSeen(Instant.now());
        return stations.save(s);
    }

    private String nextStationId(String stateCode) {
        String prefix = "IN-" + stateCode + "-";
        String maxId = stations.maxIdForPrefix(prefix);
        int seq = 1;
        if (maxId != null) {
            try {
                seq = Integer.parseInt(maxId.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                // fall back to 1; collision is prevented by the PK on insert
            }
        }
        return prefix + String.format("%04d", seq);
    }

    private void attachAlias(SourceStation src, String stationId, String method, Double confidence) {
        aliases.save(new StationAlias(src.source(), src.sourceKey(), stationId,
                src.name(), method, confidence));
    }

    private void touch(Station s, SourceStation src) {
        s.setLastSeen(Instant.now());
        if (s.getFirstSeen() == null) {
            s.setFirstSeen(Instant.now());
        }
        stations.save(s);
    }

    // --- read side used by the API ---

    @Transactional(readOnly = true)
    public Optional<Station> find(String id) {
        return stations.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Station> search(String stateCode, String city) {
        return stations.search(stateCode, city);
    }

    @Transactional(readOnly = true)
    public List<Station> withinBbox(double minLon, double minLat, double maxLon, double maxLat) {
        return stations.withinBbox(minLon, minLat, maxLon, maxLat);
    }

    @Transactional(readOnly = true)
    public List<StationRepository.NearestRow> nearest(double lat, double lon, int limit) {
        return stations.nearest(lat, lon, limit);
    }

    /** Merge {@code fromId} into {@code intoId}, re-pointing aliases; preserves history (FR-2.1). */
    @Transactional
    public void merge(String fromId, String intoId) {
        Station from = stations.findById(fromId)
                .orElseThrow(() -> ApiException.notFound("Station not found: " + fromId));
        stations.findById(intoId)
                .orElseThrow(() -> ApiException.notFound("Station not found: " + intoId));
        aliases.findAll().stream()
                .filter(a -> a.getStationId().equals(fromId))
                .forEach(a -> a.setStationId(intoId));
        from.setStatus("INACTIVE");
        from.setNeedsReview(false);
        stations.save(from);
        log.info("Merged station {} into {}", fromId, intoId);
    }

    private record Match(Station station, double similarity) {
    }
}
