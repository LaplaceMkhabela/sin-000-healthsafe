package co.wethinkcode.healthsafe;

import com.opencsv.CSVReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the legacy {@code wards-outdated.csv} export, cleans each row with
 * {@link WardCleaner}, and collapses duplicates for the same real-world ward.
 */
public final class WardCsvLoader {

    private static final Logger log = LoggerFactory.getLogger(WardCsvLoader.class);

    /** CSV column order as shipped in wards-outdated.csv. */
    private static final int COL_WARD_ID = 0;
    private static final int COL_WING = 1;
    private static final int COL_DEPARTMENT = 2;
    private static final int COL_BEDS_AVAILABLE = 3;

    private WardCsvLoader() {
    }

    /** Loads and cleans the given classpath resource. */
    public static List<WardRecord> loadFromResource(String resource) {
        try (InputStream in = WardCsvLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("CSV resource not found on classpath: " + resource);
            }
            return load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read CSV resource: " + resource, e);
        }
    }

    /** Loads and cleans CSV content from a stream, one header row followed by data rows. */
    public static List<WardRecord> load(InputStream in) {
        Map<String, WardRecord> byId = new LinkedHashMap<>();
        int rawRows = 0;
        int skipped = 0;

        try (CSVReader reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.readNext(); // header
            String[] row;
            while ((row = reader.readNext()) != null) {
                rawRows++;
                if (isBlankRow(row)) {
                    continue;
                }
                WardRecord cleaned = WardCleaner.cleanRow(row);
                if (cleaned == null) {
                    skipped++;
                    log.warn("Skipping row {}: no usable ward id", rawRows + 1);
                    continue;
                }
                byId.merge(cleaned.getWardId(), cleaned, WardRecord::mergeWith);
            }
        } catch (com.opencsv.exceptions.CsvValidationException e) {
            throw new UncheckedIOException("CSV validation failed", new IOException(e));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse CSV stream", e);
        }

        int merged = rawRows - skipped - byId.size();
        log.info("Cleaned {} raw rows into {} wards ({} duplicates merged, {} rows skipped)",
            rawRows, byId.size(), merged, skipped);
        return new ArrayList<>(byId.values());
    }

    private static boolean isBlankRow(String[] row) {
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }
}