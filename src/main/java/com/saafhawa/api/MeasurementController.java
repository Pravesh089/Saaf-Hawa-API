package com.saafhawa.api;

import com.saafhawa.api.dto.AggregateDto;
import com.saafhawa.api.dto.ApiResponse;
import com.saafhawa.api.dto.MeasurementDto;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** GET /v1/measurements (FR-4.4). Supports json + csv output. */
@RestController
public class MeasurementController {

    private final MeasurementQueryService service;

    public MeasurementController(MeasurementQueryService service) {
        this.service = service;
    }

    @GetMapping("/v1/measurements")
    public ResponseEntity<?> measurements(
            @RequestParam(required = false) String station,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String pollutant,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false, defaultValue = "raw") String interval,
            @RequestParam(name = "include_flagged", required = false) String includeFlagged,
            @RequestParam(required = false, defaultValue = "json") String format,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {

        ApiResponse<Object> response = service.query(station, city, pollutant, from, to,
                interval, includeFlagged, cursor, limit);

        CacheControl cache = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();
        if ("csv".equalsIgnoreCase(format)) {
            String csv = toCsv(response);
            return ResponseEntity.ok()
                    .cacheControl(cache)
                    .header("Content-Disposition", "attachment; filename=measurements.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }
        return ResponseEntity.ok().cacheControl(cache).body(response);
    }

    private String toCsv(ApiResponse<Object> response) {
        StringBuilder sb = new StringBuilder();
        boolean aggregate = !response.results().isEmpty() && response.results().get(0) instanceof AggregateDto;
        if (aggregate) {
            sb.append("station_id,pollutant,unit,interval,start,end,mean,min,max,count,expected,availability\n");
            for (Object o : response.results()) {
                AggregateDto a = (AggregateDto) o;
                sb.append(String.join(",", a.stationId(), a.pollutant(), nz(a.unit()),
                        a.period().interval(), a.period().start(), a.period().end(),
                        str(a.value().mean()), str(a.value().min()), str(a.value().max()),
                        String.valueOf(a.value().count()), String.valueOf(a.value().expected()),
                        String.valueOf(a.value().availability()))).append("\n");
            }
        } else {
            sb.append("station_id,pollutant,unit,interval,start,end,value,min,max,flags,source,qc_ruleset\n");
            for (Object o : response.results()) {
                MeasurementDto m = (MeasurementDto) o;
                sb.append(String.join(",", m.stationId(), m.pollutant(), nz(m.unit()),
                        m.period().interval(), m.period().start(), m.period().end(),
                        str(m.value()), str(m.valueMin()), str(m.valueMax()),
                        String.join("|", m.flags()), nz(m.source()), nz(m.qcRuleset()))).append("\n");
            }
        }
        return sb.toString();
    }

    private static String str(Double d) {
        return d == null ? "" : d.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
