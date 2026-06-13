package com.saafhawa.api.dto;

import java.util.List;

/** Standard list envelope: {@code { meta, results }} (FDD §7). */
public record ApiResponse<T>(Meta meta, List<T> results) {

    public static <T> ApiResponse<T> of(Meta meta, List<T> results) {
        return new ApiResponse<>(meta, results);
    }
}
