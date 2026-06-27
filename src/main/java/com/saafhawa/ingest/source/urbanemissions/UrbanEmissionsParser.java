package com.saafhawa.ingest.source.urbanemissions;

import com.opencsv.CSVReader;
import com.saafhawa.ingest.spi.CityBulletinRow;
import com.saafhawa.ingest.spi.ParseResult;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Parses urbanemissions.info bulletin CSVs into {@link CityBulletinRow}s (§5.2, verification-log V6).
 *
 * <p>The archive ships the same data in several files with slightly different column layouts — the
 * clean form {@code date,City,No. Stations,Air Quality,Index Value,Prominent Pollutant} and an
 * "openrefined" form that prepends a row-index column and appends a {@code year} column. Because the
 * adapter may concatenate several files into one payload, this parser is header-driven: it maps
 * columns by name and re-syncs that map whenever it encounters a header row, so heterogeneous
 * schemas in a single stream parse correctly.
 */
@Component
public class UrbanEmissionsParser {

    static final String SOURCE_ID = "urbanemissions";

    public ParseResult parse(byte[] bytes) throws Exception {
        ParseResult result = new ParseResult();
        try (CSVReader reader = new CSVReader(
                new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            ColumnMap cols = null;
            String[] row;
            while ((row = reader.readNext()) != null) {
                if (isHeader(row)) {
                    cols = ColumnMap.from(row);
                    continue;
                }
                if (cols == null) {
                    continue; // data before any recognizable header — skip quietly
                }
                result.countFetched();
                CityBulletinRow parsed = parseRow(row, cols);
                if (parsed == null) {
                    result.reject(String.join(",", row));
                } else {
                    result.add(parsed);
                }
            }
        }
        return result;
    }

    /** A header row carries a "date" column and an "Index Value" column (case-insensitive). */
    private boolean isHeader(String[] row) {
        boolean date = false;
        boolean index = false;
        for (String cell : row) {
            String c = cell.trim();
            if (c.equalsIgnoreCase("date")) {
                date = true;
            } else if (c.equalsIgnoreCase("Index Value")) {
                index = true;
            }
        }
        return date && index;
    }

    private CityBulletinRow parseRow(String[] row, ColumnMap cols) {
        if (row.length <= cols.max) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(row[cols.date].trim());
            String city = row[cols.city].trim();
            if (city.isEmpty()) {
                return null;
            }
            Integer aqi = parseIntOrNull(row[cols.index].trim());
            String prominent = row[cols.pollutant].trim();
            boolean blank = prominent.isEmpty() || prominent.equalsIgnoreCase("NA");
            return new CityBulletinRow(city, null, date, aqi, blank ? null : prominent);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer parseIntOrNull(String s) {
        if (s.isEmpty() || s.equalsIgnoreCase("NA")) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            try {
                return (int) Math.round(Double.parseDouble(s));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    /** Column positions resolved by name from a header row. */
    private record ColumnMap(int date, int city, int index, int pollutant, int max) {

        static ColumnMap from(String[] header) {
            int date = -1;
            int city = -1;
            int index = -1;
            int pollutant = -1;
            for (int i = 0; i < header.length; i++) {
                switch (header[i].trim().toLowerCase()) {
                    case "date" -> date = i;
                    case "city" -> city = i;
                    case "index value" -> index = i;
                    case "prominent pollutant" -> pollutant = i;
                    default -> { /* ignore index/year/No. Stations/Air Quality columns */ }
                }
            }
            int max = Math.max(Math.max(date, city), Math.max(index, pollutant));
            return new ColumnMap(date, city, index, pollutant, max);
        }
    }
}
