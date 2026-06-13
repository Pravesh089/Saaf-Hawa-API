package com.saafhawa.api;

import com.saafhawa.api.dto.ApiResponse;
import com.saafhawa.api.dto.Meta;
import com.saafhawa.api.dto.StationDto;
import com.saafhawa.catalog.CatalogService;
import com.saafhawa.catalog.Station;
import com.saafhawa.catalog.StationRepository;
import com.saafhawa.common.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Station catalog endpoints (FR-4.1/4.2/4.3). */
@RestController
@RequestMapping("/v1/stations")
public class StationController {

    private final CatalogService catalog;

    public StationController(CatalogService catalog) {
        this.catalog = catalog;
    }

    /** FR-4.1: filter by state, city, bbox; GeoJSON option. */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String bbox,
            @RequestParam(required = false, defaultValue = "json") String format) {

        List<Station> stations;
        if (bbox != null) {
            double[] b = parseBbox(bbox);
            stations = catalog.withinBbox(b[0], b[1], b[2], b[3]);
        } else {
            stations = catalog.search(state, city);
        }

        if ("geojson".equalsIgnoreCase(format)) {
            return cached(ResponseEntity.ok(toGeoJson(stations)));
        }
        List<StationDto> dtos = stations.stream().map(StationDto::from).toList();
        return cached(ResponseEntity.ok(ApiResponse.of(
                Meta.of(List.of("cpcb-datagovin"), null, null), dtos)));
    }

    /** FR-4.2: station metadata. */
    @GetMapping("/{id}")
    public ResponseEntity<StationDto> get(@PathVariable String id) {
        Station s = catalog.find(id).orElseThrow(() -> ApiException.notFound("Station not found: " + id));
        return cached(ResponseEntity.ok(StationDto.from(s)));
    }

    /** FR-4.3: nearest stations to a point (PostGIS). */
    @GetMapping("/nearest")
    public ResponseEntity<ApiResponse<StationDto>> nearest(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "5") int limit) {
        if (limit < 1 || limit > 100) {
            throw ApiException.badRequest("limit must be between 1 and 100");
        }
        List<StationRepository.NearestRow> rows = catalog.nearest(lat, lon, limit);
        List<StationDto> dtos = rows.stream()
                .map(r -> catalog.find(r.getId())
                        .map(s -> StationDto.from(s, r.getDistanceM()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        return cached(ResponseEntity.ok(ApiResponse.of(
                Meta.of(List.of("cpcb-datagovin"), null, null), dtos)));
    }

    private double[] parseBbox(String bbox) {
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw ApiException.badRequest("bbox must be minLon,minLat,maxLon,maxLat");
        }
        try {
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3])};
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("bbox values must be numbers");
        }
    }

    private Map<String, Object> toGeoJson(List<Station> stations) {
        List<Map<String, Object>> features = stations.stream()
                .filter(s -> s.getGeom() != null)
                .map(s -> {
                    Map<String, Object> geometry = new LinkedHashMap<>();
                    geometry.put("type", "Point");
                    geometry.put("coordinates", List.of(s.longitude(), s.latitude()));
                    Map<String, Object> props = new LinkedHashMap<>();
                    props.put("id", s.getId());
                    props.put("name", s.getName());
                    props.put("city", s.getCity());
                    props.put("state", s.getState());
                    Map<String, Object> feature = new LinkedHashMap<>();
                    feature.put("type", "Feature");
                    feature.put("geometry", geometry);
                    feature.put("properties", props);
                    return feature;
                })
                .toList();
        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("features", features);
        return fc;
    }

    private <T> ResponseEntity<T> cached(ResponseEntity<T> resp) {
        return ResponseEntity.status(resp.getStatusCode())
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(resp.getBody());
    }
}
