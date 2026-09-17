package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class WardCleanerTest {

    @Test
    void cleanTextTrimsAndCollapsesInternalDoubleSpaces() {
        assertEquals("South Wing", WardCleaner.cleanText(" South  Wing "));
        assertNull(WardCleaner.cleanText("   "));
        assertNull(WardCleaner.cleanText(null));
    }

    @Test
    void normalizeIdTrimsAndUppercases() {
        assertEquals("W-05", WardCleaner.normalizeId("w-05"));
        assertEquals("W-03", WardCleaner.normalizeId(" W-03 "));
        assertNull(WardCleaner.normalizeId(" "));
    }

    @Test
    void normalizeNameTitleCasesAndPreservesAcronyms() {
        assertEquals("East Wing", WardCleaner.normalizeName("east wing"));
        assertEquals("North Wing", WardCleaner.normalizeName("North wing"));
        assertEquals("Paediatrics", WardCleaner.normalizeName("PAEDIATRICS"));
        assertEquals("ICU", WardCleaner.normalizeName("icu"));
        assertEquals("South Wing", WardCleaner.normalizeName("South  Wing"));
    }

    @Test
    void normalizeDepartmentFixesRegionalSpellingVariants() {
        assertEquals("Paediatrics", WardCleaner.normalizeDepartment("Pediatrics"));
        assertEquals("Cardiology", WardCleaner.normalizeDepartment("cardiology"));
    }

    @Test
    void isPlaceholderRecognizesEveryDocumentedVariant() {
        for (String placeholder : new String[]{"", " ", "N/A", "n/a", "TBD", "unknown", "-", "--", "NaN", "null", "none", "?"}) {
            assertTrue(WardCleaner.isPlaceholder(placeholder), "expected placeholder: '" + placeholder + "'");
        }
        assertFalse(WardCleaner.isPlaceholder("Cardiology"));
    }

    @Test
    void normalizeBooleanUnifiesAllFlagSpellings() {
        for (String truthy : new String[]{"Y", "yes", "1", "true", "ACTIVE", "on"}) {
            assertEquals(Boolean.TRUE, WardCleaner.normalizeBoolean(truthy), "expected TRUE for: " + truthy);
        }
        for (String falsy : new String[]{"N", "no", "0", "FALSE", "inactive", "off"}) {
            assertEquals(Boolean.FALSE, WardCleaner.normalizeBoolean(falsy), "expected FALSE for: " + falsy);
        }
        assertNull(WardCleaner.normalizeBoolean("maybe"));
        assertNull(WardCleaner.normalizeBoolean("N/A"));
    }

    @Test
    void normalizeDateParsesTheSupportedFormats() {
        assertEquals(LocalDate.of(2024, 1, 15), WardCleaner.normalizeDate("2024-01-15"));
        assertEquals(LocalDate.of(2024, 1, 5), WardCleaner.normalizeDate("1/5/2024"));
        assertEquals(LocalDate.of(2024, 2, 3), WardCleaner.normalizeDate("02/03/2024"));
        assertEquals(LocalDate.of(2024, 1, 15), WardCleaner.normalizeDate("15-01-2024"));
    }

    @Test
    void normalizeDateRejectsPlaceholdersAndInvalidDates() {
        assertNull(WardCleaner.normalizeDate("N/A"));
        assertNull(WardCleaner.normalizeDate("unknown"));
        assertNull(WardCleaner.normalizeDate("2023-13-45"));
        assertNull(WardCleaner.normalizeDate("32/01/2024"));
        assertNull(WardCleaner.normalizeDate(null));
    }

    @Test
    void normalizeBedsKeepsPlausibleCounts() {
        WardCleaner.BedsResult five = WardCleaner.normalizeBedsAvailable("5");
        assertEquals(5, five.value);
        assertNull(five.note);
        assertEquals(2, WardCleaner.normalizeBedsAvailable(" 2 ").value);
        assertEquals(3, WardCleaner.normalizeBedsAvailable("+3").value);
    }

    @Test
    void normalizeBedsNullsPlaceholdersWithNote() {
        for (String bad : new String[]{"N/A", "TBD", "", " "}) {
            WardCleaner.BedsResult result = WardCleaner.normalizeBedsAvailable(bad);
            assertNull(result.value, "expected null beds for: '" + bad + "'");
            assertTrue(result.note.contains("missing/placeholder"), result.note);
        }
    }

    @Test
    void normalizeBedsNullsNonNumericWithNote() {
        for (String raw : new String[]{"five", "full"}) {
            WardCleaner.BedsResult result = WardCleaner.normalizeBedsAvailable(raw);
            assertNull(result.value);
            assertTrue(result.note.contains("non-numeric"), result.note);
        }
    }

    @Test
    void normalizeBedsNullsNegativeAndUnrealisticCounts() {
        WardCleaner.BedsResult negative = WardCleaner.normalizeBedsAvailable("-1");
        assertNull(negative.value);
        assertTrue(negative.note.contains("negative"), negative.note);

        WardCleaner.BedsResult unrealistic = WardCleaner.normalizeBedsAvailable("2023");
        assertNull(unrealistic.value);
        assertTrue(unrealistic.note.contains("unrealistic"), unrealistic.note);
    }

    @Test
    void cleanRowProducesDocumentedCleanShape() {
        WardRecord record = WardCleaner.cleanRow(new String[]{"w-05", "east wing ", "PAEDIATRICS", "five"});
        assertNotNull(record);
        assertEquals("W-05", record.getWardId());
        assertEquals("East Wing", record.getWing());
        assertEquals("Paediatrics", record.getDepartment());
        assertNull(record.getBedsAvailable());
        assertNotNull(record.getNotes());
        assertTrue(record.getNotes().contains("non-numeric"), record.getNotes());
    }

    @Test
    void cleanRowFlagsMissingWing() {
        WardRecord record = WardCleaner.cleanRow(new String[]{"W-08", "", "Oncology", "4"});
        assertNotNull(record);
        assertNull(record.getWing());
        assertNotNull(record.getNotes());
        assertTrue(record.getNotes().contains("wing missing"), record.getNotes());
    }

    @Test
    void cleanRowRejectsRowWithoutUsableId() {
        assertNull(WardCleaner.cleanRow(new String[]{null, "East Wing", "Oncology", "4"}));
        assertNull(WardCleaner.cleanRow(new String[]{" ", "East Wing", "Oncology", "4"}));
    }
}