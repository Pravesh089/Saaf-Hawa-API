package com.saafhawa.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.saafhawa.catalog.Station;

/** Public station representation (FR-4.1/4.2). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StationDto(
        String id,
        String name,
        String city,
        String state,
        String stateCode,
        String agency,
        String stationType,
        Double latitude,
        Double longitude,
        String status,
        boolean needsReview,
        Double distanceMetres) {

    public static StationDto from(Station s) {
        return from(s, null);
    }

    public static StationDto from(Station s, Double distanceMetres) {
        return new StationDto(s.getId(), s.getName(), s.getCity(), s.getState(), s.getStateCode(),
                s.getAgency(), s.getStationType(), s.latitude(), s.longitude(), s.getStatus(),
                s.isNeedsReview(), distanceMetres);
    }
}
