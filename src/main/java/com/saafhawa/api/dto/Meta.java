package com.saafhawa.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Response metadata block (FDD §7). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Meta(
        List<String> source,
        String qcRuleset,
        Instant generatedAt,
        String attribution,
        String next,
        String note) {

    public static Meta of(List<String> sources, String qcRuleset, String next) {
        return new Meta(sources, qcRuleset, Instant.now(),
                "CPCB via data.gov.in; Saaf Hawa", next, null);
    }
}
