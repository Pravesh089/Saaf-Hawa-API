package com.saafhawa.catalog;

import java.util.Map;
import java.util.Locale;

/** Maps Indian state/UT names to 2-letter codes for the station ID scheme (architecture.md §5). */
public final class StateCodes {

    private static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("andhra pradesh", "AP"), Map.entry("arunachal pradesh", "AR"),
            Map.entry("assam", "AS"), Map.entry("bihar", "BR"), Map.entry("chhattisgarh", "CG"),
            Map.entry("goa", "GA"), Map.entry("gujarat", "GJ"), Map.entry("haryana", "HR"),
            Map.entry("himachal pradesh", "HP"), Map.entry("jharkhand", "JH"),
            Map.entry("karnataka", "KA"), Map.entry("kerala", "KL"),
            Map.entry("madhya pradesh", "MP"), Map.entry("maharashtra", "MH"),
            Map.entry("manipur", "MN"), Map.entry("meghalaya", "ML"), Map.entry("mizoram", "MZ"),
            Map.entry("nagaland", "NL"), Map.entry("odisha", "OR"), Map.entry("punjab", "PB"),
            Map.entry("rajasthan", "RJ"), Map.entry("sikkim", "SK"), Map.entry("tamil nadu", "TN"),
            Map.entry("telangana", "TG"), Map.entry("tripura", "TR"),
            Map.entry("uttar pradesh", "UP"), Map.entry("uttarakhand", "UK"),
            Map.entry("west bengal", "WB"), Map.entry("delhi", "DL"),
            Map.entry("national capital territory of delhi", "DL"),
            Map.entry("jammu and kashmir", "JK"), Map.entry("ladakh", "LA"),
            Map.entry("puducherry", "PY"), Map.entry("chandigarh", "CH"),
            Map.entry("andaman and nicobar islands", "AN"),
            Map.entry("dadra and nagar haveli and daman and diu", "DN"),
            Map.entry("lakshadweep", "LD"));

    private StateCodes() {
    }

    /** @return 2-letter code, or "XX" if unknown/blank. */
    public static String codeFor(String stateName) {
        if (stateName == null || stateName.isBlank()) {
            return "XX";
        }
        return CODES.getOrDefault(stateName.trim().toLowerCase(Locale.ROOT), "XX");
    }
}
