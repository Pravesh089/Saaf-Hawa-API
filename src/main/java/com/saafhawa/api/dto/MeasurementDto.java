package com.saafhawa.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** A raw measurement in the API (interval=raw). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeasurementDto(
        String stationId,
        String pollutant,
        String unit,
        Period period,
        Double value,
        Double valueMin,
        Double valueMax,
        List<String> flags,
        String source,
        String qcRuleset,
        Integer reportedAqi) {

    public record Period(String start, String end, String interval) {
    }
}
