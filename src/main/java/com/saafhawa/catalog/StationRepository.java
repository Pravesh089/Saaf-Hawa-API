package com.saafhawa.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StationRepository extends JpaRepository<Station, String> {

    @Query("""
            SELECT s FROM Station s
            WHERE (CAST(:state AS string) IS NULL OR s.stateCode = :state)
              AND (CAST(:city AS string) IS NULL OR LOWER(s.city) = LOWER(CAST(:city AS string)))
            ORDER BY s.id ASC
            """)
    List<Station> search(@Param("state") String stateCode, @Param("city") String city);

    /** Bounding-box filter (FR-4.1). minLon/minLat/maxLon/maxLat. */
    @Query(value = """
            SELECT * FROM station
            WHERE geom IS NOT NULL
              AND geom && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
            ORDER BY id ASC
            """, nativeQuery = true)
    List<Station> withinBbox(@Param("minLon") double minLon, @Param("minLat") double minLat,
                             @Param("maxLon") double maxLon, @Param("maxLat") double maxLat);

    /** Nearest stations to a point, ordered by spherical distance (FR-4.3, PostGIS). */
    @Query(value = """
            SELECT id,
                   ST_DistanceSphere(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)) AS distance_m
            FROM station
            WHERE geom IS NOT NULL
            ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
            LIMIT :limit
            """, nativeQuery = true)
    List<NearestRow> nearest(@Param("lat") double lat, @Param("lon") double lon,
                             @Param("limit") int limit);

    /** Highest existing station id for a given prefix, for sequence assignment. */
    @Query(value = "SELECT id FROM station WHERE id LIKE :prefix || '%' ORDER BY id DESC LIMIT 1",
            nativeQuery = true)
    String maxIdForPrefix(@Param("prefix") String prefix);

    /** Candidate stations in a city for fuzzy alias matching. */
    List<Station> findByCityIgnoreCase(String city);

    long countByNeedsReviewTrue();

    /** Projection for {@link #nearest}. */
    interface NearestRow {
        String getId();

        double getDistanceM();
    }
}
