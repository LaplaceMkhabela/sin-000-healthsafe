package co.wethinkcode.healthsafe;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes raw strings from the legacy {@code wards-outdated.csv} into clean values.
 *
 * <p>Handles the known data-quality issues in one place: casing, padding, internal
 * double spaces, placeholder/missing values, non-numeric and unrealistic bed counts,
 * boolean/flag variants, and inconsistent date formats. The CSV as shipped exercises
 * the text/number handling; the boolean and date helpers are ready for extra columns
 * and are covered by unit tests.</p>
 */
public final class WardCleaner {

    private static final Set<String> PLACEHOLDERS = Set.of(
        "", "n/a", "na", "tbd", "unknown", "unk", "-", "--", "nan", "null", "none", "missing", "?", "??"
    );

    private static final Set<String> ACRONYMS = Set.of("ICU", "NICU", "PICU", "CCU", "MICU", "ER", "OR", "MRI");

    private static final Map<String, String> DEPARTMENT_ALIASES = Map.of(
        "pediatrics", "Paediatrics"
    );

    // anything above this is treated as a data-entry error (e.g. a year typed into a count)
    private static final int MAX_BEDS = 500;

    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("uuuu-MM-dd"),
        DateTimeFormatter.ofPattern("M/d/uuuu"),
        DateTimeFormatter.ofPattern("M-d-uuuu"),
        DateTimeFormatter.ofPattern("d/M/uuuu"),
        DateTimeFormatter.ofPattern("d-M-uuuu")
    };

    private WardCleaner() {
    }

    /**
     * Trims padding and collapses runs of internal whitespace to a single space.
     * Returns {@code null} for blank input.
     */
    public static String cleanText(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static String normalizeId(String raw) {
        String cleaned = cleanText(raw);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    /** Title-cases a human-readable name, e.g. {@code "east wing"} -> {@code "East Wing"}. */
    public static String normalizeName(String raw) {
        String cleaned = cleanText(raw);
        if (cleaned == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String part : cleaned.split(" ")) {
            String upper = part.toUpperCase(Locale.ROOT);
            if (ACRONYMS.contains(upper)) {
                out.append(upper);
            } else {
                out.append(Character.toUpperCase(part.charAt(0)))
                   .append(part.substring(1).toLowerCase(Locale.ROOT));
            }
            out.append(' ');
        }
        return out.toString().trim();
    }

    /** Normalizes a department name, including regional spelling variants. */
    public static String normalizeDepartment(String raw) {
        String cleaned = normalizeName(raw);
        if (cleaned == null) {
            return null;
        }
        return DEPARTMENT_ALIASES.getOrDefault(cleaned.toLowerCase(Locale.ROOT), cleaned);
    }

    /** Rejects placeholder/missing tokens (blank, {@code N/A}, {@code TBD}, {@code unknown}, {@code NaN}, ...). */
    public static boolean isPlaceholder(String raw) {
        String cleaned = cleanText(raw);
        return cleaned == null || PLACEHOLDERS.contains(cleaned.toLowerCase(Locale.ROOT));
    }

    /**
     * Normalizes the various boolean/flag spellings ({@code Y}/{@code yes}/{@code 1}/{@code true}/...)
     * to a single {@link Boolean}, or {@code null} when the value is unrecognized.
     */
    public static Boolean normalizeBoolean(String raw) {
        String cleaned = cleanText(raw);
        if (cleaned == null) {
            return null;
        }
        return switch (cleaned.toLowerCase(Locale.ROOT)) {
            case "y", "yes", "true", "1", "on", "active" -> true;
            case "n", "no", "false", "0", "off", "inactive" -> false;
            default -> null;
        };
    }

    /**
     * Parses a date written in any of the accepted formats (ISO, US slash, and dash formats
     * with one- or two-digit months/days), or {@code null} when the value is blank, a
     * placeholder, or not a valid date at all.
     */
    public static LocalDate normalizeDate(String raw) {
        String cleaned = cleanText(raw);
        if (cleaned == null || isPlaceholder(cleaned)) {
            return null;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(cleaned, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    /** Result of cleaning a bed count: the value if trustworthy, plus a note for follow-up. */
    public static final class BedsResult {
        public final Integer value;
        public final String note;

        BedsResult(Integer value, String note) {
            this.value = value;
            this.note = note;
        }
    }

    /**
     * Cleans a {@code beds_available} value. Placeholders, spelled-out numbers,
     * negative counts, and implausibly large values all yield {@code null} plus a
     * follow-up note so the row is never dropped but never trusts bad data.
     */
    public static BedsResult normalizeBedsAvailable(String raw) {
        String cleaned = cleanText(raw);
        if (cleaned == null || isPlaceholder(cleaned)) {
            String shown = cleaned == null ? "blank" : "'" + cleaned + "'";
            return new BedsResult(null, "bedsAvailable was missing/placeholder (" + shown + ") - flagged for follow-up");
        }
        Integer value;
        try {
            value = Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return new BedsResult(null, "bedsAvailable was non-numeric ('" + cleaned + "') - flagged for follow-up");
        }
        if (value < 0) {
            return new BedsResult(null, "bedsAvailable was negative (" + value + ") - flagged for follow-up");
        }
        if (value > MAX_BEDS) {
            return new BedsResult(null, "bedsAvailable was unrealistic (" + value + ") - flagged for follow-up");
        }
        return new BedsResult(value, null);
    }

    private static String cell(String[] row, int index) {
        return index < row.length ? row[index] : null;
    }

    /** Cleans one CSV row into a record, or {@code null} when the row has no usable ward id. */
    public WardRecord cleanRow(String[] row) {
        String wardId = normalizeId(cell(row, 0));
        if (wardId == null) {
            return null;
        }
        WardRecord record = new WardRecord();
        record.setWardId(wardId);

        String wing = normalizeName(cell(row, 1));
        record.setWing(wing);
        if (wing == null) {
            record.addFlag("wing missing/blank - flagged for follow-up");
        }

        record.setDepartment(normalizeDepartment(cell(row, 2)));

        BedsResult beds = normalizeBedsAvailable(cell(row, 3));
        record.setBedsAvailable(beds.value);
        if (beds.note != null) {
            record.addFlag(beds.note);
        }
        return record;
    }
}