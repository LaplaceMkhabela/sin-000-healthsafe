package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class WardCatalogTest {

    private Ward ward(String id, String department) {
        Ward ward = new Ward();
        ward.setWardId(id);
        ward.setDepartment(department);
        return ward;
    }

    @Test
    void allReturnsEverythingInOrder() {
        List<Ward> wards = List.of(ward("W-01", "Cardiology"), ward("W-02", "Oncology"));
        WardCatalog catalog = new WardCatalog(wards);
        assertEquals(2, catalog.all().size());
        assertEquals("W-01", catalog.all().get(0).getWardId());
    }

    @Test
    void byIdFindsWardRegardlessOfCaseAndPadding() {
        WardCatalog catalog = new WardCatalog(List.of(ward("W-05", "Paediatrics")));
        assertTrue(catalog.byId("W-05").isPresent());
        assertTrue(catalog.byId("w-05").isPresent());
        assertTrue(catalog.byId(" w-05 ").isPresent());
        assertEquals("Paediatrics", catalog.byId("w-05").orElseThrow().getDepartment());
    }

    @Test
    void byIdReturnsEmptyForUnknownOrBlank() {
        WardCatalog catalog = new WardCatalog(List.of(ward("W-05", "Paediatrics")));
        assertTrue(catalog.byId("W-99").isEmpty());
        assertTrue(catalog.byId("").isEmpty());
        assertTrue(catalog.byId(null).isEmpty());
    }

    @Test
    void departmentsAreDistinctSortedAndSkipBlanks() {
        WardCatalog catalog = new WardCatalog(List.of(
            ward("W-01", "Cardiology"),
            ward("W-02", "Oncology"),
            ward("W-03", "Cardiology"),
            ward("W-04", null),
            ward("W-05", "ICU")
        ));
        assertEquals(List.of("Cardiology", "ICU", "Oncology"), catalog.departments());
    }

    @Test
    void byIdToleratesNullWardIdsInData() {
        Ward broken = ward(null, "Oncology");
        WardCatalog catalog = new WardCatalog(List.of(broken, ward("W-01", "ICU")));
        assertFalse(catalog.byId("W-01").isEmpty());
        Optional<Ward> missing = catalog.byId("");
        assertTrue(missing.isEmpty());
    }
}