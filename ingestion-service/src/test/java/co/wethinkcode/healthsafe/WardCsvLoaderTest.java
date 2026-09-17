package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class WardCsvLoaderTest {

    @Test
    void loadsEveryWardFromTheShippedCsv() {
        List<WardRecord> wards = WardCsvLoader.loadFromResource("wards-outdated.csv");
        assertEquals(17, wards.size());
    }

    @Test
    void preservesSourceOrder() {
        List<WardRecord> wards = WardCsvLoader.loadFromResource("wards-outdated.csv");
        assertEquals("W-01", wards.get(0).getWardId());
        assertEquals("W-17", wards.get(wards.size() - 1).getWardId());
    }

    @Test
    void everyWardHasANormalizedId() {
        for (WardRecord ward : WardCsvLoader.loadFromResource("wards-outdated.csv")) {
            assertTrue(ward.getWardId().matches("W-\\d{2}"), "unexpected id: " + ward.getWardId());
            assertEquals(ward.getWardId(), ward.getWardId().toUpperCase());
        }
    }

    @Test
    void mergesTheDuplicatedWardInsteadOfSplittingIt() {
        List<WardRecord> wards = WardCsvLoader.loadFromResource("wards-outdated.csv");
        long w05Copies = wards.stream().filter(w -> w.getWardId().equals("W-05")).count();
        assertEquals(1, w05Copies);

        WardRecord w05 = wardById(wards, "W-05");
        assertEquals(Integer.valueOf(5), w05.getBedsAvailable());
        assertNotNull(w05.getNotes());
        assertTrue(w05.getNotes().contains("merged duplicate"), w05.getNotes());
    }

    @Test
    void cleansWingsAndDepartments() {
        List<WardRecord> wards = WardCsvLoader.loadFromResource("wards-outdated.csv");
        for (WardRecord ward : wards) {
            assertTrue(ward.getWing() == null || !ward.getWing().contains("  "), "double space in wing: " + ward.getWing());
            assertTrue(!ward.getDepartment().matches(".*[a-z]{2,}[A-Z].*") || ward.getDepartment().equals("ICU"),
                "unexpected casing in department: " + ward.getDepartment());
        }
        assertEquals("South Wing", wardById(wards, "W-10").getWing());
        assertEquals("Paediatrics", wardById(wards, "W-05").getDepartment());
    }

    @Test
    void flagsMissingWingForWardEight() {
        WardRecord w08 = wardById(WardCsvLoader.loadFromResource("wards-outdated.csv"), "W-08");
        assertNull(w08.getWing());
        assertNotNull(w08.getNotes());
        assertTrue(w08.getNotes().contains("wing missing"), w08.getNotes());
    }

    @Test
    void flagsEveryRequireFollowUpBedCountInsteadOfTrustingIt() {
        List<WardRecord> wards = WardCsvLoader.loadFromResource("wards-outdated.csv");
        assertNull(wardById(wards, "W-02").getBedsAvailable());  // N/A
        assertNull(wardById(wards, "W-04").getBedsAvailable());  // -1
        assertNull(wardById(wards, "W-12").getBedsAvailable());  // full
        assertNull(wardById(wards, "W-13").getBedsAvailable());  // 2023
        assertNull(wardById(wards, "W-15").getBedsAvailable());  // unknown
        for (String id : new String[]{"W-02", "W-04", "W-12", "W-13", "W-15"}) {
            assertNotNull(wardById(wards, id).getNotes(), id + " should carry a follow-up note");
        }
        assertEquals(Integer.valueOf(2), wardById(wards, "W-06").getBedsAvailable());
        assertEquals(Integer.valueOf(6), wardById(wards, "W-17").getBedsAvailable());
    }

    private WardRecord wardById(List<WardRecord> wards, String id) {
        return wards.stream()
            .filter(w -> w.getWardId().equals(id))
            .findFirst()
            .orElse(null);
    }
}