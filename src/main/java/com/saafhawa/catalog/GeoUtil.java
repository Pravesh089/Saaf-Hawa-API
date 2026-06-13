package com.saafhawa.catalog;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/** Geometry + name-normalization helpers for identity resolution (architecture.md §5). */
public final class GeoUtil {

    /** SRID 4326 (WGS84) geometry factory shared across the catalog. */
    public static final GeometryFactory WGS84 =
            new GeometryFactory(new PrecisionModel(), 4326);

    private static final double EARTH_RADIUS_M = 6_371_000;

    private GeoUtil() {
    }

    public static Point point(double lon, double lat) {
        Point p = WGS84.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }

    /** Great-circle distance in metres. */
    public static double haversineMetres(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Normalize a station name for matching: lowercase, strip punctuation, collapse spaces. */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Jaccard token similarity in [0,1] between two normalized names. */
    public static double nameSimilarity(String normA, String normB) {
        if (normA.isEmpty() || normB.isEmpty()) {
            return 0;
        }
        if (normA.equals(normB)) {
            return 1;
        }
        var a = new java.util.HashSet<>(java.util.List.of(normA.split(" ")));
        var b = new java.util.HashSet<>(java.util.List.of(normB.split(" ")));
        var inter = new java.util.HashSet<>(a);
        inter.retainAll(b);
        var union = new java.util.HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }
}
