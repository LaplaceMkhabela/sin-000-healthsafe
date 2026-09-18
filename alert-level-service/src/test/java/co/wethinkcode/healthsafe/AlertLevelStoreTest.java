package co.wethinkcode.healthsafe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AlertLevelStoreTest {

    @Test
    void defaultsToZero() {
        assertEquals(0, new AlertLevelStore().current());
    }

    @Test
    void updatesWithinRange() {
        AlertLevelStore store = new AlertLevelStore();
        assertEquals(3, store.update(3));
        assertEquals(3, store.current());
        assertEquals(0, store.update(0));
        assertEquals(8, store.update(8));
        assertEquals(8, store.current());
    }

    @Test
    void rejectsOutOfRange() {
        AlertLevelStore store = new AlertLevelStore();
        assertThrows(IllegalArgumentException.class, () -> store.update(-1));
        assertThrows(IllegalArgumentException.class, () -> store.update(9));
    }
}