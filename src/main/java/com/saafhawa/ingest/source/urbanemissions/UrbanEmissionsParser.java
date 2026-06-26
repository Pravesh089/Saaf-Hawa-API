package com.saafhawa.ingest.source.urbanemissions;

import com.opencsv.CSVReader;
import com.saafhawa.ingest.spi.CityBulletinRow;
import com.saafhawa.ingest.spi.ParseResult;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Parses the urbanemissions.info {@code AllIndiaBulletins_<year>.csv} format: columns
 * {@code date,City,No. Stations,Air Quality,Index Value,Prominent Pollutant} (verification-log V6).
 */
@Component
public class UrbanEmissionsParser {

    static final String SOURCE_ID = "urbanemissions";

    public ParseResult parse(byte[] bytes) throws Exception {
        ParseResult result = new ParseResult();
        try (CSVReader reader = new CSVReader(
                new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            List<String[]> rows = reader.readAll();
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                result.countFetched();
                CityBulletinRow parsed = parseRow(row);
                if (parsed == null) {
                    result.reject(String.join(",", row));
                } else {
                    result.add(parsed);
                }
            }
        }
        return result;
    }

    private CityBulletinRow parseRow(String[] row) {
        if (row.length < 6) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(row[0].trim());
            String city = row[1].trim();
            if (city.isEmpty()) {
                return null;
            }
            Integer aqi = parseIntOrNull(row[4].trim());
            String prominent = row[5].trim();
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
}
