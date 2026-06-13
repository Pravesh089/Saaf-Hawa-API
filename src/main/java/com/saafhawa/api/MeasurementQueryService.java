package com.saafhawa.api;

import com.saafhawa.api.dto.AggregateDto;
import com.saafhawa.api.dto.ApiResponse;
import com.saafhawa.api.dto.MeasurementDto;
import com.saafhawa.api.dto.Meta;
import com.saafhawa.catalog.CatalogService;
import com.saafhawa.catalog.Pollutant;
import com.saafhawa.catalog.PollutantRepository;
import com.saafhawa.common.ApiException;
import com.saafhawa.common.TimeUtil;
import com.saafhawa.measurement.AggregateRow;
import com.saafhawa.measurement.MeasurementCursor;
import com.saafhawa.measurement.MeasurementRepository;
import com.saafhawa.measurement.MeasurementRow;
import com.saafhawa.qc.QcFlag;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Implements GET /v1/measurements (FR-4.4): param parsing, aggregation dispatch, pagination. */
@Service
public class MeasurementQueryService {

    static final int MAX_ROWS = 50_000;

    private final MeasurementRepository measurements;
    private final CatalogService catalog;
    private final PollutantRepository pollutants;

    public MeasurementQueryService(MeasurementRepository measurements, CatalogService catalog,
                                   PollutantRepository pollutants) {
        this.measurements = measurements;
        this.catalog = catalog;
        this.pollutants = pollutants;
    }

    public ApiResponse<Object> query(String stationParam, String cityParam, String pollutantParam,
                                     String from, String to, String interval, String includeFlagged,
                                     String cursor, Integer limit) {
        List<String> stations = resolveStations(stationParam, cityParam);
        List<String> polls = parsePollutants(pollutantParam);
        Instant fromTs = parseTime(from, false);
        Instant toTs = parseTime(to, true);
        if (!toTs.isAfter(fromTs)) {
            throw ApiException.badRequest("'to' must be after 'from'");
        }
        String flaggedMode = flaggedMode(includeFlagged);
        int rowLimit = limit == null ? MAX_ROWS : Math.min(Math.max(1, limit), MAX_ROWS);
        Map<String, String> units = unitMap();

        String iv = interval == null ? "raw" : interval.toLowerCase();
        return switch (iv) {
            case "raw", "hour" -> rawQuery(stations, polls, fromTs, toTs, flaggedMode, cursor, rowLimit, units);
            case "day" -> aggregateQuery("measurement_daily", "day", stations, polls, fromTs, toTs, rowLimit, units);
            case "month" -> aggregateQuery("measurement_monthly", "month", stations, polls, fromTs, toTs, rowLimit, units);
            default -> throw ApiException.badRequest("interval must be raw|hour|day|month");
        };
    }

    private ApiResponse<Object> rawQuery(List<String> stations, List<String> polls, Instant from,
                                         Instant to, String flaggedMode, String cursorToken,
                                         int limit, Map<String, String> units) {
        MeasurementCursor cursor = cursorToken == null ? null : MeasurementCursor.decode(cursorToken);
        List<MeasurementRow> rows = measurements.queryRaw(stations, polls, from, to, flaggedMode, cursor, limit + 1);
        String next = null;
        if (rows.size() > limit) {
            MeasurementRow last = rows.get(limit - 1);
            next = new MeasurementCursor(last.intervalStart(), last.stationId(), last.pollutant()).encode();
            rows = rows.subList(0, limit);
        }
        List<Object> results = new ArrayList<>(rows.size());
        for (MeasurementRow r : rows) {
            Instant end = r.intervalStart().plusSeconds(r.intervalSeconds());
            List<String> flags = QcFlag.fromMask(r.qcFlags()).stream().map(Enum::name).toList();
            results.add(new MeasurementDto(r.stationId(), r.pollutant(),
                    units.get(r.pollutant()),
                    new MeasurementDto.Period(TimeUtil.toIstIso(r.intervalStart()), TimeUtil.toIstIso(end), "raw"),
                    r.value(), r.valueMin(), r.valueMax(), flags, r.source(), r.qcRuleset(), r.reportedAqi()));
        }
        return ApiResponse.of(Meta.of(List.of("cpcb-datagovin"), rulesetOf(rows), next), results);
    }

    private ApiResponse<Object> aggregateQuery(String view, String interval, List<String> stations,
                                               List<String> polls, Instant from, Instant to,
                                               int limit, Map<String, String> units) {
        List<AggregateRow> rows = measurements.queryAggregate(view, stations, polls, from, to, limit);
        List<Object> results = new ArrayList<>(rows.size());
        for (AggregateRow r : rows) {
            Instant end = "day".equals(interval)
                    ? r.bucketStart().plus(1, ChronoUnit.DAYS)
                    : r.bucketStart().atZone(TimeUtil.IST).plusMonths(1).toInstant();
            long expected = expectedIntervals(interval, r.bucketStart());
            double availability = expected == 0 ? 0 : (double) r.unflaggedCount() / expected;
            results.add(new AggregateDto(r.stationId(), r.pollutant(), units.get(r.pollutant()),
                    new MeasurementDto.Period(TimeUtil.toIstIso(r.bucketStart()), TimeUtil.toIstIso(end), interval),
                    new AggregateDto.Value(r.mean(), r.min(), r.max(), r.count(), expected,
                            Math.round(availability * 1000.0) / 1000.0),
                    Map.of("flagged", Math.max(0, r.count() - r.unflaggedCount()))));
        }
        return ApiResponse.of(Meta.of(List.of("cpcb-datagovin"), null, null), results);
    }

    /** Hourly source data → 24 expected intervals/day; month scales by its length. */
    private long expectedIntervals(String interval, Instant bucketStart) {
        if ("day".equals(interval)) {
            return 24;
        }
        int days = bucketStart.atZone(TimeUtil.IST).toLocalDate().lengthOfMonth();
        return 24L * days;
    }

    private List<String> resolveStations(String stationParam, String cityParam) {
        if (stationParam != null && !stationParam.isBlank()) {
            return Arrays.stream(stationParam.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        if (cityParam != null && !cityParam.isBlank()) {
            List<String> ids = catalog.search(null, cityParam).stream().map(s -> s.getId()).toList();
            if (ids.isEmpty()) {
                throw ApiException.notFound("No stations found for city: " + cityParam);
            }
            return ids;
        }
        throw ApiException.badRequest("Provide 'station' or 'city'");
    }

    private List<String> parsePollutants(String pollutantParam) {
        if (pollutantParam == null || pollutantParam.isBlank()) {
            throw ApiException.badRequest("'pollutant' is required");
        }
        return Arrays.stream(pollutantParam.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String flaggedMode(String includeFlagged) {
        if (includeFlagged == null) {
            return "ALL";
        }
        return switch (includeFlagged.toLowerCase()) {
            case "true" -> "ALL";
            case "false" -> "UNFLAGGED";
            case "only" -> "FLAGGED_ONLY";
            default -> throw ApiException.badRequest("include_flagged must be true|false|only");
        };
    }

    private Instant parseTime(String value, boolean endExclusiveDefault) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("'from' and 'to' are required");
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException ignored) {
            // fall through to date-only
        }
        try {
            LocalDate d = LocalDate.parse(value);
            return d.atStartOfDay(TimeUtil.IST).toInstant();
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Invalid date/time: " + value);
        }
    }

    private Map<String, String> unitMap() {
        Map<String, String> m = new HashMap<>();
        for (Pollutant p : pollutants.findAll()) {
            m.put(p.getCode(), p.getUnit());
        }
        return m;
    }

    private String rulesetOf(List<MeasurementRow> rows) {
        return rows.isEmpty() ? null : rows.get(0).qcRuleset();
    }
}
