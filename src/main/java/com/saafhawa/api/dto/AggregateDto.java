package com.saafhawa.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** An aggregated measurement in the API (interval=day|month), per FDD §7. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AggregateDto(
        String stationId,
        String pollutant,
        String unit,
        MeasurementDto.Period period,
        Value value,
        Map<String, Long> flagsSummary) {

    /** Aggregate statistics incl. availability (§4.4). */
    public record Value(Double mean, Double min, Double max, long count, long expected, double availability) {
    }
}
